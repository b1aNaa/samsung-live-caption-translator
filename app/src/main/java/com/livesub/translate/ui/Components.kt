package com.livesub.translate.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.livesub.translate.R
import kotlin.math.cos
import kotlin.math.sin

/** Small label above a group of settings, outside the card. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 2.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        action?.invoke(this)
    }
}

/** White card with a hairline edge and a soft, wide shadow. */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
    spacing: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val extra = AppTheme.extra
    val shape = MaterialTheme.shapes.large
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(18.dp, shape, ambientColor = extra.cardShadow, spotColor = extra.cardShadow),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, extra.hairline),
    ) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(spacing)) {
            content()
        }
    }
}

/** A [GroupCard] with an optional title row inside, for free-form content. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    GroupCard(modifier, contentPadding = PaddingValues(18.dp), spacing = 14.dp) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                action?.invoke(this)
            }
        }
        content()
    }
}

/** Divider between rows of a [GroupCard], indented past the icon column. */
@Composable
fun RowDivider(inset: Dp = 52.dp) {
    HorizontalDivider(
        Modifier.padding(start = inset),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** Rounded square holding an icon in a soft tint of [tint]. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Dp = 36.dp) {
    Box(
        Modifier
            .size(size)
            .background(tint.copy(alpha = 0.12f), RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size * 0.52f), tint = tint)
    }
}

/** A row in a [GroupCard]: icon, title, optional subtitle, optional trailing content. */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconBadge(icon, iconTint)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
) {
    SettingRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconTint = iconTint,
        onClick = { onChange(!checked) },
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                uncheckedBorderColor = Color.Transparent,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                uncheckedThumbColor = Color.White,
            ),
        )
    }
}

/** Dot that breathes while [active]. */
@Composable
fun PulseDot(active: Boolean, color: Color, modifier: Modifier = Modifier) {
    val alpha = if (active) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
            label = "pulseAlpha",
        ).value
    } else 1f
    Box(
        modifier
            .size(8.dp)
            .alpha(alpha)
            .background(color, CircleShape)
    )
}

/**
 * Night-sky background with two slow aurora glows. The glows drift only while
 * [animate] is on, so an idle screen costs nothing.
 */
@Composable
fun Modifier.aurora(animate: Boolean): Modifier {
    val extra = AppTheme.extra
    val phase = if (animate) {
        rememberInfiniteTransition(label = "aurora").animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing)),
            label = "auroraPhase",
        ).value
    } else 0.6f
    val strength = if (animate) 1f else 0.55f
    return drawBehind {
        drawRect(Brush.verticalGradient(listOf(extra.heroTop, extra.heroBottom)))
        val r = size.maxDimension * 0.75f
        val a = Offset(
            size.width * (0.82f + 0.08f * cos(phase)),
            size.height * (0.05f + 0.10f * sin(phase)),
        )
        drawCircle(Brush.radialGradient(listOf(extra.glowA.copy(alpha = 0.55f * strength), Color.Transparent), a, r), r, a)
        val b = Offset(
            size.width * (0.05f + 0.10f * sin(phase * 0.7f)),
            size.height * (1.0f + 0.08f * cos(phase * 0.7f)),
        )
        drawCircle(Brush.radialGradient(listOf(extra.glowB.copy(alpha = 0.30f * strength), Color.Transparent), b, r * 0.8f), r * 0.8f, b)
    }
}

/**
 * Text field that keeps its own editing state and reports every change.
 * Feeding a text field straight from an async settings flow makes the cursor
 * jump while typing; this avoids that.
 */
@Composable
fun SettingField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var text by remember(label) { mutableStateOf(value) }
    var revealed by rememberSaveable(label) { mutableStateOf(false) }
    val hidden = secret && !revealed
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onValueChange(it)
        },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color.Transparent,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (hidden) KeyboardType.Password else keyboardType,
            autoCorrectEnabled = false,
        ),
        visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (secret) {
            {
                IconButton(onClick = { revealed = !revealed }) {
                    Icon(
                        if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = stringResource(if (revealed) R.string.action_hide else R.string.action_show),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else null,
    )
}

/** Tonal button colours in the primary hue (the default uses the secondary). */
@Composable
fun primaryTonal() = ButtonDefaults.filledTonalButtonColors(
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

/** Round icon button with a hairline edge, for top bars. */
@Composable
fun TonalIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, AppTheme.extra.hairline),
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}
