package com.livesub.translate.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livesub.translate.CaptionA11yService
import com.livesub.translate.HistoryEntry
import com.livesub.translate.R
import com.livesub.translate.ServiceStatus
import com.livesub.translate.StartResult
import com.livesub.translate.TranslateTileService
import com.livesub.translate.settings.AppSettings
import com.livesub.translate.settings.Engine

@Composable
fun HomeScreen(
    settings: AppSettings,
    status: ServiceStatus.State,
    history: List<HistoryEntry>,
    update: ((AppSettings) -> AppSettings) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val context = LocalContext.current
    val ready = status.connected && settings.hasCredentials
    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(onOpenHistory, onOpenSettings)

            HeroCard(
                settings = settings,
                status = status,
                latest = history.lastOrNull(),
                update = update,
                onStart = {
                    when (CaptionA11yService.start()) {
                        StartResult.STARTED -> Unit
                        StartResult.NO_SERVICE -> {
                            context.toast(R.string.toast_need_a11y)
                            context.openAccessibilitySettings()
                        }
                        StartResult.NO_CREDENTIALS -> {
                            context.toast(R.string.toast_need_key)
                            onOpenSettings()
                        }
                    }
                },
                onStop = { CaptionA11yService.stop() },
            )

            AnimatedVisibility(
                visible = !ready,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Spacer(Modifier.height(4.dp))
                    SectionLabel(stringResource(R.string.setup_title))
                    SetupCard(settings, status, onOpenSettings)
                }
            }

            Spacer(Modifier.height(4.dp))
            SectionLabel(stringResource(R.string.home_recent)) {
                if (history.isNotEmpty()) {
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onOpenHistory)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.action_view_all),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward, null,
                            Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            RecentCard(history)

            Spacer(Modifier.height(4.dp))
            SectionLabel(stringResource(R.string.home_options))
            GroupCard {
                SwitchRow(
                    title = stringResource(R.string.label_auto_start),
                    subtitle = stringResource(R.string.label_auto_start_desc),
                    icon = Icons.Rounded.Bolt,
                    iconTint = AppTheme.extra.warn,
                    checked = settings.autoStart,
                    onChange = { v -> update { it.copy(autoStart = v) } },
                )
                RowDivider()
                SettingRow(
                    title = stringResource(R.string.tile_title),
                    subtitle = stringResource(R.string.tile_desc),
                    icon = Icons.Rounded.Dashboard,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    onClick = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        { context.requestAddTile() }
                    } else null,
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Text(
                            stringResource(R.string.action_add_tile),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                RowDivider()
                SettingRow(
                    title = stringResource(R.string.help_title),
                    subtitle = stringResource(R.string.home_help_desc),
                    icon = Icons.AutoMirrored.Rounded.HelpOutline,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    onClick = onOpenHelp,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Rounded.Shield, null,
                    Modifier
                        .size(13.dp)
                        .padding(top = 2.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.home_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun Header(onOpenHistory: () -> Unit, onOpenSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.home_eyebrow),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TonalIconButton(Icons.Rounded.History, stringResource(R.string.history_title), onOpenHistory)
            TonalIconButton(Icons.Rounded.Settings, stringResource(R.string.settings_title), onOpenSettings)
        }
    }
}

@Composable
private fun HeroCard(
    settings: AppSettings,
    status: ServiceStatus.State,
    latest: HistoryEntry?,
    update: ((AppSettings) -> AppSettings) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val extra = AppTheme.extra
    val running = status.running
    val shape = RoundedCornerShape(30.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .shadow(28.dp, shape, ambientColor = extra.glowA.copy(alpha = 0.35f), spotColor = extra.glowA.copy(alpha = 0.35f))
            .clip(shape)
            .aurora(animate = running)
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassPill {
                    PulseDot(running, if (running) Color(0xFF6CF0B0) else Color.White.copy(alpha = 0.45f))
                    Spacer(Modifier.width(8.dp))
                    AnimatedContent(running, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "status") {
                        Text(
                            stringResource(if (it) R.string.status_running else R.string.status_stopped),
                            style = MaterialTheme.typography.labelLarge,
                            color = extra.onHero,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.status_route, engineName(settings.engine), settings.target.nativeName),
                    style = MaterialTheme.typography.labelMedium,
                    color = extra.onHero.copy(alpha = 0.62f),
                )
            }

            SubtitleStage(running, latest, settings.showSource)

            AnimatedContent(running, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "cta") { on ->
                if (on) {
                    Button(
                        onClick = onStop,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.10f),
                            contentColor = Color.White,
                        ),
                    ) {
                        Icon(Icons.Rounded.Stop, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_stop), style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    Button(
                        onClick = onStart,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF14172F),
                        ),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(22.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_start), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            AnimatedVisibility(running) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroToggle(
                        icon = if (settings.overlayLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        label = stringResource(if (settings.overlayLocked) R.string.hero_locked else R.string.hero_unlocked),
                        active = settings.overlayLocked,
                        modifier = Modifier.weight(1f),
                    ) { update { it.copy(overlayLocked = !it.overlayLocked) } }
                    HeroToggle(
                        icon = Icons.Rounded.Subtitles,
                        label = stringResource(R.string.hero_show_source),
                        active = settings.showSource,
                        modifier = Modifier.weight(1f),
                    ) { update { it.copy(showSource = !it.showSource) } }
                }
            }
        }
    }
}

