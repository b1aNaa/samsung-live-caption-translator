package com.livesub.translate.caption

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmenterTest {

    // --- Caption lines: always a boundary, and remembered for display ---

    @Test
    fun sentencesKeepTheirCaptionLineAndOnlyLastIsOpen() {
        val segs = Segmenter.split("入ってる。ちびたぬき。\nたぬきのた")
        assertEquals(listOf("入ってる。", "ちびたぬき。", "たぬきのた"), segs.map { it.text })
        assertEquals(listOf(0, 0, 1), segs.map { it.line })
        assertEquals(listOf(true, true, false), segs.map { it.closed })
        assertEquals(listOf("入ってる。ちびたぬき。", "たぬきのた"), Segmenter.lines("入ってる。ちびたぬき。\nたぬきのた"))
    }

    @Test
    fun lineWithoutPunctuationStillEndsAtTheBreak() {
        val segs = Segmenter.split("熟考されてるに違う\n一体どんな一撃を")
        assertEquals(listOf("熟考されてるに違う", "一体どんな一撃を"), segs.map { it.text })
        assertEquals(listOf(0, 1), segs.map { it.line })
    }

    @Test
    fun blankLinesAndWhitespaceAreIgnored() {
        val segs = Segmenter.split("  a \n\n   \n b ")
        assertEquals(listOf("a", "b"), segs.map { it.text })
        assertEquals(listOf(0, 1), segs.map { it.line })
        assertEquals(listOf("a", "b"), Segmenter.lines("  a \n\n   \n b "))
        assertEquals(emptyList<Segment>(), Segmenter.split("   "))
    }

    @Test
    fun longLinesAreChunkedButStayOnTheirLine() {
        val line = "First sentence is here. " + "word ".repeat(60).trim()
        val segs = Segmenter.split(line, maxChunkChars = 80)
        assertEquals("First sentence is here.", segs.first().text)
        assertTrue(segs.size >= 3)
        segs.forEach { assertTrue(it.text.length <= 80) }
        assertTrue(segs.all { it.line == 0 })
    }

    @Test
    fun chunksStayStableAsTheLineGrows() {
        val words = (1..120).map { "word$it" }
        var previous: List<String> = emptyList()
        for (n in 1..words.size) {
            val segs = Segmenter.split(words.take(n).joinToString(" "), maxChunkChars = 60)
            val closed = segs.filter { it.closed }.map { it.text }
            // Every chunk closed earlier must still be there, unchanged.
            assertEquals(previous, closed.take(previous.size))
            previous = closed
        }
        assertTrue(previous.size > 5)
    }

    // --- Sentence and chunk rules ---

    @Test
    fun sentencesSplitOnTerminalPunctuation() {
        assertEquals(
            listOf("Hello there.", "How are you?", "I'm fine"),
            Segmenter.sentences("Hello there. How are you? I'm fine"),
        )
    }

    @Test
    fun sentencesIgnoreDecimalsUrlsAndAbbreviations() {
        assertEquals(
            listOf("Mr. Smith paid 3.5 dollars at example.com today.", "Then left"),
            Segmenter.sentences("Mr. Smith paid 3.5 dollars at example.com today. Then left"),
        )
        assertEquals(listOf("J. K. Rowling wrote it.", "Yes"), Segmenter.sentences("J. K. Rowling wrote it. Yes"))
    }

    @Test
    fun sentencesKeepClosingQuotesAndPunctuationRuns() {
        assertEquals(listOf("He said \"Really?!\"", "Okay"), Segmenter.sentences("He said \"Really?!\" Okay"))
        assertEquals(listOf("Wait...", "what"), Segmenter.sentences("Wait... what"))
    }

    @Test
    fun sentencesSplitFullWidthPunctuationWithoutSpaces() {
        assertEquals(
            listOf("今日は晴れです。", "明日は雨？", "たぶん"),
            Segmenter.sentences("今日は晴れです。明日は雨？たぶん"),
        )
    }

    @Test
    fun chunksAtClauseOrWordBoundaries() {
        val long = "so we went down to the river and we waited there for a very long time, " +
            "and nobody came at all which was strange because they had promised to meet us at noon"
        val chunks = Segmenter.chunk(long, 80)
        assertTrue(chunks.size >= 2)
        chunks.forEach { assertTrue("too long: $it", it.length <= 80) }
        assertTrue(chunks[0].endsWith(","))
        assertEquals(long, chunks.joinToString(" "))
    }

    @Test
    fun hardCutsTextWithoutSpaces() {
        assertEquals(listOf(100, 100, 100), Segmenter.chunk("我".repeat(300), 100).map { it.length })
    }
}
