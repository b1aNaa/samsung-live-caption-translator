package com.livesub.translate.translate

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Any chat model behind an OpenAI-compatible `/chat/completions` endpoint —
 * DeepSeek, or a user-supplied provider. Unlike dedicated MT engines an LLM
 * can use the preceding captions as context, which it gets here.
 */
class OpenAiCompatTranslator(
    baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val targetName: String,
) : Translator {

    override val debounceMs = 550L

    private val url = baseUrl.trim().trimEnd('/').let {
        if (it.endsWith("/chat/completions")) it else "$it/chat/completions"
    }

    override suspend fun translate(request: TranslationRequest): String {
        if (apiKey.isBlank()) throw TranslateException(TranslateException.Kind.MissingKey)
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            throw TranslateException(TranslateException.Kind.Server, "Invalid base URL")
        }
        val user = if (request.context.isEmpty()) {
            request.text
        } else {
            buildString {
                append("Previous captions (context only, do not translate):\n")
                request.context.forEach { append(it).append('\n') }
                append("\nTranslate:\n").append(request.text)
            }
        }
        val body = JSONObject()
            .put("model", model)
            .put("stream", false)
            .put("temperature", 0.2)
            .put("max_tokens", 400)
            .put(
                "messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt(targetName)))
                    .put(JSONObject().put("role", "user").put("content", user))
            )
            .toString()
            .toRequestBody(JSON)
        val result = try {
            Http.execute(
                Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $apiKey")
                    .post(body)
                    .build()
            )
        } catch (e: IllegalArgumentException) {
            throw TranslateException(TranslateException.Kind.Server, "Invalid base URL", e)
        }
        if (!result.isSuccessful) throw Http.errorFor(result)
        return contentOf(result).trim('"', '“', '”', '「', '」').trim()
    }

    companion object {
        const val DEEPSEEK_BASE = "https://api.deepseek.com"
        const val DEEPSEEK_MODEL = "deepseek-chat"

        private val JSON = "application/json".toMediaType()

        private fun systemPrompt(target: String) =
            "You are a real-time subtitle translator. Translate the caption text the user sends into " +
                "$target. Output only the translation — no explanations, notes, quotes or numbering. " +
                "The text comes from speech recognition and may be an incomplete sentence: translate " +
                "exactly what is there without completing it. Keep a natural spoken tone and use the " +
                "conventional renderings of names."

        internal fun contentOf(result: HttpResult): String {
            val json = result.jsonOrNull() ?: throw TranslateException(TranslateException.Kind.BadResponse)
            return json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: throw TranslateException(TranslateException.Kind.BadResponse)
        }
    }
}
