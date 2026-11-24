package app.rustbenz.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.Instant

@Composable
fun HomeScreen(state: ShellState, padding: PaddingValues, actions: ShellActions) {
    val car = state.car
    val now = Instant.now()
    TabColumn(padding) {
        item {
            ScreenHeader(
                title = car.name,
                subtitle = listOfNotNull(car.displayId, car.retrievedAt?.let { "Updated ${clock(it)}" }).joinToString("  ·  "),
                preview = state.preview,
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = actions.refresh)
                        .semantics { contentDescription = "Refresh" },
                    contentAlignment = Alignment.Center,
                ) { Icon(LuxIcons.Refresh, contentDescription = null, tint = Lux.TextMuted, modifier = Modifier.size(22.dp)) }
            }
        }
        if (state.loading) {
            item {
                Column {
                    Text("Contacting your car… this can take up to 30 seconds.", style = LuxType.BodySmall, color = Lux.TextMuted)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Lux.Champagne, trackColor = Lux.Hairline)
                }
            }
        }
        state.message?.let { msg -> item { MessagePanel(msg, onRetry = actions.refresh) } }
        item { Hero(car, now, onOpenCharging = { actions.open(Tab.Charging) }) }
        item { StatusRow(car) }
        item { SectionHeader("Controls") }
        item { ControlsGrid(car, actions) }
        item { SectionHeader("This week", action = "Trips", onAction = { actions.open(Tab.Trips) }) }
        item { WeekInsights() }
        item { SectionHeader("Recent", action = "History", onAction = { actions.open(Tab.Charging) }) }
        item { RecentActivity(now) }
    }
}

@Composable
private fun Hero(car: CarUi, now: Instant, onOpenCharging: () -> Unit) {
    val charging = car.charging?.value == ChargeState.Charging
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BatteryRing(
            progress = car.soc?.value?.div(100f),
            limit = car.chargeLimit?.value?.div(100f),
            charging = charging,
            modifier = Modifier.clickable(role = Role.Button, onClick = onOpenCharging),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Overline("Battery")
                Row(verticalAlignment = Alignment.Bottom) {
                    val soc = car.soc?.value
                    Text(soc?.toString() ?: "—", style = LuxType.Hero, color = if (soc == null) Lux.TextFaint else Lux.Text)
                    if (soc != null) Text("%", style = LuxType.Metric, color = Lux.TextMuted, modifier = Modifier.padding(bottom = 12.dp))
                }
                Text(
                    car.rangeKm?.let { "${it.value} km range" } ?: "Range not reported",
                    style = LuxType.Body,
                    color = Lux.TextMuted,
                )
                Spacer(Modifier.height(6.dp))
                SourceTag(car.soc?.source, now)
            }
        }
        CarIllustration(
            Modifier.fillMaxWidth().offset(y = (-18).dp),
            plugged = car.pluggedIn?.value == true,
            charging = charging,
        )
        Text(
            chargeSummary(car),
            style = LuxType.BodySmall,
            color = if (charging) Lux.ChampagneBright else Lux.TextMuted,
            modifier = Modifier.offset(y = (-10).dp),
        )
    }
}

private fun chargeSummary(car: CarUi): String {
    val state = when (car.charging?.value) {
        ChargeState.Charging -> car.powerKw?.let { "Charging at %.1f kW".format(it.value) } ?: "Charging"
        ChargeState.Complete -> "Charging complete"
        ChargeState.NotCharging -> "Not charging"
        ChargeState.Fault -> "Charging fault"
        ChargeState.Unknown, null -> "Charging status not reported"
    }
    return listOfNotNull(state, car.chargeLimit?.let { "Limit ${it.value} %" }).joinToString("  ·  ")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusRow(car: CarUi) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        car.locked?.let {
            StatusPill(if (it.value) LuxIcons.Lock else LuxIcons.Unlock, if (it.value) "Locked" else "Unlocked", if (it.value) Tone.Good else Tone.Attention)
        }
        car.pluggedIn?.let {
            StatusPill(LuxIcons.Plug, if (it.value) "Plugged in" else "Unplugged", if (it.value) Tone.Accent else Tone.Neutral)
        } ?: StatusPill(LuxIcons.Plug, "Plug not reported", Tone.Neutral)
        car.climateOn?.let {
            StatusPill(LuxIcons.Climate, if (it.value) "Climate on" else "Climate off", if (it.value) Tone.Accent else Tone.Neutral)
        }
        car.openings?.let {
            StatusPill(LuxIcons.Window, if (it.value.allClosed) "All closed" else "Something is open", if (it.value.allClosed) Tone.Good else Tone.Alert)
        }
    }
}

