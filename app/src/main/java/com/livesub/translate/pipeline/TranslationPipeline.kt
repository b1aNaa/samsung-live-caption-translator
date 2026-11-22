package com.livesub.translate.pipeline

import com.livesub.translate.caption.Segment
import com.livesub.translate.caption.Segmenter
import com.livesub.translate.translate.TranslationRequest
import com.livesub.translate.translate.Translator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** One subtitle line, mirroring one Live Caption line. */
data class DisplayLine(
    val source: String,
    /** null while nothing on this line has been translated yet. */
    val translation: String?,
    /** The line speech recognition is still extending or rewriting. */
    val live: Boolean,
)

data class DisplayState(
    val lines: List<DisplayLine> = emptyList(),
    val error: String? = null,
) {
    val isEmpty get() = lines.isEmpty() && error == null
}

data class PipelineConfig(
    /** How many trailing caption lines to keep on screen. */
    val displayCount: Int = 2,
    /** Glue between translated sentences of one line: "" for Chinese/Japanese, " " otherwise. */
    val joiner: String = "",
    /** Live segment must be unchanged this long before it's sent. */
    val debounceMs: Long = 450,
    /** During continuous speech, send the live segment at least this often. */
    val maxWaitMs: Long = 1500,
    /** …but only once it's long enough to be worth translating. */
    val minForcedChars: Int = 12,
    /** Preceding segments handed to context-aware engines. */
    val contextSize: Int = 3,
    /** Clear the display after captions have been gone this long. */
    val idleClearMs: Long = 6000,
    /** Pause new requests this long after a failure; doubles while failures continue. */
    val errorBackoffMs: Long = 2000,
    val maxErrorBackoffMs: Long = 30_000,
    val maxChunkChars: Int = Segmenter.DEFAULT_MAX_CHARS,
)

/**
 * Turns a stream of caption transcripts into translated subtitle lines.
 *
 * - The subtitle has one line per Live Caption line, breaking where it breaks.
 * - Within a line, translation works per sentence: finished sentences are
 *   translated exactly once and cached, and only the one still being spoken
 *   is re-sent as it grows. Their translations are joined back into the line.
 * - The live segment is debounced: sent when it stops changing, or every
 *   [PipelineConfig.maxWaitMs] while speech keeps going.
 * - Until a new translation arrives, the previous one stays on screen, so
 *   the overlay never blanks or flickers between versions.
 *
 * All state is confined to [scope], which **must be single-threaded**
 * (e.g. `Dispatchers.Default.limitedParallelism(1)`). [submit] may be called
 * from any thread.
 */
