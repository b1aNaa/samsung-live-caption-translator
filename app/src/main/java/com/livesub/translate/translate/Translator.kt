package com.livesub.translate.translate

import android.content.Context
import com.livesub.translate.Prefs.baiduAppId
import com.livesub.translate.Prefs.baiduKey
import com.livesub.translate.Prefs.deepseekKey
import com.livesub.translate.Prefs.engine
import com.livesub.translate.Prefs.qwenKey

interface Translator {
    /** Milliseconds to wait for text to settle before translating. */
    val debounceMs: Long
    suspend fun translate(text: String): String
    fun close() {}

    companion object {
        fun create(ctx: Context): Translator = when (ctx.engine) {
            "baidu" -> BaiduTranslator(ctx, ctx.baiduAppId, ctx.baiduKey)
            "deepseek" -> DeepSeekTranslator(ctx, ctx.deepseekKey)
            else -> QwenMtTranslator(ctx, ctx.qwenKey)
        }
    }
}
