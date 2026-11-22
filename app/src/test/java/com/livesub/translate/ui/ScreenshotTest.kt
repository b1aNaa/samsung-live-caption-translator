package com.livesub.translate.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.livesub.translate.HistoryEntry
import com.livesub.translate.ServiceStatus
import com.livesub.translate.settings.AppSettings
import com.livesub.translate.settings.Engine
import com.livesub.translate.settings.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the screens to PNG files under app/build/screenshots so the UI can
 * be reviewed without a device. Run: gradlew testDebugUnitTest
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "zh-w400dp-h1500dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val configured = AppSettings(engine = Engine.QWEN, qwenKey = "sk-test")
    private val ready = ServiceStatus.State(connected = true)
    private val running = ServiceStatus.State(connected = true, running = true)
    private val history = listOf(
        HistoryEntry(1, "I never thought we'd actually make it this far.", "我从没想过我们真的能走到这一步。", 1_700_000_000_000),
        HistoryEntry(2, "So what's the plan now?", "那现在计划是什么？", 1_700_000_004_000),
        HistoryEntry(3, "We wait until the storm passes, then we head north.", "我们等风暴过去，然后往北走。", 1_700_000_009_000),
    )

    private fun shoot(name: String, theme: ThemeMode = ThemeMode.LIGHT, content: @Composable () -> Unit) {
        compose.setContent {
            LiveSubTheme(theme) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
        compose.waitForIdle()
        val view = compose.activity.findViewById<View>(android.R.id.content)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun homeFirstRun() = shoot("home_first_run") {
        HomeScreen(AppSettings(), ServiceStatus.State(), emptyList(), {}, {}, {}, {})
    }

    @Test
    fun homeReady() = shoot("home_ready") {
        HomeScreen(configured, ready, emptyList(), {}, {}, {}, {})
    }

    @Test
    fun homeRunning() = shoot("home_running") {
        HomeScreen(configured, running, history, {}, {}, {}, {})
    }

    @Test
    fun homeRunningDark() = shoot("home_running_dark", ThemeMode.DARK) {
        HomeScreen(configured, running, history, {}, {}, {}, {})
    }

    @Test
    fun settings() = shoot("settings") {
        SettingsScreen(configured, {}, {}, {})
    }

    @Test
    fun settingsDark() = shoot("settings_dark", ThemeMode.DARK) {
        SettingsScreen(configured, {}, {}, {})
    }

    @Test
    fun history() = shoot("history") {
        HistoryScreen(history, {}, {})
    }

    @Test
    fun help() = shoot("help") {
        HelpScreen {}
    }
}
