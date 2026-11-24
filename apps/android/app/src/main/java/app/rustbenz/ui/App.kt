package app.rustbenz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rustbenz.AppViewModel
import app.rustbenz.UiState

@Composable
fun App(vm: AppViewModel, onSignIn: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(Lux.Background)) {
        when (val s = ui) {
            UiState.Starting -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Lux.Champagne, strokeWidth = 1.5.dp)
            }
            is UiState.SignedOut -> SignInScreen(s, onSignIn, onExplore = vm::explore)
            is UiState.Dashboard -> AppShell(
                ShellState(
                    car = carUi(s.vehicle?.model ?: "Your vehicle", s.vehicle?.displayId ?: "", s.state, preview = false),
                    preview = false,
                    loading = s.loading,
                    message = s.message,
                ),
                onRefresh = vm::refresh,
                onSignOut = vm::signOut,
            )
            UiState.Preview -> AppShell(
                ShellState(carUi("EQA 250", "Design preview", null, preview = true), preview = true, loading = false, message = null),
                onRefresh = {},
                onSignOut = vm::signOut,
            )
        }
    }
}

@Composable
private fun SignInScreen(state: UiState.SignedOut, onSignIn: () -> Unit, onExplore: () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text("RUSTBENZ", style = LuxType.Overline.copy(fontSize = LuxType.Overline.fontSize * 1.4f, letterSpacing = 0.5.em), color = Lux.Champagne)
        Spacer(Modifier.height(14.dp))
        Text("Your car,\nbeautifully informed.", style = LuxType.Display, color = Lux.Text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        CarIllustration(Modifier.fillMaxWidth().padding(vertical = 12.dp), plugged = true)
        Spacer(Modifier.weight(1f))
        if (state.busy) {
            CircularProgressIndicator(color = Lux.Champagne, strokeWidth = 1.5.dp)
        } else {
            PrimaryButton("Sign in with Mercedes me", onClick = onSignIn, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            GhostButton("Explore with sample data", onClick = onExplore, modifier = Modifier.fillMaxWidth())
        }
        state.message?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, style = LuxType.BodySmall, color = Lux.Critical, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "You sign in on Mercedes' own page. Your password never reaches this app.",
                style = LuxType.BodySmall,
                color = Lux.TextFaint,
                textAlign = TextAlign.Center,
            )
            Text("Unofficial · not affiliated with Mercedes-Benz", style = LuxType.BodySmall, color = Lux.TextFaint)
        }
    }
}
