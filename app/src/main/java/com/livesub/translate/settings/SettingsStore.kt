package com.livesub.translate.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for settings. Every component observes [flow], so
 * a change made in the UI reaches the running overlay and translator
 * immediately.
 *
 * Keys of v1.x are kept so existing users don't lose their API keys.
 */
class SettingsStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private val state = MutableStateFlow(read())
    val flow: StateFlow<AppSettings> = state.asStateFlow()
    val current: AppSettings get() = state.value

    fun update(transform: (AppSettings) -> AppSettings) {
        synchronized(this) {
            val old = state.value
            val new = transform(old)
            if (new == old) return
            write(new)
            state.value = new
        }
    }

    private fun read(): AppSettings = with(prefs) {
        val d = AppSettings()
        AppSettings(
            engine = Engine.of(getString(K_ENGINE, null)),
            target = TargetLang.of(getString(K_TARGET, null)),
            baiduAppId = getString(K_BAIDU_ID, null) ?: d.baiduAppId,
            baiduKey = getString(K_BAIDU_KEY, null) ?: d.baiduKey,
            qwenKey = getString(K_QWEN_KEY, null) ?: d.qwenKey,
            qwenModel = getString(K_QWEN_MODEL, null) ?: d.qwenModel,
            qwenIntl = getBoolean(K_QWEN_INTL, d.qwenIntl),
            deepseekKey = getString(K_DS_KEY, null) ?: d.deepseekKey,
            deepseekModel = getString(K_DS_MODEL, null) ?: d.deepseekModel,
            customBaseUrl = getString(K_CUSTOM_URL, null) ?: d.customBaseUrl,
            customKey = getString(K_CUSTOM_KEY, null) ?: d.customKey,
            customModel = getString(K_CUSTOM_MODEL, null) ?: d.customModel,
            // v1 stored "lines to read" (1..5); it maps naturally onto segments to show.
            // Its meaning changed (sentences, then visible lines), so older keys are not carried over.
            displayCount = getInt(K_DISPLAY, d.displayCount)
                .coerceIn(AppSettings.DISPLAY_RANGE),
            fontSp = getFloat(K_FONT_SP, d.fontSp).coerceIn(AppSettings.MIN_FONT, AppSettings.MAX_FONT),
            bgAlpha = getInt(K_BG_ALPHA, d.bgAlpha).coerceIn(0, 255),
            showSource = getBoolean(K_SHOW_SRC, d.showSource),
            overlayLocked = getBoolean(K_OV_LOCKED, d.overlayLocked),
            autoStart = getBoolean(K_AUTO_START, d.autoStart),
            overlayX = getFloat(K_OV_FX, d.overlayX),
            overlayY = getFloat(K_OV_FY, d.overlayY),
            theme = if (contains(K_THEME)) ThemeMode.of(getString(K_THEME, null))
            else if (getBoolean(K_LEGACY_DARK, false)) ThemeMode.DARK else d.theme,
            language = AppLanguage.of(getString(K_LANG, null)),
        )
    }

    private fun write(s: AppSettings) {
        prefs.edit()
            .putString(K_ENGINE, s.engine.key)
            .putString(K_TARGET, s.target.key)
            .putString(K_BAIDU_ID, s.baiduAppId)
            .putString(K_BAIDU_KEY, s.baiduKey)
            .putString(K_QWEN_KEY, s.qwenKey)
            .putString(K_QWEN_MODEL, s.qwenModel)
            .putBoolean(K_QWEN_INTL, s.qwenIntl)
            .putString(K_DS_KEY, s.deepseekKey)
            .putString(K_DS_MODEL, s.deepseekModel)
            .putString(K_CUSTOM_URL, s.customBaseUrl)
            .putString(K_CUSTOM_KEY, s.customKey)
            .putString(K_CUSTOM_MODEL, s.customModel)
            .putInt(K_DISPLAY, s.displayCount)
            .putFloat(K_FONT_SP, s.fontSp)
            .putInt(K_BG_ALPHA, s.bgAlpha)
            .putBoolean(K_SHOW_SRC, s.showSource)
            .putBoolean(K_OV_LOCKED, s.overlayLocked)
            .putBoolean(K_AUTO_START, s.autoStart)
            .putFloat(K_OV_FX, s.overlayX)
            .putFloat(K_OV_FY, s.overlayY)
            .putString(K_THEME, s.theme.key)
            .putString(K_LANG, s.language.tag)
            .remove(K_LEGACY_DARK)
            .remove(K_LEGACY_LINES)
            .remove(K_LEGACY_DISPLAY)
            .remove(K_LEGACY_OV_X)
            .remove(K_LEGACY_OV_Y)
            .apply()
    }

    companion object {
        private const val NAME = "settings"
        private const val K_ENGINE = "engine"
        private const val K_TARGET = "target_lang"
        private const val K_BAIDU_ID = "baidu_appid"
        private const val K_BAIDU_KEY = "baidu_key"
        private const val K_QWEN_KEY = "qwen_key"
        private const val K_QWEN_MODEL = "qwen_model"
        private const val K_QWEN_INTL = "qwen_intl"
        private const val K_DS_KEY = "deepseek_key"
        private const val K_DS_MODEL = "deepseek_model"
        private const val K_CUSTOM_URL = "custom_base_url"
        private const val K_CUSTOM_KEY = "custom_key"
        private const val K_CUSTOM_MODEL = "custom_model"
        private const val K_DISPLAY = "display_lines"
        private const val K_FONT_SP = "font_sp"
        private const val K_BG_ALPHA = "bg_alpha"
        private const val K_SHOW_SRC = "show_src"
        private const val K_OV_LOCKED = "ov_locked"
        private const val K_AUTO_START = "auto_start"
        private const val K_OV_FX = "ov_fx"
        private const val K_OV_FY = "ov_fy"
        private const val K_THEME = "theme"
        private const val K_LANG = "lang"
        private const val K_LEGACY_DARK = "dark"
        private const val K_LEGACY_LINES = "last_lines"
        private const val K_LEGACY_DISPLAY = "display_count"
        private const val K_LEGACY_OV_X = "ov_x"
        private const val K_LEGACY_OV_Y = "ov_y"

        @Volatile private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context).also { instance = it }
            }
    }
}
