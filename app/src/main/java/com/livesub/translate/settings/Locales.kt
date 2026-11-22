package com.livesub.translate.settings

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Applies the in-app language choice; works on every supported API level. */
object Locales {
    fun wrap(base: Context, language: AppLanguage): Context {
        if (language == AppLanguage.SYSTEM) return base
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(Locale.forLanguageTag(language.tag))
        return base.createConfigurationContext(cfg)
    }
}
