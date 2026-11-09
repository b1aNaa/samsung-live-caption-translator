package com.livesub.translate.translate

import android.content.Context
import com.livesub.translate.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Baidu Translation API (fanyi-api.baidu.com).
 * Free tier for individual devs; needs appid + key from
 * https://fanyi-api.baidu.com (开发者后台 → 通用文本翻译).
 */
class BaiduTranslator(
    private val ctx: Context,
    private val appId: String,
    private val key: String
) : Translator {

    /** Paid per char — wait a bit longer so we only send settled text. */
    override val debounceMs = 500L

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private fun md5(s: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override suspend fun translate(text: String): String =
        withContext(Dispatchers.IO) {
            require(appId.isNotBlank() && key.isNotBlank()) {
                ctx.getString(R.string.err_baidu_missing)
            }
            val salt = System.currentTimeMillis().toString()
            val sign = md5(appId + text + salt + key)
            val body = FormBody.Builder()
                .add("q", text)
                .add("from", "auto")
                .add("to", "zh")
                .add("appid", appId)
                .add("salt", salt)
                .add("sign", sign)
                .build()
            val req = Request.Builder()
                .url("https://fanyi-api.baidu.com/api/trans/vip/translate")
                .post(body)
                .build()
            http.newCall(req).execute().use { resp ->
                val json = JSONObject(resp.body?.string() ?: "")
                if (json.has("error_code")) {
                    throw Exception(ctx.getString(R.string.err_baidu_prefix) + json.getString("error_code") + ": " + json.optString("error_msg"))
                }
                val arr = json.getJSONArray("trans_result")
                buildString {
                    for (i in 0 until arr.length()) {
                        if (i > 0) append('\n')
                        append(arr.getJSONObject(i).getString("dst"))
                    }
                }
            }
        }
}