@Composable
private fun GlassPill(content: @Composable () -> Unit) {
    Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            content()
        }
    }
}

/** A letterboxed "screen" previewing the latest subtitle, like the overlay itself. */
@Composable
private fun SubtitleStage(running: Boolean, latest: HistoryEntry?, showSource: Boolean) {
    val onHero = AppTheme.extra.onHero
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.28f))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = latest?.takeIf { running },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "stage",
        ) { entry ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (entry != null) {
                    if (showSource) {
                        Text(
                            entry.source,
                            style = MaterialTheme.typography.bodySmall,
                            color = onHero.copy(alpha = 0.55f),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        entry.translation,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
                        color = onHero,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Icon(
                        Icons.Rounded.Subtitles, null,
                        Modifier.size(26.dp),
                        tint = onHero.copy(alpha = 0.35f),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(if (running) R.string.stage_waiting else R.string.stage_idle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = onHero.copy(alpha = 0.62f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroToggle(
    icon: ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (active) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (active) 0.28f else 0.10f)),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, Modifier.size(17.dp), tint = Color.White.copy(alpha = if (active) 1f else 0.7f))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = if (active) 1f else 0.7f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SetupCard(settings: AppSettings, status: ServiceStatus.State, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    GroupCard {
        SetupStep(
            done = status.connected,
            icon = Icons.Rounded.Accessibility,
            title = stringResource(R.string.setup_a11y_title),
            description = stringResource(if (status.connected) R.string.setup_a11y_done else R.string.setup_a11y_desc),
            actionLabel = stringResource(R.string.action_enable),
            onAction = { context.openAccessibilitySettings() },
        )
        if (!status.connected) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 52.dp, bottom = 10.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(AppTheme.extra.warn.copy(alpha = 0.09f))
                    .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 2.dp)
            ) {
                Text(
                    stringResource(R.string.setup_restricted_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = { context.openAppDetails() }, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.action_app_info))
                }
            }
        }
        RowDivider()
        SetupStep(
            done = settings.hasCredentials,
            icon = Icons.Rounded.Key,
            title = stringResource(R.string.setup_engine_title),
            description = if (settings.hasCredentials) engineName(settings.engine)
            else stringResource(R.string.setup_engine_desc),
            actionLabel = stringResource(R.string.action_configure),
            onAction = onOpenSettings,
        )
    }
}

@Composable
private fun SetupStep(
    done: Boolean,
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val extra = AppTheme.extra
    SettingRow(
        title = title,
        subtitle = description,
        icon = if (done) Icons.Rounded.CheckCircle else icon,
        iconTint = if (done) extra.ok else MaterialTheme.colorScheme.primary,
    ) {
        if (!done) {
            FilledTonalButton(onClick = onAction, colors = primaryTonal()) { Text(actionLabel) }
        }
    }
}

@Composable
private fun RecentCard(history: List<HistoryEntry>) {
    GroupCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        if (history.isEmpty()) {
            Text(
                stringResource(R.string.home_recent_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 14.dp),
            )
        } else {
            history.takeLast(3).asReversed().forEachIndexed { i, entry ->
                if (i > 0) RowDivider(inset = 0.dp)
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text(
                        entry.translation,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        entry.source,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun engineName(engine: Engine): String = stringResource(
    when (engine) {
        Engine.BAIDU -> R.string.engine_baidu
        Engine.QWEN -> R.string.engine_qwen
        Engine.DEEPSEEK -> R.string.engine_deepseek
        Engine.CUSTOM -> R.string.engine_custom_short
    }
)

internal fun Context.toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_LONG).show()

internal fun Context.openAccessibilitySettings() =
    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

private fun Context.openAppDetails() = startActivity(
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
)

private fun Context.requestAddTile() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val sbm = getSystemService(StatusBarManager::class.java) ?: return
    sbm.requestAddTileService(
        ComponentName(this, TranslateTileService::class.java),
        getString(R.string.app_name),
        Icon.createWithResource(this, R.drawable.ic_tile),
        mainExecutor,
    ) { result ->
        if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) toast(R.string.toast_tile_exists)
    }
}
