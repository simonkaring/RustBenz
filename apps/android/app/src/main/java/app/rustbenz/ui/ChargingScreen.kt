package app.rustbenz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.Instant
import kotlin.math.roundToInt

@Composable
fun ChargingScreen(state: ShellState, padding: PaddingValues, actions: ShellActions) {
    val car = state.car
    val now = Instant.now()
    var limit by rememberSaveable { mutableFloatStateOf((car.chargeLimit?.value ?: 80).toFloat()) }
    var program by rememberSaveable { mutableIntStateOf(0) }
    var offPeak by rememberSaveable { mutableStateOf(true) }

    TabColumn(padding) {
        item { ScreenHeader("Charging", subtitle = "EQA 250 · 66.5 kWh usable") }
        item { NowPanel(car, now) }
        item { SectionHeader("Charge limit") }
        item {
            Panel {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${limit.roundToInt()}", style = LuxType.Display, color = Lux.Text)
                    Text(" %", style = LuxType.Metric, color = Lux.TextMuted, modifier = Modifier.padding(bottom = 6.dp))
                    Spacer(Modifier.weight(1f))
                    SourceTag(car.chargeLimit?.source)
                }
                if (car.chargeLimit == null) {
                    Text("Your car's current limit is not reported yet.", style = LuxType.BodySmall, color = Lux.TextFaint)
                }
                Text(limitHint(limit.roundToInt()), style = LuxType.BodySmall, color = Lux.TextMuted)
                Slider(
                    value = limit,
                    onValueChange = { limit = it },
                    valueRange = 50f..100f,
                    steps = 9,
                    modifier = Modifier.semantics { contentDescription = "Charge limit" },
                    colors = SliderDefaults.colors(
                        thumbColor = Lux.ChampagneBright,
                        activeTrackColor = Lux.Champagne,
                        inactiveTrackColor = Lux.SurfaceHighest,
                        activeTickColor = Lux.ChampagneDeep,
                        inactiveTickColor = Lux.Hairline,
                    ),
                )
                Spacer(Modifier.height(16.dp))
                Overline("Charge program")
                Spacer(Modifier.height(10.dp))
                Segmented(listOf("Standard", "Home", "Work"), program, { program = it })
                Spacer(Modifier.height(20.dp))
                PrimaryButton(
                    "Apply",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        actions.command(
                            CommandRequest(
                                title = "Update charging",
                                details = listOf("Charge limit ${limit.roundToInt()} %", "Program: ${listOf("Standard", "Home", "Work")[program]}"),
                                icon = LuxIcons.Charging,
                                confirm = "Apply to car",
                            ),
                        )
                    },
                )
            }
        }
        item { SectionHeader("Schedule") }
        item {
            Panel(padding = 8.dp) {
                ListRow(
                    title = "Off-peak charging",
                    subtitle = "Only charge 00:00 – 06:00 at home",
                    icon = LuxIcons.Clock,
                    trailing = { LuxSwitch(offPeak, { offPeak = it }, "Off-peak charging") },
                )
                Hairline(Modifier.padding(horizontal = 12.dp))
                ListRow(title = "Ready by", subtitle = "07:15 on weekdays · follows departure times", icon = LuxIcons.Leaf, onClick = { actions.open(Tab.Climate) })
            }
        }
        item { SectionHeader("Live session") }
        item {
            LiveSessionPanel(onStop = {
                actions.command(CommandRequest("Stop charging", listOf("Ends the current charging session"), LuxIcons.Charging, "Stop charging"))
            })
        }
        item { SectionHeader("History") }
        item { MonthSummary() }
        items(SampleData.chargeSessions.size) { i -> SessionRow(SampleData.chargeSessions[i], now) }
        item {
            Text(
                "History is built from snapshots RustBenz records on this device — it never leaves your phone.",
                style = LuxType.BodySmall,
                color = Lux.TextFaint,
            )
        }
    }
}

private fun limitHint(limit: Int) = when {
    limit <= 80 -> "Recommended for daily driving and long battery life."
    limit < 100 -> "Good for longer days."
    else -> "Best reserved for road trips; charge shortly before leaving."
}