class TranslationPipeline(
    private val scope: CoroutineScope,
    private val translator: Translator,
    private val config: PipelineConfig,
    private val formatError: (Throwable) -> String,
    private val now: () -> Long = System::currentTimeMillis,
    /** Called once per finished caption line, with its full translation. */
    private val onCommitted: (source: String, translation: String) -> Unit = { _, _ -> },
    private val log: (String) -> Unit = {},
) {
    private val _display = MutableStateFlow(DisplayState())
    val display: StateFlow<DisplayState> = _display

    private val cache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 300
    }
    private val inFlight = HashSet<String>()
    private val committed = object : LinkedHashMap<String, Unit>(64, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > 300
    }

    private var lastTranscript: String? = null
    private var segments: List<Segment> = emptyList()
    private var lineTexts: List<String> = emptyList()
    private var liveText: String? = null
    private var liveChangedAt = 0L
    private var pendingSince: Long? = null
    private var error: String? = null
    private var backoffUntil = 0L
    private var consecutiveErrors = 0

    private var liveJob: Job? = null
    private var timerJob: Job? = null
    private var idleJob: Job? = null

    fun submit(transcript: String) {
        scope.launch { onTranscript(transcript) }
    }

    private fun onTranscript(t: String) {
        if (t == lastTranscript) return
        lastTranscript = t
        if (t.isBlank()) {
            scheduleIdleClear()
            return
        }
        idleJob?.cancel()
        segments = Segmenter.split(t, config.maxChunkChars)
        lineTexts = Segmenter.lines(t)
        val live = segments.lastOrNull()?.takeIf { !it.closed }?.text
        if (live != liveText) {
            liveText = live
            if (live != null) {
                liveChangedAt = now()
                if (pendingSince == null) pendingSince = liveChangedAt
            }
        }
        pump()
        publish()
    }

    /** Starts whatever requests are due, and arms a timer for the next one. */
    private fun pump() {
        timerJob?.cancel()
        timerJob = null
        val t = now()
        if (t < backoffUntil) {
            arm(backoffUntil - t)
            return
        }
        for (seg in window()) {
            if (seg.closed && seg.text !in cache && seg.text !in inFlight) launchTranslate(seg.text, live = false)
        }
        val live = liveText ?: return
        if (live in cache || live in inFlight || liveJob?.isActive == true) return
        val settleAt = liveChangedAt + config.debounceMs
        val forceAt = pendingSince
            ?.takeIf { live.length >= config.minForcedChars }
            ?.plus(config.maxWaitMs)
            ?: Long.MAX_VALUE
        val due = minOf(settleAt, forceAt)
        if (t >= due) {
            pendingSince = null
            liveJob = launchTranslate(live, live = true)
        } else {
            arm(due - t)
        }
    }

    private fun arm(delayMs: Long) {
        timerJob = scope.launch {
            delay(delayMs)
            timerJob = null
            pump()
        }
    }

    private fun launchTranslate(text: String, live: Boolean): Job {
        inFlight += text
        val context = contextFor(text)
        return scope.launch {
            try {
                log("→ ${if (live) "live" else "closed"}: $text")
                val out = translator.translate(TranslationRequest(text, context))
                log("← $out")
                // One caption line in, one subtitle line out — even if the engine wraps.
                cache[text] = out.replace(LINE_BREAKS, " ").trim()
                error = null
                consecutiveErrors = 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                log("✗ $e")
                error = formatError(e)
                // A bad key or empty balance won't fix itself in 2 s: back off exponentially.
                val backoff = (config.errorBackoffMs shl consecutiveErrors.coerceAtMost(10))
                    .coerceAtMost(config.maxErrorBackoffMs)
                consecutiveErrors++
                backoffUntil = now() + backoff
            } finally {
                inFlight -= text
            }
            publish()
            pump()
        }
    }

    /** Segments of the last [PipelineConfig.displayCount] caption lines. */
    private fun window(): List<Segment> {
        val last = segments.lastOrNull()?.line ?: return emptyList()
        val first = last - config.displayCount + 1
        return segments.filter { it.line >= first }
    }

    private fun contextFor(text: String): List<String> {
        if (config.contextSize <= 0) return emptyList()
        val idx = segments.indexOfLast { it.text == text }
        if (idx <= 0) return emptyList()
        return segments.subList(maxOf(0, idx - config.contextSize), idx).map { it.text }
    }

    private fun scheduleIdleClear() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(config.idleClearMs)
            commitFinishedLines(includeLast = true)
            segments = emptyList()
            lineTexts = emptyList()
            liveText = null
            pendingSince = null
            error = null
            publish()
        }
    }

    private fun publish() {
        commitFinishedLines(includeLast = false)
        val win = window()
        // Provisional matches must be older versions of a segment, never another
        // segment that is on screen right now — that would show its text twice.
        val onScreen = win.mapTo(HashSet()) { it.text }
        val lines = win.groupBy { it.line }.map { (line, segs) ->
            val parts = segs.map { cache[it.text] ?: provisional(it.text, onScreen) }
            DisplayLine(
                source = sourceOf(line, segs),
                translation = parts.filterNotNull().takeIf { it.isNotEmpty() }?.joinToString(config.joiner),
                live = segs.any { !it.closed },
            )
        }
        _display.value = DisplayState(lines, error)
    }

    /**
     * Reports caption lines that are finished — a later line exists, or captions
     * went away ([includeLast]) — and fully translated, each exactly once.
     */
    private fun commitFinishedLines(includeLast: Boolean) {
        val win = window()
        val lastLine = win.lastOrNull()?.line ?: return
        for ((line, segs) in win.groupBy { it.line }) {
            if (line == lastLine && !includeLast) continue
            val parts = segs.map { cache[it.text] }
            if (parts.any { it == null }) continue
            val source = sourceOf(line, segs)
            if (source in committed) continue
            committed[source] = Unit
            onCommitted(source, parts.joinToString(config.joiner))
        }
    }

    private fun sourceOf(line: Int, segs: List<Segment>) =
        lineTexts.getOrNull(line) ?: segs.joinToString(" ") { it.text }

    /**
     * Best stand-in while [text] has no translation of its own: the cached
     * translation of an earlier version of the same segment — the one sharing
     * the longest prefix with it. Speech recognition keeps rewriting the tail
     * (and sometimes merges a finished sentence back into the live one), so
     * the most recent request is not always the closest match.
     */
    private fun provisional(text: String, exclude: Set<String>): String? {
        var best: String? = null
        var bestShared = 0
        for ((src, dst) in cache.entries) {
            if (src in exclude) continue
            val shared = text.commonPrefixWith(src).length
            if (shared >= minOf(12, (src.length + 1) / 2) && shared >= bestShared) {
                best = dst
                bestShared = shared
            }
        }
        return best
    }

    private companion object {
        val LINE_BREAKS = Regex("\\s*[\\r\\n]+\\s*")
    }
}
