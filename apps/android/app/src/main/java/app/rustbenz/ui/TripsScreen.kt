package app.rustbenz.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.Instant

@Composable
fun TripsScreen(padding: PaddingValues) {
    val now = Instant.now()
    val trips = SampleData.trips
    val total = SampleData.weekDistanceKm.sumOf { it.second.toDouble() }
    val driving = trips.fold(Duration.ZERO) { acc, t -> acc + t.duration }
    val avg = trips.sumOf { it.kwhPer100 * it.distanceKm } / trips.sumOf { it.distanceKm }

    TabColumn(padding) {
        item { ScreenHeader("Trips", subtitle = "Last 7 days", preview = true) }
        item {
            Panel {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric("Distance", "%.0f".format(total), "km", null, Modifier.weight(1f))
                    Metric("Driving", "${driving.toHours()}h ${driving.toMinutesPart()}", null, null, Modifier.weight(1f))
                    Metric("kWh/100 km", "%.1f".format(avg), null, null, Modifier.weight(1f))
                }
                Spacer(Modifier.height(22.dp))
                BarChart(SampleData.weekDistanceKm, Modifier.fillMaxWidth(), description = "Distance per day this week")
            }
        }
        item { SectionHeader("Efficiency") }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Overline("Consumption trend")
                        Text("Better than last week by 4 %", style = LuxType.Body, color = Lux.Positive)
                    }
                    SampleChip()
                }
                Spacer(Modifier.height(16.dp))
                AreaChart(SampleData.weekConsumption, Modifier.fillMaxWidth().height(90.dp), color = Lux.Positive, description = "Consumption per day this week")
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric("Best day", "17.2", null, null, Modifier.weight(1f))
                    Metric("Recovered", "11", "%", null, Modifier.weight(1f))
                    Metric("Eco score", "82", null, null, Modifier.weight(1f))
                }
            }
        }
        item { SectionHeader("Trip log") }
        items(trips.size) { i -> TripRow(trips[i], avg, now) }
        item {
            Text(
                "Trips are reconstructed from status snapshots stored on this device. With an OBD2 adapter, RustBenz will log full trip telemetry.",
                style = LuxType.BodySmall,
                color = Lux.TextFaint,
            )
        }
    }
}

@Composable
private fun TripRow(t: Trip, average: Double, now: Instant) {
    val tone = if (t.kwhPer100 <= average) Lux.Positive else Lux.Caution
    Panel(padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Lux.TextMuted))
                Box(Modifier.width(1.dp).height(18.dp).background(Lux.Hairline))
                Box(Modifier.size(8.dp).clip(CircleShape).background(Lux.Champagne))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t.from, style = LuxType.Body, color = Lux.TextMuted)
                Text(t.to, style = LuxType.Heading, color = Lux.Text)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${km(t.distanceKm)} km", style = LuxType.MetricSmall, color = Lux.Text)
                Text("${dayLabel(t.start, now)} · ${clock(t.start)}", style = LuxType.BodySmall, color = Lux.TextFaint)
            }
        }
        Spacer(Modifier.height(12.dp))
        Hairline()
        Spacer(Modifier.height(12.dp))
        Row {
            Text(duration(t.duration), style = LuxType.BodySmall, color = Lux.TextMuted, modifier = Modifier.weight(1f))
            Text("−${t.socUsed} % battery", style = LuxType.BodySmall, color = Lux.TextMuted, modifier = Modifier.weight(1f))
            Text("%.1f kWh/100".format(t.kwhPer100), style = LuxType.BodySmall, color = tone)
        }
    }
}
