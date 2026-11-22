package com.livesub.translate.caption

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptTest {

    @Test
    fun keepsOneLinePerUtteranceAndCollapsesSpaces() {
        assertEquals(
            "so I told him that\nwe should go",
            Transcript.join(listOf("so I told him that", "  we   should go ")),
        )
    }

    @Test
    fun skipsBlankLines() {
        assertEquals("a\nb", Transcript.join(listOf("a", "", "   ", "b")))
    }

    @Test
    fun keepsOnlyTheTail() {
        val out = Transcript.join(List(1000) { "line$it" })
        assertTrue(out.length <= Transcript.MAX_CHARS)
        assertTrue(out.endsWith("line999"))
    }

    @Test
    fun realLiveCaptionTextSplitsPerUtterance() {
        // Captured from Live Caption on a Galaxy S24 Ultra: the second line has no final punctuation.
        val node = "この方のことだ、きっと悪口を熟考されてるに違う\n一体どんな一撃をちび。\nたぬきのた"
        val segs = Segmenter.split(Transcript.join(node.split('\n')))
        assertEquals(
            listOf("この方のことだ、きっと悪口を熟考されてるに違う", "一体どんな一撃をちび。", "たぬきのた"),
            segs.map { it.text },
        )
        assertEquals(listOf(true, true, false), segs.map { it.closed })
    }
}