@Composable
private fun ControlsGrid(car: CarUi, actions: ShellActions) {
    val climateOn = car.climateOn?.value == true
    val tiles = listOf(
        Triple(Control.Climate, LuxIcons.Climate, if (climateOn) "On · 21.5°" else "Off"),
        Triple(Control.Charge, LuxIcons.Charging, car.chargeLimit?.let { "Limit ${it.value} %" } ?: "Settings"),
        Triple(Control.Lock, if (car.locked?.value == false) LuxIcons.Unlock else LuxIcons.Lock, if (car.locked?.value == false) "Unlocked" else "Locked"),
        Triple(Control.Locate, LuxIcons.Locate, "Find car"),
        Triple(Control.Windows, LuxIcons.Window, "Ventilate"),
        Triple(Control.Signal, LuxIcons.Signal, "Flash & honk"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (control, icon, text) ->
                    val availability = SampleData.availability[control] ?: Availability.Available
                    val tileState = when {
                        availability is Availability.Unavailable -> ControlState.Unavailable(availability.reason)
                        control == Control.Climate && climateOn -> ControlState.On
                        else -> ControlState.Off
                    }
                    ControlTile(
                        icon = icon,
                        label = control.label,
                        stateText = if (availability is Availability.Unavailable) "Not available" else text,
                        state = tileState,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when {
                                availability is Availability.Unavailable -> actions.explainUnavailable(availability.reason)
                                control == Control.Climate -> actions.command(
                                    CommandRequest(
                                        title = if (climateOn) "Stop climate" else "Start climate",
                                        details = if (climateOn) listOf("Stops preconditioning now") else listOf("Target 21.5 °C", "Front seats heated", "Runs on battery power"),
                                        icon = LuxIcons.Climate,
                                        confirm = if (climateOn) "Stop now" else "Start now",
                                    ),
                                )
                                control == Control.Charge -> actions.open(Tab.Charging)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekInsights() {
    Panel {
        Row(verticalAlignment = Alignment.Top) {
            Metric("Driven", "395", "km", null, Modifier.weight(1f))
            Metric("Avg kWh/100 km", "18.3", null, null, Modifier.weight(1f))
            SampleChip()
        }
        Spacer(Modifier.height(18.dp))
        AreaChart(SampleData.weekConsumption, Modifier.fillMaxWidth().height(56.dp), description = "Consumption trend this week")
    }
}

@Composable
private fun RecentActivity(now: Instant) {
    val charge = SampleData.chargeSessions.first()
    val trip = SampleData.trips.first()
    Panel(padding = 8.dp) {
        ListRow(
            title = "Charged ${"%.1f".format(charge.energyKwh)} kWh at ${charge.place}",
            subtitle = "${dayLabel(charge.start, now)} · ${charge.socFrom} → ${charge.socTo} %",
            icon = LuxIcons.Charging,
            trailing = { SampleChip() },
        )
        Hairline(Modifier.padding(horizontal = 12.dp))
        ListRow(
            title = "${trip.from} → ${trip.to}",
            subtitle = "${dayLabel(trip.start, now)} · ${km(trip.distanceKm)} km · ${"%.1f".format(trip.kwhPer100)} kWh/100 km",
            icon = LuxIcons.Trips,
            trailing = { SampleChip() },
        )
    }
}

@Composable
fun MessagePanel(message: String, onRetry: (() -> Unit)?) {
    Panel(padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(LuxIcons.Info, contentDescription = null, tint = Lux.Caution, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(message, style = LuxType.BodySmall, color = Lux.Text, modifier = Modifier.weight(1f))
            if (onRetry != null) {
                Text(
                    "Retry",
                    style = LuxType.BodySmall,
                    color = Lux.Champagne,
                    modifier = Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onRetry).padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
    }
}
