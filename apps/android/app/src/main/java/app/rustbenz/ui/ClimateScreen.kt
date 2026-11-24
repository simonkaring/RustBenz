package app.rustbenz.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

private val seatLabels = listOf("Driver", "Passenger", "Rear left", "Rear right")

@Composable
fun ClimateScreen(state: ShellState, padding: PaddingValues, actions: ShellActions) {
    val car = state.car
    var target by rememberSaveable { mutableFloatStateOf(21.5f) }
    val seats = remember { mutableStateListOf(true, false, false, false) }
    val departures = remember { mutableStateListOf(*SampleData.departures.toTypedArray()) }
    val on = car.climateOn?.value == true

    TabColumn(padding) {
        item {
            ScreenHeader(
                "Climate",
                subtitle = listOfNotNull(
                    car.cabinC?.let { "Inside %.1f°".format(it.value) },
                    car.outsideC?.let { "Outside %.1f°".format(it.value) },
                ).joinToString("  ·  "),
            )
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Overline(if (on) "Preconditioning" else "Ready", Modifier.weight(1f), color = if (on) Lux.ChampagneBright else Lux.TextMuted)
                    SourceTag(car.climateOn?.source)
                }
                TemperatureDial(
                    value = target,
                    onChange = { target = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("%.1f°".format(target), style = LuxType.Hero.copy(fontSize = LuxType.Hero.fontSize * 0.8f), color = Lux.Text)
                        Text("Target", style = LuxType.BodySmall, color = Lux.TextMuted)
                    }
                    // ± sit in the open bottom of the arc.
                    Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RoundIconButton(LuxIcons.Minus, "Lower temperature") { target = (target - 0.5f).coerceAtLeast(16f) }
                        Spacer(Modifier.width(56.dp))
                        RoundIconButton(LuxIcons.Plus, "Raise temperature") { target = (target + 0.5f).coerceAtMost(28f) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = if (on) "Stop climate" else "Start climate",
                    icon = LuxIcons.Power,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        actions.command(
                            CommandRequest(
                                title = if (on) "Stop climate" else "Start climate",
                                details = if (on) {
                                    listOf("Stops preconditioning now")
                                } else {
                                    listOf("Target %.1f °C".format(target), seatsSummary(seats), "Runs on battery power")
                                },
                                icon = LuxIcons.Climate,
                                confirm = if (on) "Stop now" else "Start now",
                            ),
                        )
                    },
                )
            }
        }
        item { SectionHeader("Seats") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                seatLabels.indices.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { i ->
                            SeatToggle(seatLabels[i], seats[i], Modifier.weight(1f)) { seats[i] = !seats[i] }
                        }
                    }
                }
            }
        }
        item { SectionHeader("Departure times") }
        item {
            Panel(padding = 8.dp) {
                departures.forEachIndexed { i, d ->
                    if (i > 0) Hairline(Modifier.padding(horizontal = 12.dp))
                    ListRow(
                        title = d.time,
                        subtitle = "${d.label} · ${d.days}",
                        icon = LuxIcons.Clock,
                        trailing = { LuxSwitch(d.enabled, { departures[i] = d.copy(enabled = it) }, "${d.label} at ${d.time}") },
                    )
                }
                Hairline(Modifier.padding(horizontal = 12.dp))
                ListRow(title = "Add departure time", icon = LuxIcons.Plus, onClick = {
                    actions.explainUnavailable("Scheduling departures will arrive together with remote climate commands.")
                })
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SampleChip()
                Spacer(Modifier.width(8.dp))
                Text(
                    "Seats and schedules are a design preview. The car is pre-warmed using battery energy; staying plugged in preserves range.",
                    style = LuxType.BodySmall,
                    color = Lux.TextFaint,
                )
            }
        }
    }
}

private fun seatsSummary(seats: List<Boolean>): String {
    val selected = seatLabels.filterIndexed { i, _ -> seats[i] }
    return if (selected.isEmpty()) "No seat heating" else "Seat heating: ${selected.joinToString(", ")}"
}

@Composable
private fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .border(1.dp, Lux.Hairline, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = Lux.Text) }
}

@Composable
private fun SeatToggle(label: String, on: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val border by animateColorAsState(if (on) Lux.Champagne.copy(alpha = 0.7f) else Lux.Hairline, label = "seat")
    Row(
        modifier
            .clip(shape)
            .background(if (on) Lux.Champagne.copy(alpha = 0.12f) else Lux.Surface)
            .border(1.dp, border, shape)
            .clickable(role = Role.Switch, onClick = onToggle)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp)
            .semantics { stateDescription = if (on) "Heating on" else "Heating off" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(LuxIcons.Seat, contentDescription = null, tint = if (on) Lux.ChampagneBright else Lux.TextMuted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = LuxType.Body, color = Lux.Text)
            Text(if (on) "Heating" else "Off", style = LuxType.BodySmall, color = if (on) Lux.ChampagneBright else Lux.TextFaint)
        }
    }
}
