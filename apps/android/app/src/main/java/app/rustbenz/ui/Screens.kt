package app.rustbenz.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rustbenz.AppViewModel
import app.rustbenz.UiState
import app.rustbenz.core.ChargingStatus
import app.rustbenz.core.VehicleState
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun App(vm: AppViewModel, onSignIn: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    when (val s = ui) {
        UiState.Starting -> Centered { CircularProgressIndicator() }
        is UiState.SignedOut -> SignInScreen(s, onSignIn)
        is UiState.Dashboard -> DashboardScreen(s, onRefresh = vm::refresh, onSignOut = vm::signOut)
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun SignInScreen(state: UiState.SignedOut, onSignIn: () -> Unit) {
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("RustBenz", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Your Mercedes EV at a glance.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(40.dp))
            if (state.busy) {
                CircularProgressIndicator()
            } else {
                Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Sign in with Mercedes me")
                }
            }
            state.message?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(40.dp))
            Text(
                "Unofficial app. You sign in on Mercedes' own page; your password is never seen by this app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScreen(state: UiState.Dashboard, onRefresh: () -> Unit, onSignOut: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.vehicle?.model ?: "Your vehicle")
                        state.vehicle?.let {
                            Text(
                                it.displayId,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = { TextButton(onClick = onSignOut) { Text("Sign out") } },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading && state.state != null,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                state.message?.let { MessageCard(it, onRetry = if (state.vehicle != null) onRefresh else null) }
                val vehicleState = state.state
                when {
                    vehicleState != null -> {
                        val now = Instant.now()
                        BatteryCard(vehicleState, now)
                        DetailsCard(vehicleState, now)
                        Text(
                            "Fetched ${clock(vehicleState.retrievedAt)}. Values are your car's last report to Mercedes; ages show when the car measured them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.loading -> Column(
                        Modifier.fillMaxWidth().padding(top = 96.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Contacting your car…", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "This can take up to 30 seconds.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.vehicle != null -> Column(
                        Modifier.fillMaxWidth().padding(top = 96.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("No status loaded yet.", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onRefresh) { Text("Load status") }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryCard(s: VehicleState, now: Instant) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Text("Battery", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val soc = s.stateOfChargePercent
            Text(
                soc?.let { "${it.value.roundToInt()}%" } ?: "—",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics {
                    contentDescription = soc?.let { "Battery ${it.value.roundToInt()} percent" } ?: "Battery level not reported"
                },
            )
            if (soc != null) {
                LinearProgressIndicator(
                    progress = { (soc.value / 100.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    s.electricRangeKm?.let { "${it.value.roundToInt()} km" } ?: "Range not reported",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.size(8.dp))
                if (s.electricRangeKm != null) {
                    Text("range", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Measured ${age(soc?.observedAt, now)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailsCard(s: VehicleState, now: Instant) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            DetailRow("Charging", s.chargingStatus?.value?.let(::chargingLabel), s.chargingStatus?.observedAt, now)
            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            DetailRow("Plugged in", s.pluggedIn?.value?.let(::yesNo), s.pluggedIn?.observedAt, now)
            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            DetailRow("Charging power", s.chargingPowerKw?.value?.let { "%.1f kW".format(it) }, s.chargingPowerKw?.observedAt, now)
            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            DetailRow("Climate", s.preconditioningActive?.value?.let { if (it) "On" else "Off" }, s.preconditioningActive?.observedAt, now)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String?, observedAt: Instant?, now: Instant) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(value ?: "Not reported", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (value != null) {
                Text(age(observedAt, now), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MessageCard(message: String, onRetry: (() -> Unit)?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
            onRetry?.let { TextButton(onClick = it) { Text("Retry") } }
        }
    }
}

private fun chargingLabel(status: ChargingStatus): String = when (status) {
    ChargingStatus.CHARGING -> "Charging"
    ChargingStatus.NOT_CHARGING -> "Not charging"
    ChargingStatus.COMPLETE -> "Complete"
    ChargingStatus.FAULT -> "Fault"
    ChargingStatus.UNRECOGNIZED -> "Unknown state"
}

private fun yesNo(b: Boolean) = if (b) "Yes" else "No"

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

private fun clock(t: Instant): String = clockFormat.format(t.atZone(ZoneId.systemDefault()))

/** Vehicle-side age, so stale data is never presented as live. */
internal fun age(observedAt: Instant?, now: Instant): String {
    observedAt ?: return "time unknown"
    val secs = Duration.between(observedAt, now).seconds.coerceAtLeast(0)
    return when {
        secs < 60 -> "just now"
        secs < 3_600 -> "${secs / 60} min ago"
        secs < 86_400 -> "${secs / 3_600} h ago"
        else -> "${secs / 86_400} days ago"
    }
}
