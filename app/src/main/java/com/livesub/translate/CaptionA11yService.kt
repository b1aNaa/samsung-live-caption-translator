package com.livesub.translate

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import android.service.quicksettings.TileService
import android.view.accessibility.AccessibilityEvent
import com.livesub.translate.caption.CaptionReader
import com.livesub.translate.overlay.OverlayController
import com.livesub.translate.pipeline.PipelineConfig
import com.livesub.translate.pipeline.TranslationPipeline
import com.livesub.translate.settings.Locales
import com.livesub.translate.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads the system Live Caption text through the accessibility tree — no
 * screen capture, no OCR — and feeds it to the translation pipeline.
 *
 * The class name is part of the user-enabled accessibility component; keep
 * it, or every user has to re-enable the service after updating.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CaptionA11yService : AccessibilityService() {

    companion object {
        @Volatile private var instance: CaptionA11yService? = null

        /** Must be called on the main thread. */
        fun start(): StartResult = instance?.startTranslating() ?: StartResult.NO_SERVICE

        /** Must be called on the main thread. */
        fun stop() {
            instance?.apply {
                // Don't let auto-start undo a manual stop until Live Caption comes back later.
                autoStartSuppressed = true
                stopTranslating()
            }
        }

        private const val TAG = "LiveSub"
        private const val POLL_MS = 1000L
        private const val SCAN_COALESCE_MS = 80L
        private const val STATUS_TIMEOUT_MS = 10_000L
        /** Silence this long from Live Caption counts as a new captioning session. */
        private const val NEW_SESSION_GAP_MS = 60_000L
    }

    private val store by lazy { SettingsStore.get(this) }
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val scanDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val pipelineDispatcher = Dispatchers.Default.limitedParallelism(1)

    private var overlay: OverlayController? = null
    private var session: Job? = null
    @Volatile private var pipeline: TranslationPipeline? = null
    private val scanQueued = AtomicBoolean(false)
    private var lastLoggedTranscript = ""
    private var lastCaptionEventAt = 0L
    private var autoStartSuppressed = false

    override fun onServiceConnected() {
        instance = this
        overlay = OverlayController(this, store)
        ServiceStatus.set { it.copy(connected = true) }
        refreshTile()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Events are already filtered to Live Caption by packageNames in the service config.
        if (event?.packageName?.toString() != CaptionReader.LIVE_CAPTION_PKG) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            // Track this even while translating; otherwise a manual stop looks like
            // the start of a new captioning session and auto-start undoes it at once.
            val now = SystemClock.elapsedRealtime()
            if (now - lastCaptionEventAt > NEW_SESSION_GAP_MS) autoStartSuppressed = false
            lastCaptionEventAt = now
        }
        if (pipeline != null) {
            requestScan()
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        val s = store.current
        if (session == null && s.autoStart && s.hasCredentials && !autoStartSuppressed) {
            startTranslating(showStatus = false)
        }
    }

    override fun onInterrupt() {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.onConfigurationChanged()
    }

    /** Coalesces bursts of content-change events into one tree walk. */
    private fun requestScan() {
        if (!scanQueued.compareAndSet(false, true)) return
        mainScope.launch(scanDispatcher) {
            delay(SCAN_COALESCE_MS)
            scanQueued.set(false)
            val p = pipeline ?: return@launch
            val windows = try { windows } catch (_: Exception) { return@launch }
            val transcript = CaptionReader.read(windows)
            if (BuildConfig.DEBUG && transcript != lastLoggedTranscript) {
                lastLoggedTranscript = transcript
                Log.d(TAG, "tree:\n" + CaptionReader.dump(windows))
                Log.d(TAG, "transcript: " + transcript.takeLast(300))
            }
            p.submit(transcript)
        }
    }

    private fun startTranslating(showStatus: Boolean = true): StartResult {
        if (session != null) return StartResult.STARTED
        if (!store.current.hasCredentials) return StartResult.NO_CREDENTIALS

        val ov = overlay ?: return StartResult.NO_SERVICE
        autoStartSuppressed = false
        ov.attach()
        if (showStatus) ov.setStatus(strings().getString(R.string.overlay_waiting))
        ServiceStatus.set { it.copy(running = true) }
        refreshTile()

        session = mainScope.launch {
            launch { store.flow.collect { ov.applySettings(it) } }
            launch {
                delay(STATUS_TIMEOUT_MS)
                ov.setStatus(null)
            }
            launch {
                while (isActive) {
                    requestScan()
                    delay(POLL_MS)
                }
            }
            // A new pipeline whenever the engine, its credentials or the layout change.
            store.flow
                .distinctUntilChangedBy { it.translatorConfig() + it.displayCount }
                .collectLatest { s ->
                    coroutineScope {
                        val translator = s.createTranslator()
                        val p = TranslationPipeline(
                            scope = CoroutineScope(coroutineContext + pipelineDispatcher),
                            translator = translator,
                            config = PipelineConfig(
                                displayCount = s.displayCount,
                                debounceMs = translator.debounceMs,
                                joiner = s.target.sentenceJoiner,
                            ),
                            formatError = { strings().describeError(it) },
                            onCommitted = TranslationHistory::add,
                            log = { if (BuildConfig.DEBUG) Log.d(TAG, it) },
                        )
                        pipeline = p
                        requestScan()
                        p.display.collect { ov.render(it) }
                    }
                }
        }
        return StartResult.STARTED
    }

    private fun stopTranslating() {
        session?.cancel()
        session = null
        pipeline = null
        overlay?.detach()
        ServiceStatus.set { it.copy(running = false) }
        refreshTile()
    }

    /** Resources in the in-app language, which may differ from the system's. */
    private fun strings(): Context = Locales.wrap(this, store.current.language)

    private fun refreshTile() {
        try {
            TileService.requestListeningState(this, ComponentName(this, TranslateTileService::class.java))
        } catch (_: Exception) {}
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        if (instance !== this) return
        stopTranslating()
        overlay = null
        instance = null
        ServiceStatus.set { it.copy(connected = false) }
        refreshTile()
        mainScope.cancel()
    }
}
