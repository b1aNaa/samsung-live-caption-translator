package com.livesub.translate.pipeline

import com.livesub.translate.translate.TranslateException
import com.livesub.translate.translate.TranslationRequest
import com.livesub.translate.translate.Translator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TranslationPipelineTest {

    private class FakeTranslator(private val latencyMs: Long = 100) : Translator {
        val calls = mutableListOf<TranslationRequest>()
        var failNext: Throwable? = null

        override suspend fun translate(request: TranslationRequest): String {
            calls += request
            delay(latencyMs)
            failNext?.let {
                failNext = null
                throw it
            }
            return "T(${request.text})"
        }
    }

    private val config = PipelineConfig(
        displayCount = 2,
        debounceMs = 400,
        maxWaitMs = 1500,
        minForcedChars = 12,
        contextSize = 2,
        idleClearMs = 5000,
        errorBackoffMs = 2000,
    )

    private fun TestScope.newPipeline(tr: Translator, cfg: PipelineConfig = config) =
        TranslationPipeline(backgroundScope, tr, cfg, { it.message ?: "error" }, now = { testScheduler.currentTime })

    private fun TestScope.step(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    private fun TranslationPipeline.translations() = display.value.lines.map { it.translation }

    @Test
    fun liveSegmentWaitsForDebounce() = runTest {
        val tr = FakeTranslator()
        val p = newPipeline(tr)
        p.submit("Hello there friend")
        step(399)
        assertEquals(0, tr.calls.size)
        step(1)
        assertEquals(listOf("Hello there friend"), tr.calls.map { it.text })
        assertEquals(listOf<String?>(null), p.translations())
        step(100)
        assertEquals(listOf("T(Hello there friend)"), p.translations())
    }

    @Test
    fun continuousSpeechIsForcedAtMaxWait() = runTest {
        val tr = FakeTranslator()
        val p = newPipeline(tr)
        val words = "this sentence just keeps going and going without any pause at all".split(" ")
        // A new word every 200 ms — the text never settles.
        for (i in 1..words.size) {
            p.submit(words.take(i).joinToString(" "))
            step(200)
        }
        val first = tr.calls.first().text
        assertTrue("forced request expected, got ${tr.calls}", tr.calls.isNotEmpty())
        // Sent around t=1500 even though the text never stopped changing.
        assertEquals(words.take(8).joinToString(" "), first)
    }

    @Test
    fun closedSentenceIsNotRetranslatedWhenLiveVersionMatched() = runTest {
        val tr = FakeTranslator()
        val p = newPipeline(tr)
        p.submit("First sentence here.")
        step(600)
        p.submit("First sentence here.\nAnd then")
        step(600)
        assertEquals(listOf("First sentence here.", "And then"), tr.calls.map { it.text })
        assertEquals(listOf("T(First sentence here.)", "T(And then)"), p.translations())
    }

    @Test
    fun closedSentenceIsTranslatedImmediatelyWithContext() = runTest {
        val tr = FakeTranslator()
        val p = newPipeline(tr, config.copy(displayCount = 3))
        p.submit("One fish.\nTwo fish.\nRed")
        runCurrent()
        // Both closed segments go out at once; only the live one is debounced.
        assertEquals(listOf("One fish.", "Two fish."), tr.calls.map { it.text })
        assertEquals(listOf("One fish."), tr.calls[1].context)
    }

    @Test
    fun previousTranslationStaysVisibleWhileNewOneIsPending() = runTest {
        val tr = FakeTranslator(latencyMs = 300)
        val p = newPipeline(tr)
        p.submit("We are going to the")
        step(800)
        assertEquals(listOf("T(We are going to the)"), p.translations())
        p.submit("We are going to the beach tomorrow")
        runCurrent()
        // New text, no new translation yet: keep showing the old one instead of blanking.
        assertEquals(listOf("T(We are going to the)"), p.translations())
        step(800)
        assertEquals(listOf("T(We are going to the beach tomorrow)"), p.translations())
    }

    @Test
    fun recognitionMergingASentenceBackKeepsItsTranslationVisible() = runTest {
        // Seen on a Galaxy S24 Ultra: Live Caption ends a sentence, then removes the
        // "。" and merges it with the next words.
        val tr = FakeTranslator(latencyMs = 300)
        val p = newPipeline(tr)
        p.submit("それが邪魔になるから。あれ")
        step(1000)
        assertEquals(listOf("T(それが邪魔になるから。)T(あれ)"), p.translations())
        p.submit("それが邪魔になるからあれえっと")
        runCurrent()
        assertEquals(listOf("T(それが邪魔になるから。)"), p.translations())
    }

    @Test
    fun sentencesOfOneCaptionLineFormOneSubtitleLine() = runTest {
        val tr = FakeTranslator()
        val p = newPipeline(tr, config.copy(joiner = " "))
        p.submit("I see. You came back.\nWhere were you")
        step(1000)
        assertEquals(listOf("I see. You came back.", "Where were you"), p.display.value.lines.map { it.source })
        assertEquals(listOf("T(I see.) T(You came back.)", "T(Where were you)"), p.translations())
        // Only the sentence still being spoken is re-sent as the line grows.
        p.submit("I see. You came back.\nWhere were you. I looked everywhere")
        step(1000)
        assertEquals(1, tr.calls.count { it.text == "I see." })
        assertEquals("T(Where were you.) T(I looked everywhere)", p.translations().last())
    }

    @Test
    fun engineLineBreaksNeverAddSubtitleLines() = runTest {
        val wrapping = object : Translator {
            override suspend fun translate(request: TranslationRequest) = "第一部分\n第二部分"
        }
        val p = newPipeline(wrapping)
        p.submit("入ってる\nたぬきのた")
        step(1000)
        assertEquals(listOf("第一部分 第二部分", "第一部分 第二部分"), p.translations())
    }

    @Test
    fun finishedLinesAreCommittedOnceWithTheirFullTranslation() = runTest {
        val committed = mutableListOf<Pair<String, String>>()
        val p = TranslationPipeline(
            backgroundScope, FakeTranslator(), config, { "error" },
            now = { testScheduler.currentTime },
            onCommitted = { s, t -> committed += s to t },
        )
        p.submit("A one. A two.\nB")
        step(1000)
        p.submit("A one. A two.\nB three")
        step(1000)
        assertEquals(listOf("A one. A two." to "T(A one.)T(A two.)"), committed)
    }

    @Test
    fun provisionalNeverBorrowsAnotherOnScreenSentence() = runTest {
        // "そうですね。" is cached; the live "そうだよ" shares its first characters but is a
        // different sentence on the same line — it must not show "T(そうですね。)" twice.
        val tr = FakeTranslator(latencyMs = 300)
        val p = newPipeline(tr)
        p.submit("そうですね。そうだよ")
        runCurrent()
        step(300)
        assertEquals(listOf("T(そうですね。)"), p.translations())
    }

    @Test
    fun repeatedFailuresBackOffExponentially() = runTest {
        val failing = object : Translator {
            var calls = 0
            override suspend fun translate(request: TranslationRequest): String {
                calls++
                throw TranslateException(TranslateException.Kind.Auth)
            }
        }
        val p = newPipeline(failing)
        p.submit("Is anybody out there")
        step(400)                  // t=400: first attempt, backoff 2 s
        assertEquals(1, failing.calls)
        step(2000)                 // t=2400: second attempt, backoff 4 s
        assertEquals(2, failing.calls)
        step(3900)
        assertEquals("still backing off", 2, failing.calls)
        step(100)                  // t=6400: third attempt
        assertEquals(3, failing.calls)
        assertTrue(p.display.value.error != null)
    }

    @Test
    fun lastLineIsCommittedWhenCaptionsGoAway() = runTest {
        val committed = mutableListOf<String>()
        val p = TranslationPipeline(
            backgroundScope, FakeTranslator(), config, { "error" },
            now = { testScheduler.currentTime },
            onCommitted = { s, _ -> committed += s },
        )
        p.submit("The very last words")
        step(1000)
        assertEquals(emptyList<String>(), committed)
        p.submit("")
        step(config.idleClearMs + 10)
        assertEquals(listOf("The very last words"), committed)
    }

    @Test
    fun errorsAreShownThenBackedOffAndCleared() = runTest {
        val tr = FakeTranslator()
        tr.failNext = TranslateException(TranslateException.Kind.Network, "offline")
        val p = newPipeline(tr)
        p.submit("Is anybody out there")
        step(500)
        assertEquals("offline", p.display.value.error)
        assertEquals(1, tr.calls.size)
        step(1000)
        assertEquals("no retry during backoff", 1, tr.calls.size)
        step(1200)
        assertEquals(2, tr.calls.size)
        assertNull(p.display.value.error)
        assertEquals(listOf("T(Is anybody out there)"), p.translations())
    }

    @Test
    fun displayClearsAfterCaptionsDisappear() = runTest {
        val p = newPipeline(FakeTranslator())
        p.submit("Short line here")
        step(600)
        p.submit("")
        step(4000)
        assertEquals(1, p.display.value.lines.size)
        step(1100)
        assertTrue(p.display.value.isEmpty)
    }

    @Test
    fun onlyTrailingSegmentsAreShown() = runTest {
        val tr = FakeTranslator()
        val p = newPipeline(tr)
        p.submit("A one.\nB two.\nC three.\nD four")
        step(1000)
        assertEquals(listOf("C three.", "D four"), p.display.value.lines.map { it.source })
        // Sentences that scrolled out of view are never sent.
        assertTrue(tr.calls.none { it.text.startsWith("A") || it.text.startsWith("B") })
    }
}
