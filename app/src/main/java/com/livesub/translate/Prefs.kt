package com.livesub.translate

import android.content.Context

object Prefs {
    private const val NAME = "settings"
    private const val K_ENGINE = "engine"           // baidu | qwen | deepseek
    private const val K_BAIDU_ID = "baidu_appid"
    private const val K_BAIDU_KEY = "baidu_key"
    private const val K_QWEN_KEY = "qwen_key"
    private const val K_DS_KEY = "deepseek_key"
    private const val K_FONT_SP = "font_sp"
    private const val K_BG_ALPHA = "bg_alpha"
    private const val K_SHOW_SRC = "show_src"
    private const val K_LAST_LINES = "last_lines"
    private const val K_OV_X = "ov_x"
    private const val K_OV_Y = "ov_y"
    private const val K_LANG = "lang"               // ""=system | "zh" | "en"
    private const val K_DARK = "dark"

    private fun sp(ctx: Context) =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var Context.engine: String
        get() = sp(this).getString(K_ENGINE, "baidu") ?: "baidu"
        set(v) = sp(this).edit().putString(K_ENGINE, v).apply()

    var Context.baiduAppId: String
        get() = sp(this).getString(K_BAIDU_ID, "") ?: ""
        set(v) = sp(this).edit().putString(K_BAIDU_ID, v).apply()

    var Context.baiduKey: String
        get() = sp(this).getString(K_BAIDU_KEY, "") ?: ""
        set(v) = sp(this).edit().putString(K_BAIDU_KEY, v).apply()

    var Context.qwenKey: String
        get() = sp(this).getString(K_QWEN_KEY, "") ?: ""
        set(v) = sp(this).edit().putString(K_QWEN_KEY, v).apply()

    var Context.deepseekKey: String
        get() = sp(this).getString(K_DS_KEY, "") ?: ""
        set(v) = sp(this).edit().putString(K_DS_KEY, v).apply()

    var Context.fontSp: Float
        get() = sp(this).getFloat(K_FONT_SP, 17f)
        set(v) = sp(this).edit().putFloat(K_FONT_SP, v).apply()

    /** 0..255, default 160 */
    var Context.bgAlpha: Int
        get() = sp(this).getInt(K_BG_ALPHA, 160)
        set(v) = sp(this).edit().putInt(K_BG_ALPHA, v).apply()

    var Context.showSrc: Boolean
        get() = sp(this).getBoolean(K_SHOW_SRC, true)
        set(v) = sp(this).edit().putBoolean(K_SHOW_SRC, v).apply()

    /** How many trailing caption lines to feed the translator, 1..5. */
    var Context.lastLines: Int
        get() = sp(this).getInt(K_LAST_LINES, 2)
        set(v) = sp(this).edit().putInt(K_LAST_LINES, v.coerceIn(1, 5)).apply()

    /** Saved overlay position; -1 = not dragged yet. */
    var Context.ovX: Int
        get() = sp(this).getInt(K_OV_X, -1)
        set(v) = sp(this).edit().putInt(K_OV_X, v).apply()

    var Context.ovY: Int
        get() = sp(this).getInt(K_OV_Y, -1)
        set(v) = sp(this).edit().putInt(K_OV_Y, v).apply()

    var Context.lang: String
        get() = sp(this).getString(K_LANG, "") ?: ""
        set(v) = sp(this).edit().putString(K_LANG, v).apply()

    var Context.dark: Boolean
        get() = sp(this).getBoolean(K_DARK, false)
        set(v) = sp(this).edit().putBoolean(K_DARK, v).apply()
}
