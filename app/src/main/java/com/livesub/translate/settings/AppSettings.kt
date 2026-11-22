package com.livesub.translate.settings

import com.livesub.translate.translate.BaiduTranslator
import com.livesub.translate.translate.OpenAiCompatTranslator
import com.livesub.translate.translate.QwenMtTranslator
import com.livesub.translate.translate.Translator

enum class Engine(val key: String) {
    BAIDU("baidu"), QWEN("qwen"), DEEPSEEK("deepseek"), CUSTOM("custom");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: BAIDU
    }
}

/**
 * Target languages and how each engine names them.
 * @property llmName used in prompts for Qwen-MT and chat models.
 */
enum class TargetLang(val key: String, val baiduCode: String, val llmName: String, val nativeName: String) {
    ZH_HANS("zh", "zh", "Simplified Chinese", "简体中文"),
    ZH_HANT("zh-Hant", "cht", "Traditional Chinese", "繁體中文"),
    EN("en", "en", "English", "English"),
    JA("ja", "jp", "Japanese", "日本語"),
    KO("ko", "kor", "Korean", "한국어");

    /** How translated sentences are glued into one line: no spaces in Chinese/Japanese. */
    val sentenceJoiner get() = if (this == ZH_HANS || this == ZH_HANT || this == JA) "" else " "

    /** Qwen-MT expects plain "Chinese" for Simplified. */
    val qwenName get() = if (this == ZH_HANS) "Chinese" else llmName

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: ZH_HANS
    }
}

enum class ThemeMode(val key: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

enum class AppLanguage(val tag: String) {
    SYSTEM(""), ZH("zh"), EN("en");

    companion object {
        fun of(tag: String?) = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

data class AppSettings(
    val engine: Engine = Engine.BAIDU,
    val target: TargetLang = TargetLang.ZH_HANS,

    val baiduAppId: String = "",
    val baiduKey: String = "",
    val qwenKey: String = "",
    val qwenModel: String = QwenMtTranslator.DEFAULT_MODEL,
    val qwenIntl: Boolean = false,
    val deepseekKey: String = "",
    val deepseekModel: String = OpenAiCompatTranslator.DEEPSEEK_MODEL,
    val customBaseUrl: String = "",
    val customKey: String = "",
    val customModel: String = "",

    /** Subtitle lines visible at once; older lines scroll off the top. */
    val displayCount: Int = 4,
    val fontSp: Float = 18f,
    val bgAlpha: Int = 160,
    val showSource: Boolean = true,
    /** Touches pass through the overlay to the app underneath (can't be dragged). */
    val overlayLocked: Boolean = false,
    /** Start translating as soon as Live Caption shows up. */
    val autoStart: Boolean = false,
    /** Overlay centre offset from screen centre, as a fraction of screen width; NaN = default. */
    val overlayX: Float = Float.NaN,
    /** Overlay top edge as a fraction of screen height; NaN = default. */
    val overlayY: Float = Float.NaN,

    val theme: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
) {
    val hasCredentials: Boolean
        get() = when (engine) {
            Engine.BAIDU -> baiduAppId.isNotBlank() && baiduKey.isNotBlank()
            Engine.QWEN -> qwenKey.isNotBlank()
            Engine.DEEPSEEK -> deepseekKey.isNotBlank()
            Engine.CUSTOM -> customKey.isNotBlank() && customBaseUrl.isNotBlank() && customModel.isNotBlank()
        }

    /** Everything a translator depends on; a change here means rebuilding it. */
    fun translatorConfig(): List<Any> = when (engine) {
        Engine.BAIDU -> listOf(engine, target, baiduAppId, baiduKey)
        Engine.QWEN -> listOf(engine, target, qwenKey, qwenModel, qwenIntl)
        Engine.DEEPSEEK -> listOf(engine, target, deepseekKey, deepseekModel)
        Engine.CUSTOM -> listOf(engine, target, customBaseUrl, customKey, customModel)
    }

    fun createTranslator(): Translator = when (engine) {
        Engine.BAIDU -> BaiduTranslator(baiduAppId.trim(), baiduKey.trim(), target.baiduCode)
        Engine.QWEN -> QwenMtTranslator(qwenKey.trim(), qwenModel.trim(), target.qwenName, qwenIntl)
        Engine.DEEPSEEK -> OpenAiCompatTranslator(
            OpenAiCompatTranslator.DEEPSEEK_BASE,
            deepseekKey.trim(),
            deepseekModel.trim().ifBlank { OpenAiCompatTranslator.DEEPSEEK_MODEL },
            target.llmName,
        )
        Engine.CUSTOM -> OpenAiCompatTranslator(customBaseUrl, customKey.trim(), customModel.trim(), target.llmName)
    }

    companion object {
        const val MIN_FONT = 12f
        const val MAX_FONT = 32f
        val DISPLAY_RANGE = 1..6
    }
}
