package com.livesub.translate.translate

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Alibaba Qwen-MT — a dedicated translation model — through DashScope's
 * OpenAI-compatible endpoint. Qwen-MT takes no system prompt; behaviour is
 * steered with `translation_options` instead.
 */
class QwenMtTranslator(
    private val apiKey: String,
    private val model: String,
    private val targetName: String,
    international: Boolean,
) : Translator {

    override val debounceMs = 400L

    private val url = (if (international) INTL_BASE else CN_BASE) + "/chat/completions"

    override suspend fun translate(request: TranslationRequest): String {
        if (apiKey.isBlank()) throw TranslateException(TranslateException.Kind.MissingKey)
        val body = JSONObject()
            .put("model", model.ifBlank { DEFAULT_MODEL })
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", request.text)))
            .put(
                "translation_options", JSONObject()
                    .put("source_lang", "auto")
                    .put("target_lang", targetName)
                    .put("domains", DOMAIN_HINT)
            )
            .toString()
            .toRequestBody(JSON)
        val result = Http.execute(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .post(body)
                .build()
        )
        if (!result.isSuccessful) throw Http.errorFor(result)
        return OpenAiCompatTranslator.contentOf(result)
    }

    companion object {
        const val DEFAULT_MODEL = "qwen-mt-lite"
        private const val CN_BASE = "https://dashscope.aliyuncs.com/compatible-mode/v1"
        private const val INTL_BASE = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1"
        private const val DOMAIN_HINT =
            "The text is live speech-recognition captions from videos. It may be an incomplete " +
                "sentence; translate it as-is in a natural, colloquial spoken style."
        private val JSON = "application/json".toMediaType()
    }
}
