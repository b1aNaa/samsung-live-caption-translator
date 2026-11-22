package com.livesub.translate.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livesub.translate.R
import com.livesub.translate.describeError
import com.livesub.translate.settings.AppLanguage
import com.livesub.translate.settings.AppSettings
import com.livesub.translate.settings.Engine
import com.livesub.translate.settings.TargetLang
import com.livesub.translate.settings.ThemeMode
import com.livesub.translate.translate.TranslationRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private typealias Update = ((AppSettings) -> AppSettings) -> Unit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    update: Update,
    onLanguageChange: (AppLanguage) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel(stringResource(R.string.section_engine))
            EngineCard(settings, update)

            Spacer(Modifier.height(4.dp))
            SectionLabel(stringResource(R.string.label_target_lang))
            TargetCard(settings, update)

            Spacer(Modifier.height(4.dp))
            SectionLabel(stringResource(R.string.section_credentials, engineName(settings.engine)))
            CredentialCard(settings, update)

            Spacer(Modifier.height(4.dp))
            SectionLabel(stringResource(R.string.section_overlay))
            OverlayCard(settings, update)

            Spacer(Modifier.height(4.dp))
            SectionLabel(stringResource(R.string.section_appearance))
            AppearanceCard(settings, update, onLanguageChange)

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun EngineCard(settings: AppSettings, update: Update) {
    GroupCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        Engine.entries.forEachIndexed { i, engine ->
            if (i > 0) RowDivider(inset = 56.dp)
            EngineRow(engine, selected = settings.engine == engine) { update { it.copy(engine = engine) } }
        }
    }
}