@Composable
private fun NowPanel(car: CarUi, now: Instant) {
    val state = car.charging?.value
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Lux.SurfaceHighest), contentAlignment = Alignment.Center) {
                Icon(
                    if (state == ChargeState.Charging) LuxIcons.Charging else LuxIcons.Plug,
                    contentDescription = null,
                    tint = if (state == ChargeState.Charging) Lux.ChampagneBright else Lux.TextMuted,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (state) {
                        ChargeState.Charging -> "Charging"
                        ChargeState.Complete -> "Charging complete"
                        ChargeState.NotCharging -> "Not charging"
                        ChargeState.Fault -> "Charging fault"
                        ChargeState.Unknown, null -> "Status not reported"
                    },
                    style = LuxType.Title,
                    color = Lux.Text,
                )
                Text(
                    when (car.pluggedIn?.value) {
                        true -> "Cable connected"
                        false -> "Plug in to start charging"
                        null -> "Plug state not reported"
                    },
                    style = LuxType.BodySmall,
                    color = Lux.TextMuted,
                )
            }
            SourceTag(car.charging?.source, now)
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric("Battery", car.soc?.value?.toString(), "%", car.soc?.source, Modifier.weight(1f))
            Metric("Range", car.rangeKm?.value?.toString(), "km", car.rangeKm?.source, Modifier.weight(1f))
            Metric("Power", car.powerKw?.let { "%.1f".format(it.value) }, "kW", car.powerKw?.source, Modifier.weight(1f))
        }
    }
}

@Composable
private fun LiveSessionPanel(onStop: () -> Unit) {
    val curve = SampleData.livePowerCurve
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Overline("DC fast charging")
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("%.0f".format(curve.last()), style = LuxType.Display, color = Lux.Text)
                    Text(" kW", style = LuxType.Metric, color = Lux.TextMuted, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            SampleChip()
        }
        Spacer(Modifier.height(12.dp))
        AreaChart(curve.map { it }, Modifier.fillMaxWidth().height(110.dp), description = "Charging power over the session")
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric("Added", "+31.4", "kWh", null, Modifier.weight(1f))
            Metric("Now", "62", "%", null, Modifier.weight(1f))
            Metric("80 % in", "9", "min", null, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        GhostButton("Stop charging", onClick = onStop, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun MonthSummary() {
    val sessions = SampleData.chargeSessions
    val energy = sessions.sumOf { it.energyKwh }
    val cost = sessions.sumOf { it.costDkk }
    val dc = sessions.filter { it.type == ChargeType.DC }.sumOf { it.energyKwh }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline("Last 14 days", Modifier.weight(1f))
            SampleChip()
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric("Energy", "%.0f".format(energy), "kWh", null, Modifier.weight(1f))
            Metric("Cost", "%.0f".format(cost), "DKK", null, Modifier.weight(1f))
            Metric("Per kWh", "%.2f".format(cost / energy), "DKK", null, Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))
        val acShare = ((energy - dc) / energy).toFloat()
        Row(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)) {
            Box(Modifier.weight(acShare).fillMaxWidth().height(8.dp).background(Lux.Champagne))
            Box(Modifier.weight(1 - acShare).fillMaxWidth().height(8.dp).background(Lux.Cool))
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Text("AC %.0f %%".format(acShare * 100), style = LuxType.BodySmall, color = Lux.Champagne, modifier = Modifier.weight(1f))
            Text("DC %.0f %%".format((1 - acShare) * 100), style = LuxType.BodySmall, color = Lux.Cool)
        }
    }
}

@Composable
private fun SessionRow(s: ChargeSession, now: Instant) {
    Panel(padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                s.type.name,
                style = LuxType.Overline,
                color = if (s.type == ChargeType.DC) Lux.Cool else Lux.Champagne,
                modifier = Modifier
                    .border(1.dp, (if (s.type == ChargeType.DC) Lux.Cool else Lux.Champagne).copy(alpha = 0.5f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.place, style = LuxType.Heading, color = Lux.Text)
                Text("${dayLabel(s.start, now)} · ${clock(s.start)} · ${duration(s.duration)}", style = LuxType.BodySmall, color = Lux.TextFaint)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("+%.1f kWh".format(s.energyKwh), style = LuxType.MetricSmall, color = Lux.Text)
                Text("${s.socFrom} → ${s.socTo} %", style = LuxType.BodySmall, color = Lux.TextMuted)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Text("Peak %.0f kW".format(s.peakKw), style = LuxType.BodySmall, color = Lux.TextFaint, modifier = Modifier.weight(1f))
            Text(if (s.costDkk == 0.0) "Free" else "%.2f DKK".format(s.costDkk), style = LuxType.BodySmall, color = Lux.TextMuted)
        }
    }
}
