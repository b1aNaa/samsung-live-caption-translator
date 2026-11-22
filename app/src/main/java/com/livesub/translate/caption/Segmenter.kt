package com.livesub.translate.caption

/**
 * A translatable unit of the transcript: one sentence (or clause chunk) of
 * one Live Caption line.
 *
 * @property closed true when the text can no longer grow — something has
 *   been said after it. Only the very last segment is open; it is the one
 *   speech recognition is still rewriting.
 * @property line index of the caption line it belongs to. The subtitle shows
 *   one line per caption line, so segments are regrouped by this for display.
 */
data class Segment(val text: String, val closed: Boolean, val line: Int = 0)

/**
 * Splits a transcript into translation units.
 *
 * Live Caption starts a new line when the speaker pauses; that is always a
 * boundary, and the subtitle reproduces those lines one for one. Within a
 * line — which can run long when someone talks without pausing — text is
 * further split into sentences and clause chunks, so that only the sentence
 * still being spoken is re-translated as the line grows.
 *
 * Chunk boundaries depend only on the text before them, so they stay put as
 * new words are appended — that keeps already-translated chunks cacheable.
 */
object Segmenter {

    const val DEFAULT_MAX_CHARS = 140

    private val ABBREVIATIONS = setOf(
        "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "vs", "e.g", "i.e",
    )

    fun split(transcript: String, maxChunkChars: Int = DEFAULT_MAX_CHARS): List<Segment> {
        val pieces = ArrayList<Pair<String, Int>>()
        var lineIndex = 0
        for (raw in transcript.split('\n')) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            for (sentence in sentences(line)) {
                for (c in chunk(sentence, maxChunkChars)) pieces += c to lineIndex
            }
            lineIndex++
        }
        return pieces.mapIndexed { i, (s, line) -> Segment(s, closed = i < pieces.lastIndex, line = line) }
    }

    /** The caption lines themselves, indexed like [Segment.line]. */
    fun lines(transcript: String): List<String> =
        transcript.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    internal fun sentences(t: String): List<String> {
        val res = ArrayList<String>()
        var start = 0
        var i = 0
        while (i < t.length) {
            val c = t[i]
            if (c == '\n') {
                // A new caption line means the speaker paused: always a boundary.
                val s = t.substring(start, i).trim()
                if (s.isNotEmpty()) res += s
                start = i + 1
                i++
                continue
            }
            if (!isTerminal(c)) {
                i++
                continue
            }
            // Absorb "?!", "...", closing quotes/brackets: 'Really?!"' stays together.
            var end = i + 1
            while (end < t.length && (isTerminal(t[end]) || isCloser(t[end]))) end++
            val boundary = when {
                isFullWidthTerminal(c) -> true
                end >= t.length -> true
                !t[end].isWhitespace() -> false            // 3.5, U.S.A, example.com
                c == '.' && isAbbreviation(t, start, i) -> false
                else -> true
            }
            if (boundary) {
                val s = t.substring(start, end).trim()
                if (s.isNotEmpty()) res += s
                start = end
            }
            i = end
        }
        val tail = t.substring(start).trim()
        if (tail.isNotEmpty()) res += tail
        return res
    }

    internal fun chunk(s: String, maxChars: Int): List<String> {
        if (s.length <= maxChars) return listOf(s)
        val out = ArrayList<String>()
        var rest = s
        while (rest.length > maxChars) {
            val cut = findCut(rest, maxChars)
            val head = rest.substring(0, cut).trim()
            if (head.isNotEmpty()) out += head
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) out += rest
        return out
    }

    /** Index to cut [s] at, looking only at its first [max] + 1 chars. */
    private fun findCut(s: String, max: Int): Int {
        val min = max / 2
        for (i in max - 1 downTo min) {
            val c = s[i]
            if (c in CLAUSE_FULL_WIDTH) return i + 1
            if (c in CLAUSE && s[i + 1].isWhitespace()) return i + 1
        }
        for (i in max downTo min) {
            if (s[i].isWhitespace()) return i
        }
        // No spaces (Chinese / Japanese / Thai): hard cut, never inside a surrogate pair.
        return if (Character.isHighSurrogate(s[max - 1])) max - 1 else max
    }

    private const val CLAUSE = ",;:"
    private const val CLAUSE_FULL_WIDTH = "，、；："

    private fun isTerminal(c: Char) = c == '.' || c == '?' || c == '!' || c == '…' ||
        c == '؟' || c == '।' || isFullWidthTerminal(c)

    private fun isFullWidthTerminal(c: Char) = c == '。' || c == '？' || c == '！'

    private fun isCloser(c: Char) = c in "\"'”’)]）」』"

    private fun isAbbreviation(t: String, sentenceStart: Int, dot: Int): Boolean {
        var j = dot
        while (j > sentenceStart && !t[j - 1].isWhitespace()) j--
        val word = t.substring(j, dot).trimStart('"', '\'', '(', '“', '‘').lowercase()
        if (word.isEmpty()) return false
        if (word in ABBREVIATIONS) return true
        if (word.length == 1 && t[dot - 1].isUpperCase()) return true // initials: "J. K. Rowling"
        return '.' in word                                            // "U.S." , "a.m."
    }
}
