package com.livesub.translate

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.livesub.translate.settings.Locales
import com.livesub.translate.settings.SettingsStore
import com.livesub.translate.settings.ThemeMode
import com.livesub.translate.ui.HelpScreen
import com.livesub.translate.ui.HistoryScreen
import com.livesub.translate.ui.HomeScreen
import com.livesub.translate.ui.LiveSubTheme
import com.livesub.translate.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_SETTINGS = "open_settings"
    }

    private enum class Screen { HOME, SETTINGS, HELP, HISTORY }

    private val store by lazy { SettingsStore.get(this) }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Locales.wrap(newBase, SettingsStore.get(newBase).current.language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySystemBars(store.current.theme)
        val initial = if (intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)) Screen.SETTINGS else Screen.HOME
        setContent { App(initial) }
    }

    @Composable
    private fun App(initial: Screen) {
        val settings by store.flow.collectAsStateWithLifecycle()
        val status by ServiceStatus.flow.collectAsStateWithLifecycle()
        val history by TranslationHistory.flow.collectAsStateWithLifecycle()
        var screen by rememberSaveable { mutableStateOf(initial) }

        LaunchedEffect(settings.theme) { applySystemBars(settings.theme) }
        BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

        LiveSubTheme(settings.theme) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    settings = settings,
                    status = status,
                    history = history,
                    update = store::update,
                    onOpenSettings = { screen = Screen.SETTINGS },
                    onOpenHelp = { screen = Screen.HELP },
                    onOpenHistory = { screen = Screen.HISTORY },
                )
                Screen.SETTINGS -> SettingsScreen(
                    settings = settings,
                    update = store::update,
                    onLanguageChange = { lang ->
                        store.update { it.copy(language = lang) }
                        recreate()
                    },
                    onBack = { screen = Screen.HOME },
                )
                Screen.HELP -> HelpScreen(onBack = { screen = Screen.HOME })
                Screen.HISTORY -> HistoryScreen(
                    history = history,
                    onClear = TranslationHistory::clear,
                    onBack = { screen = Screen.HOME },
                )
            }
        }
    }

    private fun applySystemBars(theme: ThemeMode) {
        val dark: (android.content.res.Resources) -> Boolean = { res ->
            when (theme) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM ->
                    (res.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            }
        }
        val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT, dark)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
