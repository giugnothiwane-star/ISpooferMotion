package com.chessoverlay.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chessoverlay.ui.theme.Palette

/** Carte « verre dépoli » : fond translucide, liseré dégradé violet → cyan, lueur subtile. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    glow: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(120), label = "press")
    Column(
        modifier
            .scale(scale)
            .then(
                if (glow) Modifier.drawBehind {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Palette.Violet.copy(alpha = 0.16f), Color.Transparent),
                            center = Offset(size.width * 0.15f, 0f), radius = size.maxDimension * 0.8f,
                        ),
                        radius = size.maxDimension, center = Offset(size.width * 0.15f, 0f),
                    )
                } else Modifier
            )
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.065f), Color.White.copy(alpha = 0.025f))))
            .border(1.dp, Brush.linearGradient(listOf(Palette.Violet.copy(alpha = 0.45f), Color.White.copy(alpha = 0.06f), Palette.Cyan.copy(alpha = 0.35f))), shape)
            .then(if (onClick != null) Modifier.clickable(interaction, indication = androidx.compose.material3.ripple(), onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/** Bouton principal au dégradé violet → cyan avec retour visuel à l'appui. */
@Composable
fun GlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    secondary: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(110), label = "btn")
    val shape = RoundedCornerShape(16.dp)
    val bg: Brush = when {
        !enabled -> Brush.linearGradient(listOf(Palette.GraphiteHigh, Palette.GraphiteHigh))
        secondary -> Brush.linearGradient(listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.04f)))
        else -> Palette.Accent
    }
    Row(
        modifier
            .scale(scale)
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(bg)
            .then(if (secondary) Modifier.border(1.dp, Palette.GraphiteLine, shape) else Modifier)
            .clickable(interaction, androidx.compose.material3.ripple(color = Color.White), enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (!enabled) Palette.TextMuted else if (secondary) Palette.TextPrimary else Color.White
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Bouton icône rond, translucide. */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 46.dp,
) {
    val bg by animateColorAsState(if (active) Palette.Violet.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f), label = "bg")
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, if (active) Palette.Violet.copy(alpha = 0.7f) else Palette.GraphiteLine, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (enabled) Palette.TextPrimary else Palette.TextMuted, modifier = Modifier.size(size * 0.46f))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(Palette.Accent))
        Spacer(Modifier.width(10.dp))
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Ligne libellé / valeur utilisée dans les panneaux d'analyse. */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Palette.TextPrimary, emphasize: Boolean = false) {
    Row(modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, modifier = Modifier.weight(0.42f))
        Text(
            value,
            style = if (emphasize) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = valueColor,
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(0.58f),
        )
    }
}

/** Pastille colorée (évaluation, classification, verdict). */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) color else color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, color = if (filled) Color.Black else color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Ligne de réglage avec interrupteur. */
@Composable
fun SettingSwitch(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = Palette.Violet,
                uncheckedThumbColor = Palette.TextMuted, uncheckedTrackColor = Palette.GraphiteHigh, uncheckedBorderColor = Palette.GraphiteLine,
            ),
        )
    }
}

/**
 * Barre d'évaluation horizontale : la part blanche correspond à la probabilité de gain des Blancs.
 * @param whiteWinPercent 0..100
 */
@Composable
fun EvalBar(whiteWinPercent: Float, label: String, modifier: Modifier = Modifier, vertical: Boolean = false) {
    val p by animateFloatAsState(whiteWinPercent.coerceIn(0f, 100f) / 100f, tween(450), label = "eval")
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .then(if (vertical) Modifier.width(14.dp).fillMaxHeight() else Modifier.fillMaxWidth().height(14.dp))
            .clip(shape)
            .background(Color(0xFF101018))
            .border(1.dp, Palette.GraphiteLine, shape)
            .drawBehind {
                if (vertical) {
                    val h = size.height * p
                    drawRect(Color(0xFFEDEBF7), topLeft = Offset(0f, size.height - h), size = androidx.compose.ui.geometry.Size(size.width, h))
                } else {
                    drawRect(Color(0xFFEDEBF7), size = androidx.compose.ui.geometry.Size(size.width * p, size.height))
                }
                val mid = if (vertical) Offset(0f, size.height / 2) else Offset(size.width / 2, 0f)
                if (vertical) drawLine(Palette.Violet, mid, Offset(size.width, size.height / 2), 2f)
                else drawLine(Palette.Violet, mid, Offset(size.width / 2, size.height), 2f)
            },
    ) {
        if (!vertical) Text(
            label, style = MaterialTheme.typography.labelSmall,
            color = if (p > 0.5f) Color(0xFF1A1A22) else Palette.TextPrimary,
            modifier = Modifier.align(if (p > 0.5f) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 6.dp),
        )
    }
}

/** Petit indicateur d'activité (calcul en cours). */
@Composable
fun PulseDot(active: Boolean, modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
    val a by transition.animateFloat(
        0.35f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(tween(700), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "a",
    )
    Box(
        modifier.size(10.dp).clip(CircleShape)
            .background(if (active) Palette.Cyan.copy(alpha = a) else Palette.Success),
    )
}

/** Fond d'écran commun : dégradé profond + halos lumineux discrets. */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .background(Palette.Backdrop)
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(Palette.Violet.copy(alpha = 0.18f), Color.Transparent), center = Offset(size.width * 0.9f, size.height * 0.05f), radius = size.width * 0.9f),
                    radius = size.width * 0.9f, center = Offset(size.width * 0.9f, size.height * 0.05f),
                )
                drawCircle(
                    Brush.radialGradient(listOf(Palette.Cyan.copy(alpha = 0.10f), Color.Transparent), center = Offset(0f, size.height * 0.75f), radius = size.width * 0.8f),
                    radius = size.width * 0.8f, center = Offset(0f, size.height * 0.75f),
                )
            },
    ) { content() }
}
