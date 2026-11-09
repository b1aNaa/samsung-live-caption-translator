package com.livesub.translate.translate

import android.content.Context
import com.livesub.translate.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Alibaba DashScope Qwen-MT (dedicated machine-translation model) via the
 * OpenAI-compatible endpoint. Get an API key at https://bailian.console.aliyun.com
 * (阿里云百炼 → API-KEY). Model: qwen-mt-lite — fastest tier, tuned for
 * real-time subtitle-style text.
 */
class QwenMtTranslator(
    private val ctx: Context,
    private val apiKey: String
) : Translator {

    override val debounceMs = 400L

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    override suspend fun translate(text: String): String =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { ctx.getString(R.string.err_qwen_missing) }
            val body = JSONObject().apply {
                put("model", "qwen-mt-lite")
                put("messages", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("content", text)
                }))
                put("translation_options", JSONObject().apply {
                    put("source_lang", "auto")
                    put("target_lang", "Chinese")
                })
            }.toString().toRequestBody("application/json".toMediaType())

            val req = Request.Builder()
                .url("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .post(body)
                .build()

            http.newCall(req).execute().use { resp ->
                val json = JSONObject(resp.body?.string() ?: "")
                if (!resp.isSuccessful) {
                    val msg = json.optJSONObject("error")?.optString("message")
                        ?: "HTTP ${resp.code}"
                    throw Exception(ctx.getString(R.string.err_qwen_prefix) + msg)
                }
                json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
            }
        }
}
