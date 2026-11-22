package com.livesub.translate.translate

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.FormBody
import okhttp3.Request
import java.security.MessageDigest

/**
 * Baidu general text translation (fanyi-api.baidu.com).
 *
 * The free standard tier allows 1 query/second, so requests are serialized
 * and a rate-limit error is retried once after a short pause.
 */
class BaiduTranslator(
    private val appId: String,
    private val secret: String,
    private val targetCode: String,
) : Translator {

    override val debounceMs = 450L

    private val lock = Mutex()

    override suspend fun translate(request: TranslationRequest): String {
        if (appId.isBlank() || secret.isBlank()) throw TranslateException(TranslateException.Kind.MissingKey)
        return lock.withLock {
            try {
                call(request.text)
            } catch (e: TranslateException) {
                if (e.kind != TranslateException.Kind.RateLimit) throw e
                delay(1100)
                call(request.text)
            }
        }
    }

    private suspend fun call(text: String): String {
        val salt = System.currentTimeMillis().toString()
        val body = FormBody.Builder()
            .add("q", text)
            .add("from", "auto")
            .add("to", targetCode)
            .add("appid", appId)
            .add("salt", salt)
            .add("sign", md5(appId + text + salt + secret))
            .build()
        val result = Http.execute(
            Request.Builder().url(URL).post(body).build()
        )
        val json = result.jsonOrNull()
            ?: throw TranslateException(TranslateException.Kind.Server, "HTTP ${result.code}")
        val code = json.optString("error_code")
        if (code.isNotEmpty() && code != "52000") {
            throw TranslateException(kindFor(code), "$code ${json.optString("error_msg")}".trim())
        }
        val arr = json.optJSONArray("trans_result")
            ?: throw TranslateException(TranslateException.Kind.BadResponse)
        return (0 until arr.length()).joinToString("\n") { arr.getJSONObject(it).optString("dst") }
    }

    private fun kindFor(code: String) = when (code) {
        "52001" -> TranslateException.Kind.Timeout
        "52003", "54001", "58000", "90107" -> TranslateException.Kind.Auth
        "54003", "54005" -> TranslateException.Kind.RateLimit
        "54004", "58002" -> TranslateException.Kind.Quota
        else -> TranslateException.Kind.Server
    }

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val URL = "https://fanyi-api.baidu.com/api/trans/vip/translate"
    }
}
