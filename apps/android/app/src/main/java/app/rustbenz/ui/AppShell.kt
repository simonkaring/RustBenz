package app.rustbenz.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", LuxIcons.Home),
    Climate("Climate", LuxIcons.Climate),
    Charging("Charging", LuxIcons.Charging),
    Trips("Trips", LuxIcons.Trips),
    Vehicle("Vehicle", LuxIcons.Vehicle),
}

/** A remote command awaiting confirmation. Commands are not wired to the car yet: the flow is simulated. */
data class CommandRequest(val title: String, val details: List<String>, val icon: ImageVector, val confirm: String)

/** Everything the shell needs from the outside. */
data class ShellState(
    val car: CarUi,
    val preview: Boolean,
    val loading: Boolean,
    val message: String?,
)

@Composable
fun AppShell(state: ShellState, onRefresh: () -> Unit, onSignOut: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var command by remember { mutableStateOf<CommandRequest?>(null) }
    var unavailable by remember { mutableStateOf<String?>(null) }

    val actions = ShellActions(
        command = { command = it },
        explainUnavailable = { unavailable = it },
        open = { tab = it },
        refresh = onRefresh,
        signOut = onSignOut,
    )

    Scaffold(
        containerColor = Lux.Background,
        bottomBar = { LuxNavigationBar(tab) { tab = it } },
    ) { padding ->
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "tab",
        ) { current ->
            val content = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 24.dp)
            when (current) {
                Tab.Home -> HomeScreen(state, content, actions)
                Tab.Climate -> ClimateScreen(state, content, actions)
                Tab.Charging -> ChargingScreen(state, content, actions)
                Tab.Trips -> TripsScreen(content)
                Tab.Vehicle -> VehicleScreen(state, content, actions)
            }
        }
        // Scrim so scrolled content fades out under the transparent status bar.
        Box(
            Modifier
                .fillMaxWidth()
                .height(padding.calculateTopPadding() + 12.dp)
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Lux.Background, Lux.Background, Lux.Background.copy(alpha = 0f)))),
        )
    }

    command?.let { CommandSheet(it, onDismiss = { command = null }) }
    unavailable?.let { InfoSheet("Not available", it, onDismiss = { unavailable = null }) }
}

class ShellActions(
    val command: (CommandRequest) -> Unit,
    val explainUnavailable: (String) -> Unit,
    val open: (Tab) -> Unit,
    val refresh: () -> Unit,
    val signOut: () -> Unit,
)

@Composable
private fun LuxNavigationBar(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = Lux.Background, tonalElevation = 0.dp) {
        Tab.entries.forEach { t ->
            NavigationBarItem(
                selected = t == selected,
                onClick = { onSelect(t) },
                icon = { Icon(t.icon, contentDescription = null, modifier = Modifier.size(22.dp)) },
                label = { Text(t.label, style = LuxType.BodySmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Lux.ChampagneBright,
                    selectedTextColor = Lux.ChampagneBright,
                    indicatorColor = Lux.SurfaceHighest,
                    unselectedIconColor = Lux.TextFaint,
                    unselectedTextColor = Lux.TextFaint,
                ),
            )
        }
    }
}

private enum class Step(val label: String) { Sending("Sending to Mercedes"), Accepted("Accepted by Mercedes"), Confirmed("Confirmed by your car") }

/** Confirm → sent → accepted → confirmed. Simulated until commands are implemented in the Rust core. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommandSheet(request: CommandRequest, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var running by remember { mutableStateOf(false) }
    var reached by remember { mutableIntStateOf(-1) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        Step.entries.forEachIndexed { i, _ ->
            delay(if (i == 0) 700 else 1400)
            reached = i
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Lux.Surface, dragHandle = null) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Lux.SurfaceHighest), contentAlignment = Alignment.Center) {
                    Icon(request.icon, contentDescription = null, tint = Lux.Champagne)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(request.title, style = LuxType.Title, color = Lux.Text)
                    Overline("Simulation · not sent to your car", color = Lux.ChampagneDeep)
                }
            }
            Spacer(Modifier.height(20.dp))
            request.details.forEach { Text("·  $it", style = LuxType.Body, color = Lux.TextMuted, modifier = Modifier.padding(vertical = 2.dp)) }
            Spacer(Modifier.height(24.dp))
            if (!running) {
                PrimaryButton(request.confirm, onClick = { running = true }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                GhostButton("Cancel", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            } else {
                Step.entries.forEachIndexed { i, step ->
                    val done = i <= reached
                    val active = i == reached + 1
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                            when {
                                done -> Icon(LuxIcons.Check, contentDescription = null, tint = Lux.Positive, modifier = Modifier.size(20.dp))
                                active -> androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), color = Lux.Champagne, strokeWidth = 1.5.dp)
                                else -> Box(Modifier.size(8.dp).clip(CircleShape).background(Lux.Hairline))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(step.label, style = LuxType.Body, color = if (done || active) Lux.Text else Lux.TextFaint)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Remote commands aren't connected yet. This shows how RustBenz will report progress: a command only counts as done once the car confirms it.",
                    style = LuxType.BodySmall,
                    color = Lux.TextFaint,
                )
                Spacer(Modifier.height(16.dp))
                GhostButton(if (reached >= Step.entries.lastIndex) "Done" else "Close", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoSheet(title: String, body: String, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Lux.Surface, dragHandle = null) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(LuxIcons.Info, contentDescription = null, tint = Lux.Champagne)
                Spacer(Modifier.width(12.dp))
                Text(title, style = LuxType.Title, color = Lux.Text)
            }
            Text(body, style = LuxType.Body, color = Lux.TextMuted)
            Spacer(Modifier.height(8.dp))
            GhostButton("Got it", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Screen title block used at the top of every tab. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, preview: Boolean = false, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = LuxType.Display.copy(fontSize = LuxType.Display.fontSize * 0.7f), color = Lux.Text)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (subtitle != null) Text(subtitle, style = LuxType.BodySmall, color = Lux.TextMuted)
                if (preview) {
                    Spacer(Modifier.width(8.dp))
                    SampleChip()
                }
            }
        }
        trailing()
    }
}

/** Shared lazy-column layout for tabs. */
@Composable
fun TabColumn(padding: PaddingValues, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}
