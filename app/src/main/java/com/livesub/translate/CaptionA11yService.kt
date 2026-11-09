package com.livesub.translate

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.livesub.translate.Prefs.baiduAppId
import com.livesub.translate.Prefs.baiduKey
import com.livesub.translate.Prefs.deepseekKey
import com.livesub.translate.Prefs.engine
import com.livesub.translate.Prefs.lastLines
import com.livesub.translate.Prefs.qwenKey
import com.livesub.translate.translate.Translator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Accessibility service that reads the text shown by the system Live
 * Caption overlay (package com.google.android.as) directly — no screen
 * capture, no OCR.
 */
class CaptionA11yService : AccessibilityService() {

    companion object {
        const val TARGET_PKG = "com.google.android.as"

        @Volatile var instance: CaptionA11yService? = null
            private set

        @Volatile var translating = false
            private set

        fun isActive() = translating && instance != null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var overlay: OverlayController? = null
    private var translator: Translator? = null
    private var translatorKey = ""
    private var pollJob: Job? = null
    private var translateJob: Job? = null
    private var lastText = ""
    private var pendingText: String? = null
    private var firstPendingAt = 0L
    private var lastChangeAt = 0L

    /** Max wait while captions keep scrolling — forces a translation so
     *  continuous speech still gets partial results instead of starving. */
    private val maxWaitMs = 1500L

    override fun onServiceConnected() {
        instance = this
        overlay = OverlayController(this)
        pollJob = scope.launch { pollLoop() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Caption text changes arrive as events from com.google.android.as —
        // trigger a scan immediately instead of waiting for the poll.
        if (!translating) return
        if (event?.packageName?.toString() != TARGET_PKG) return
        scope.launch { scan() }
    }
    override fun onInterrupt() {}

    private val scanning = AtomicBoolean(false)

    /** Fallback poll — events drive most scans, this catches the rare misses. */
    private suspend fun pollLoop() {
        while (scope.isActive) {
            try { if (translating) scan() } catch (_: Exception) {}
            delay(1000)
        }
    }

    /** Engine + credentials fingerprint — rebuilds the translator on change. */
    private fun currentKey() =
        "$engine|${if (engine == "baidu") "$baiduAppId:$baiduKey" else if (engine == "deepseek") deepseekKey else qwenKey}"

    private fun ensureTranslator(): Translator? {
        val k = currentKey()
        if (translator == null || k != translatorKey) {
            translator?.close()
            translator = Translator.create(this)
            translatorKey = k
        }
        return translator
    }

    private fun scan() {
        if (!scanning.compareAndSet(false, true)) return
        try {
            scanInternal()
        } finally {
            scanning.set(false)
        }
    }

    private fun scanInternal() {
        var captionText = ""
        val wins = try { windows } catch (_: Exception) { null } ?: emptyList()
        for (w in wins) {
            val root = w.root ?: continue
            try {
                if (root.packageName?.toString() != TARGET_PKG) continue
                val lines = ArrayList<String>()
                collectTextLines(root, lines)
                if (lines.isNotEmpty()) {
                    // The caption window carries the whole scrollback — only the
                    // last N lines are what Live Caption is currently showing.
                    // Keep line breaks so source and translation stay aligned.
                    captionText = lines.takeLast(lastLines).joinToString("\n")
                }
            } finally {
                root.recycle()
            }
        }

        val now = System.currentTimeMillis()
        if (captionText.isNotBlank() && captionText != lastText) {
            lastText = captionText
            if (pendingText == null) firstPendingAt = now
            pendingText = captionText
            lastChangeAt = now
        }

        val tr = ensureTranslator() ?: return
        val pending = pendingText ?: return
        if (translateJob?.isActive == true) return  // one request in flight
        val settled = now - lastChangeAt >= tr.debounceMs
        val starving = now - firstPendingAt >= maxWaitMs
        if (!settled && !starving) return

        pendingText = null
        translateJob = scope.launch {
            try {
                val out = tr.translate(pending)
                if (translating) overlay?.show(pending, out)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                if (translating) overlay?.showError(
                    e.message ?: getString(R.string.err_translate_failed)
                )
            }
        }
    }

    private fun collectTextLines(node: AccessibilityNodeInfo?, lines: MutableList<String>) {
        if (node == null) return
        val t = node.text
        if (!t.isNullOrBlank()) {
            // A single node may hold multiple \n-separated lines
            t.split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { lines.add(it) }
        }
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            collectTextLines(c, lines)
            c.recycle()
        }
    }

    fun startTranslating() {
        translating = true
        lastText = ""
        ensureTranslator()
        // Immediate feedback so the user knows it's on, before the
        // first caption arrives
        overlay?.show("", getString(R.string.overlay_started))
        notifyState(true)
    }

    fun stopTranslating() {
        translating = false
        translateJob?.cancel()
        overlay?.destroy()
        notifyState(false)
    }

    private fun notifyState(running: Boolean) {
        sendBroadcast(
            Intent(MainActivity.ACTION_STATE)
                .putExtra(MainActivity.EXTRA_RUNNING, running)
                .setPackage(packageName)
        )
        try {
            android.service.quicksettings.TileService.requestListeningState(
                this, android.content.ComponentName(this, TranslateTileService::class.java)
            )
        } catch (_: Exception) {}
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopTranslating()
        instance = null
        translator?.close()
        scope.cancel()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopTranslating()
        instance = null
        translator?.close()
        scope.cancel()
        super.onDestroy()
    }
}
