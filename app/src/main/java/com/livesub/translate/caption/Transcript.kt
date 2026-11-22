package com.livesub.translate.caption

/**
 * Turns the text lines pulled out of the Live Caption window into one
 * transcript string, one line per utterance.
 *
 * Live Caption keeps its whole scrollback in a single `SubtitleView` and
 * starts a new line when the speaker pauses (verified on a Galaxy S24 Ultra,
 * Android 17). Visual wrapping never adds line breaks to a view's text, so a
 * line break is always an utterance boundary — [Segmenter] treats it as one,
 * which matters when speech recognition leaves out the final punctuation.
 */
object Transcript {

    /** Only the tail matters; the scrollback can grow for minutes. */
    const val MAX_CHARS = 4000

    private val WHITESPACE = Regex("[\\t\\x0B\\f\\r \\u00A0\\u3000]+")

    fun join(lines: List<String>): String {
        val out = lines.asSequence()
            .map { it.replace(WHITESPACE, " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        if (out.length <= MAX_CHARS) return out
        var from = out.length - MAX_CHARS
        if (Character.isLowSurrogate(out[from])) from++
        return out.substring(from)
    }
}
