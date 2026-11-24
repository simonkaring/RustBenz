package app.rustbenz.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.Instant

val PanelShape = RoundedCornerShape(28.dp)

/** The base surface: soft layered card with a hairline edge and a faint top sheen. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 22.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheen = Brush.verticalGradient(listOf(Lux.SurfaceHigh, Lux.Surface))
    Column(
        modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(sheen)
            .border(BorderStroke(1.dp, Lux.Hairline), PanelShape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = Lux.TextMuted) {
    Text(text.uppercase(), style = LuxType.Overline, color = color, modifier = modifier)
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Overline(title, Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action,
                style = LuxType.BodySmall,
                color = Lux.Champagne,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

/** Shows where a value came from: car timestamp, or a SAMPLE tag. */
@Composable
fun SourceTag(source: Source?, now: Instant = Instant.now(), modifier: Modifier = Modifier) {
    when (source) {
        null -> Unit
        is Source.Reported -> Text(age(source.at, now), style = LuxType.BodySmall, color = Lux.TextFaint, modifier = modifier)
        Source.Sample -> SampleChip(modifier)
    }
}

@Composable
fun SampleChip(modifier: Modifier = Modifier) {
    Text(
        "SAMPLE",
        style = LuxType.Overline.copy(fontSize = LuxType.Overline.fontSize * 0.85f),
        color = Lux.ChampagneDeep,
        modifier = modifier
            .border(1.dp, Lux.ChampagneDeep.copy(alpha = 0.6f), CircleShape)
            .padding(horizontal = 7.dp, vertical = 2.dp)
            .semantics { contentDescription = "Sample data" },
    )
}

/** Large metric with unit and source. */
@Composable
fun Metric(label: String, value: String?, unit: String?, source: Source?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Overline(label)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value ?: "—", style = LuxType.Metric, color = if (value == null) Lux.TextFaint else Lux.Text)
            if (value != null && unit != null) {
                Spacer(Modifier.width(4.dp))
                Text(unit, style = LuxType.Unit, color = Lux.TextMuted, maxLines = 1, softWrap = false, modifier = Modifier.padding(bottom = 5.dp))
            }
        }
        if (value == null) {
            Spacer(Modifier.height(4.dp))
            Text("Not reported", style = LuxType.BodySmall, color = Lux.TextFaint)
        } else if (source != null) {
            Spacer(Modifier.height(4.dp))
            SourceTag(source)
        }
    }
}

enum class Tone(val color: Color) { Neutral(Lux.TextMuted), Good(Lux.Positive), Attention(Lux.Caution), Alert(Lux.Critical), Accent(Lux.Champagne) }

@Composable
fun StatusPill(icon: ImageVector, text: String, tone: Tone, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(Lux.SurfaceHigh)
            .border(1.dp, Lux.Hairline, CircleShape)
            .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tone.color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = LuxType.BodySmall, color = Lux.Text)
    }
}

/** Visual state of a remote control. */
sealed interface ControlState {
    data object Off : ControlState
    data object On : ControlState
    data object Busy : ControlState
    data class Unavailable(val reason: String) : ControlState
}

/** Quick-control tile with press scale, haptics and clear on/off/unavailable states. */
@Composable
fun ControlTile(
    icon: ImageVector,
    label: String,
    stateText: String,
    state: ControlState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "press")
    val on = state == ControlState.On
    val unavailable = state is ControlState.Unavailable
    val container by animateColorAsState(if (on) Lux.Champagne.copy(alpha = 0.14f) else Lux.Surface, label = "bg")
    val border by animateColorAsState(if (on) Lux.Champagne.copy(alpha = 0.7f) else Lux.Hairline, label = "border")
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .scale(scale)
            .clip(shape)
            .background(container)
            .border(1.dp, border, shape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .defaultMinSize(minHeight = 104.dp)
            .padding(16.dp)
            .semantics { stateDescription = stateText },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(if (on) Lux.Champagne else Lux.SurfaceHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = when {
                        on -> Lux.Background
                        unavailable -> Lux.TextFaint
                        else -> Lux.Text
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (state == ControlState.Busy) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), color = Lux.Champagne, strokeWidth = 1.5.dp)
            } else if (unavailable) {
                Icon(LuxIcons.Lock, contentDescription = null, tint = Lux.TextFaint, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(label, style = LuxType.Heading, color = if (unavailable) Lux.TextMuted else Lux.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(stateText, style = LuxType.BodySmall, color = if (on) Lux.ChampagneBright else Lux.TextFaint, maxLines = 1)
    }
}

/** Champagne pill button for the primary action on a screen. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val shape = CircleShape
    Row(
        modifier
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(if (enabled) Lux.AccentBrush else Brush.linearGradient(listOf(Lux.SurfaceHighest, Lux.SurfaceHighest)))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Lux.Background, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = LuxType.Heading, color = if (enabled) Lux.Background else Lux.TextFaint)
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .border(1.dp, Lux.Hairline, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = LuxType.Body, color = Lux.Text) }
}

/** Segmented choice with a sliding-feel highlight. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(Lux.Surface)
            .border(1.dp, Lux.Hairline, CircleShape)
            .padding(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            val bg by animateColorAsState(if (active) Lux.SurfaceHighest else Color.Transparent, label = "seg")
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .clickable(role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = LuxType.BodySmall, color = if (active) Lux.Text else Lux.TextMuted)
            }
        }
    }
}

@Composable
fun LuxSwitch(checked: Boolean, onChange: (Boolean) -> Unit, description: String) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        modifier = Modifier.semantics { contentDescription = description },
        colors = SwitchDefaults.colors(
            checkedThumbColor = Lux.Background,
            checkedTrackColor = Lux.Champagne,
            checkedBorderColor = Lux.Champagne,
            uncheckedThumbColor = Lux.TextMuted,
            uncheckedTrackColor = Lux.Surface,
            uncheckedBorderColor = Lux.Hairline,
        ),
    )
}

/** A tappable list row for settings and navigation. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {
        if (onClick != null) Icon(LuxIcons.Chevron, contentDescription = null, tint = Lux.TextFaint, modifier = Modifier.size(18.dp))
    },
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Lux.TextMuted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = LuxType.Body, color = Lux.Text)
            if (subtitle != null) Text(subtitle, style = LuxType.BodySmall, color = Lux.TextFaint)
        }
        trailing()
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Lux.Hairline))
}
