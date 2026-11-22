package com.livesub.translate.translate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class HttpResult(val code: Int, val body: String) {
    val isSuccessful get() = code in 200..299

    /** Body as JSON, or null when the server sent something else (HTML error page…). */
    fun jsonOrNull(): JSONObject? = try { JSONObject(body) } catch (_: Exception) { null }
}

/** One shared client for all engines: connection pool and TLS sessions are reused. */
internal object Http {

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Executes [request]. Cancelling the calling coroutine cancels the HTTP
     * call, so stale requests don't keep running after captions move on.
     */
    suspend fun execute(request: Request): HttpResult {
        val response = try {
            client.newCall(request).await()
        } catch (e: InterruptedIOException) {
            throw TranslateException(TranslateException.Kind.Timeout, cause = e)
        } catch (e: IOException) {
            throw TranslateException(TranslateException.Kind.Network, e.message, e)
        }
        return withContext(Dispatchers.IO) {
            response.use {
                try {
                    HttpResult(it.code, it.body?.string().orEmpty())
                } catch (e: IOException) {
                    throw TranslateException(TranslateException.Kind.Network, e.message, e)
                }
            }
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                cont.resume(response) { _, value, _ -> value.close() }
            }
        })
    }

    /** Maps an HTTP status from an OpenAI-style API to an error kind. */
    fun errorFor(result: HttpResult): TranslateException {
        val message = result.jsonOrNull()?.let { json ->
            json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
        } ?: "HTTP ${result.code}"
        val kind = when (result.code) {
            401, 403 -> TranslateException.Kind.Auth
            402 -> TranslateException.Kind.Quota
            429 -> TranslateException.Kind.RateLimit
            else -> TranslateException.Kind.Server
        }
        return TranslateException(kind, message)
    }
}