@Composable
private fun EngineRow(engine: Engine, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (selected) Brush.linearGradient(listOf(scheme.primary, scheme.tertiary))
                    else Brush.linearGradient(listOf(scheme.surfaceContainerHigh, scheme.surfaceContainerHigh))
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                engineMonogram(engine),
                color = if (selected) Color.White else scheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                engineName(engine),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                stringResource(engineDescription(engine)),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (selected) Icon(Icons.Rounded.Check, null, tint = scheme.primary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetCard(settings: AppSettings, update: Update) {
    GroupCard(contentPadding = PaddingValues(14.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TargetLang.entries.forEach { lang ->
                val selected = settings.target == lang
                Surface(
                    onClick = { update { it.copy(target = lang) } },
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                ) {
                    Text(
                        lang.nativeName,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CredentialCard(settings: AppSettings, update: Update) {
    GroupCard(contentPadding = PaddingValues(16.dp), spacing = 12.dp) {
        // Re-key per engine so each field starts from that engine's stored value.
        key(settings.engine) {
            when (settings.engine) {
                Engine.BAIDU -> {
                    SettingField(settings.baiduAppId, { v -> update { it.copy(baiduAppId = v) } }, stringResource(R.string.field_baidu_appid))
                    SettingField(settings.baiduKey, { v -> update { it.copy(baiduKey = v) } }, stringResource(R.string.field_baidu_key), secret = true)
                    KeyHint(R.string.hint_baidu)
                }
                Engine.QWEN -> {
                    SettingField(settings.qwenKey, { v -> update { it.copy(qwenKey = v) } }, stringResource(R.string.field_api_key), secret = true)
                    SettingField(settings.qwenModel, { v -> update { it.copy(qwenModel = v) } }, stringResource(R.string.field_model))
                    SwitchRow(
                        title = stringResource(R.string.field_qwen_intl),
                        subtitle = stringResource(R.string.field_qwen_intl_desc),
                        icon = Icons.Rounded.Public,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        checked = settings.qwenIntl,
                        onChange = { v -> update { it.copy(qwenIntl = v) } },
                    )
                    KeyHint(R.string.hint_qwen)
                }
                Engine.DEEPSEEK -> {
                    SettingField(settings.deepseekKey, { v -> update { it.copy(deepseekKey = v) } }, stringResource(R.string.field_api_key), secret = true)
                    SettingField(settings.deepseekModel, { v -> update { it.copy(deepseekModel = v) } }, stringResource(R.string.field_model))
                    KeyHint(R.string.hint_deepseek)
                }
                Engine.CUSTOM -> {
                    SettingField(
                        settings.customBaseUrl, { v -> update { it.copy(customBaseUrl = v) } },
                        stringResource(R.string.field_base_url),
                        placeholder = "https://api.example.com/v1",
                        keyboardType = KeyboardType.Uri,
                    )
                    SettingField(settings.customKey, { v -> update { it.copy(customKey = v) } }, stringResource(R.string.field_api_key), secret = true)
                    SettingField(settings.customModel, { v -> update { it.copy(customModel = v) } }, stringResource(R.string.field_model))
                    KeyHint(R.string.hint_custom)
                }
            }
        }
        TestTranslation(settings)
    }
}

@Composable
private fun KeyHint(res: Int) {
    SelectionContainer {
        Text(
            stringResource(res),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

private sealed interface TestState {
    data object Idle : TestState
    data object Running : TestState
    data class Ok(val text: String, val millis: Long) : TestState
    data class Failed(val message: String) : TestState
}

@Composable
private fun TestTranslation(settings: AppSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember(settings.translatorConfig()) { mutableStateOf<TestState>(TestState.Idle) }
    val sample = stringResource(if (settings.target == TargetLang.EN) R.string.test_sample_zh else R.string.test_sample_en)

    FilledTonalButton(
        enabled = settings.hasCredentials && state != TestState.Running,
        onClick = {
            scope.launch {
                state = TestState.Running
                val start = System.nanoTime()
                state = try {
                    val out = settings.createTranslator().translate(TranslationRequest(sample))
                    TestState.Ok(out, (System.nanoTime() - start) / 1_000_000)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    TestState.Failed(context.describeError(e))
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        colors = primaryTonal(),
    ) {
        if (state == TestState.Running) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Rounded.Speed, null, Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.action_test))
    }

    AnimatedContent(state, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "test") { s ->
        when (s) {
            is TestState.Ok -> ResultBox(
                icon = Icons.Rounded.CheckCircle,
                tint = AppTheme.extra.ok,
                title = stringResource(R.string.test_ok, s.millis),
                body = "$sample\n→ ${s.text}",
            )
            is TestState.Failed -> ResultBox(
                icon = Icons.Rounded.ErrorOutline,
                tint = MaterialTheme.colorScheme.error,
                title = stringResource(R.string.test_failed),
                body = s.message,
            )
            else -> Spacer(Modifier.height(0.dp))
        }
    }
}

@Composable
private fun ResultBox(icon: ImageVector, tint: Color, title: String, body: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(tint.copy(alpha = 0.08f))
            .padding(14.dp)
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = tint)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge, color = tint)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun OverlayCard(settings: AppSettings, update: Update) {
    GroupCard(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)) {
        OverlayPreview(settings)
        Spacer(Modifier.height(16.dp))
        LabeledSlider(
            icon = Icons.Rounded.FormatSize,
            label = stringResource(R.string.label_font_size),
            valueText = "${settings.fontSp.roundToInt()} sp",
            value = settings.fontSp,
            range = AppSettings.MIN_FONT..AppSettings.MAX_FONT,
            onChange = { v -> update { it.copy(fontSp = v.roundToInt().toFloat()) } },
        )
        LabeledSlider(
            icon = Icons.Rounded.Opacity,
            label = stringResource(R.string.label_bg_alpha),
            valueText = "${settings.bgAlpha * 100 / 255}%",
            value = settings.bgAlpha.toFloat(),
            range = 0f..255f,
            onChange = { v -> update { it.copy(bgAlpha = v.roundToInt()) } },
        )
        LabeledSlider(
            icon = Icons.Rounded.ViewAgenda,
            label = stringResource(R.string.label_display_count),
            valueText = "${settings.displayCount}",
            value = settings.displayCount.toFloat(),
            range = AppSettings.DISPLAY_RANGE.first.toFloat()..AppSettings.DISPLAY_RANGE.last.toFloat(),
            steps = AppSettings.DISPLAY_RANGE.last - AppSettings.DISPLAY_RANGE.first - 1,
            onChange = { v -> update { it.copy(displayCount = v.roundToInt()) } },
        )
        RowDivider(inset = 0.dp)
        SwitchRow(
            title = stringResource(R.string.label_show_source),
            subtitle = stringResource(R.string.label_show_source_desc),
            icon = Icons.Rounded.Subtitles,
            iconTint = MaterialTheme.colorScheme.secondary,
            checked = settings.showSource,
            onChange = { v -> update { it.copy(showSource = v) } },
        )
        RowDivider()
        SwitchRow(
            title = stringResource(R.string.label_lock),
            subtitle = stringResource(R.string.label_lock_desc),
            icon = Icons.Rounded.Lock,
            iconTint = MaterialTheme.colorScheme.tertiary,
            checked = settings.overlayLocked,
            onChange = { v -> update { it.copy(overlayLocked = v) } },
        )
        RowDivider()
        SettingRow(
            title = stringResource(R.string.action_reset_position),
            icon = Icons.Rounded.RestartAlt,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = { update { it.copy(overlayX = Float.NaN, overlayY = Float.NaN) } },
        )
    }
}

/** Mock film still with the subtitle drawn as the overlay will draw it. */
@Composable
private fun OverlayPreview(settings: AppSettings) {
    val shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 2f), 6f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(
                Brush.verticalGradient(listOf(Color(0xFF1C2541), Color(0xFF3A3F6B), Color(0xFF8C6A7E), Color(0xFFD9A07A))),
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // A low sun over a dark horizon so it reads as a video frame.
        Box(
            Modifier
                .align(Alignment.Center)
                .padding(bottom = 30.dp)
                .size(90.dp)
                .background(
                    Brush.radialGradient(listOf(Color(0xFFFFE3B0).copy(alpha = 0.9f), Color.Transparent)),
                    CircleShape,
                )
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF2B2540), Color(0xFF16131F))))
        )
        Column(
            Modifier
                .padding(bottom = 14.dp, start = 12.dp, end = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = settings.bgAlpha / 255f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (settings.showSource) {
                Text(
                    stringResource(R.string.preview_source),
                    style = TextStyle(shadow = shadow),
                    color = Color(0xC8CDD2DC),
                    fontSize = (settings.fontSp * 0.72f).sp,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                stringResource(R.string.preview_translation),
                style = TextStyle(shadow = shadow),
                color = Color.White,
                fontSize = settings.fontSp.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceCard(settings: AppSettings, update: Update, onLanguageChange: (AppLanguage) -> Unit) {
    GroupCard(contentPadding = PaddingValues(16.dp), spacing = 10.dp) {
        Text(stringResource(R.string.label_theme), style = MaterialTheme.typography.bodyMedium)
        val themes = listOf(
            ThemeMode.SYSTEM to R.string.theme_system,
            ThemeMode.LIGHT to R.string.theme_light,
            ThemeMode.DARK to R.string.theme_dark,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            themes.forEachIndexed { i, (mode, label) ->
                SegmentedButton(
                    selected = settings.theme == mode,
                    onClick = { update { it.copy(theme = mode) } },
                    shape = SegmentedButtonDefaults.itemShape(i, themes.size),
                    colors = segmentedColors(),
                    icon = {},
                ) { Text(stringResource(label)) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.label_language), style = MaterialTheme.typography.bodyMedium)
        val languages = listOf(
            AppLanguage.SYSTEM to stringResource(R.string.language_system),
            AppLanguage.ZH to "中文",
            AppLanguage.EN to "English",
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            languages.forEachIndexed { i, (lang, label) ->
                SegmentedButton(
                    selected = settings.language == lang,
                    onClick = { if (settings.language != lang) onLanguageChange(lang) },
                    shape = SegmentedButtonDefaults.itemShape(i, languages.size),
                    colors = segmentedColors(),
                    icon = {},
                ) { Text(label) }
            }
        }
    }
}

@Composable
private fun segmentedColors() = SegmentedButtonDefaults.colors(
    activeContainerColor = MaterialTheme.colorScheme.onSurface,
    activeContentColor = MaterialTheme.colorScheme.surface,
    activeBorderColor = MaterialTheme.colorScheme.onSurface,
    inactiveContainerColor = Color.Transparent,
    inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant,
)

@Composable
private fun LabeledSlider(
    icon: ImageVector,
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    steps: Int = 0,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onSurface,
                activeTrackColor = MaterialTheme.colorScheme.onSurface,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                activeTickColor = MaterialTheme.colorScheme.surface,
                inactiveTickColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

private fun engineMonogram(engine: Engine) = when (engine) {
    Engine.BAIDU -> "Bd"
    Engine.QWEN -> "Qw"
    Engine.DEEPSEEK -> "DS"
    Engine.CUSTOM -> "AI"
}

private fun engineDescription(engine: Engine) = when (engine) {
    Engine.BAIDU -> R.string.engine_baidu_desc
    Engine.QWEN -> R.string.engine_qwen_desc
    Engine.DEEPSEEK -> R.string.engine_deepseek_desc
    Engine.CUSTOM -> R.string.engine_custom_desc
}
