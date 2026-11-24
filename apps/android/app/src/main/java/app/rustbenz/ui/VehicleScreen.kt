package app.rustbenz.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant

@Composable
fun VehicleScreen(state: ShellState, padding: PaddingValues, actions: ShellActions) {
    val car = state.car
    val now = Instant.now()
    TabColumn(padding) {
        item {
            ScreenHeader(
                car.name,
                subtitle = listOfNotNull(car.displayId, car.odometerKm?.let { "${thousands(it.value)} km" }).joinToString("  ·  "),
            )
        }
        item { SectionHeader("Live from your car") }
        item {
            Panel(padding = 8.dp) {
                SignalLine("Battery", car.soc?.let { "${it.value} %" }, car.soc?.source, now)
                SignalLine("Electric range", car.rangeKm?.let { "${it.value} km" }, car.rangeKm?.source, now)
                SignalLine("Charging", car.charging?.value?.name?.let(::humanize), car.charging?.source, now)
                SignalLine("Plugged in", car.pluggedIn?.let { if (it.value) "Yes" else "No" }, car.pluggedIn?.source, now)
                SignalLine("Charging power", car.powerKw?.let { "%.1f kW".format(it.value) }, car.powerKw?.source, now)
                SignalLine("Climate", car.climateOn?.let { if (it.value) "On" else "Off" }, car.climateOn?.source, now, last = true)
            }
        }
        item { SectionHeader("Openings") }
        item {
            val o = car.openings?.value
            Panel(padding = 8.dp) {
                SignalLine("Locked", car.locked?.let { if (it.value) "Yes" else "No" }, car.locked?.source, now)
                SignalLine("Doors", o?.let { if (it.doorsClosed) "Closed" else "Open" }, car.openings?.source, now)
                SignalLine("Windows", o?.let { if (it.windowsClosed) "Closed" else "Open" }, car.openings?.source, now)
                SignalLine("Tailgate", o?.let { if (it.trunkClosed) "Closed" else "Open" }, car.openings?.source, now)
                SignalLine("Sunroof", o?.let { if (it.sunroofClosed) "Closed" else "Open" }, car.openings?.source, now, last = true)
            }
        }
        if (car.locked == null && car.openings == null) {
            item {
                Text(
                    "Locks, doors and windows arrive through Mercedes' live push channel. RustBenz will show them once continuous updates are added.",
                    style = LuxType.BodySmall,
                    color = Lux.TextFaint,
                )
            }
        }
        item { SectionHeader("Tyres") }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Overline("Pressure", Modifier.weight(1f))
                    SampleChip()
                }
                TireDiagram(SampleData.tiresBar)
            }
        }
        SampleData.telemetry.forEach { group ->
            item { SectionHeader(group.title) }
            item {
                Panel(padding = 8.dp) {
                    group.rows.forEachIndexed { i, row -> SignalLine(row.label, row.value, row.source, now, last = i == group.rows.lastIndex) }
                }
            }
        }
        item { SectionHeader("Settings") }
        item {
            Panel(padding = 8.dp) {
                ListRow("Units", subtitle = "Kilometres · °C · bar · DKK", icon = LuxIcons.Sliders, onClick = { actions.explainUnavailable("Unit settings are part of the next design iteration.") })
                Hairline(Modifier.padding(horizontal = 12.dp))
                ListRow("Notifications", subtitle = "Charging done, plug-in reminder, climate ready", icon = LuxIcons.Signal, onClick = { actions.explainUnavailable("Notifications need background updates, which are not built yet.") })
                Hairline(Modifier.padding(horizontal = 12.dp))
                ListRow("Data & privacy", subtitle = "History stays on this device", icon = LuxIcons.Lock, onClick = {
                    actions.explainUnavailable("RustBenz has no servers. Your tokens are stored in the Android Keystore, and history is kept on this phone only.")
                })
                Hairline(Modifier.padding(horizontal = 12.dp))
                ListRow("About", subtitle = "Unofficial app · not affiliated with Mercedes-Benz", icon = LuxIcons.Info)
            }
        }
        item {
            GhostButton(if (state.preview) "Leave preview" else "Sign out", onClick = actions.signOut, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun humanize(name: String) = name.replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase().replaceFirstChar { it.uppercase() }

@Composable
private fun SignalLine(label: String, value: String?, source: Source?, now: Instant, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = LuxType.Body, color = Lux.TextMuted, modifier = Modifier.weight(1f))
        Text(value ?: "Not reported", style = LuxType.Body, color = if (value == null) Lux.TextFaint else Lux.Text)
        if (value != null && source != null) {
            Spacer(Modifier.width(10.dp))
            SourceTag(source, now)
        }
    }
    if (!last) Hairline(Modifier.padding(horizontal = 12.dp))
}
