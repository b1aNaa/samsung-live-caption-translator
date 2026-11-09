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
 * DeepSeek chat model (OpenAI-compatible, https://api.deepseek.com).
 * An LLM rather than a dedicated MT engine — best context handling for
 * slang/proper nouns, but slower and pricier per call. The system prompt
 * forces strict line-aligned output.
 */
class DeepSeekTranslator(
    private val ctx: Context,
    private val apiKey: String
) : Translator {

    companion object {
        // DeepSeek's chat model id — currently V3.2 non-thinking.
        // Change here if a newer model ships.
        private const val MODEL = "deepseek-chat"
        private const val SYSTEM_PROMPT =
            "你是实时字幕翻译器。将用户发送的外语字幕逐行翻译成简体中文。" +
            "要求：输出行数必须与输入完全一致，逐行对应；" +
            "只输出译文本身，不要解释、不要序号、不要引号；" +
            "口语表达要自然，人名地名按通行译法。"
    }

    override val debounceMs = 500L

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun translate(text: String): String =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { ctx.getString(R.string.err_ds_missing) }
            val body = JSONObject().apply {
                put("model", MODEL)
                put("stream", false)
                put("temperature", 0.3)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", SYSTEM_PROMPT)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", text)
                    })
                })
            }.toString().toRequestBody("application/json".toMediaType())

            val req = Request.Builder()
                .url("https://api.deepseek.com/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .post(body)
                .build()

            http.newCall(req).execute().use { resp ->
                val json = JSONObject(resp.body?.string() ?: "")
                if (!resp.isSuccessful) {
                    val msg = json.optJSONObject("error")?.optString("message")
                        ?: "HTTP ${resp.code}"
                    throw Exception(ctx.getString(R.string.err_ds_prefix) + msg)
                }
                json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
            }
        }
}
