package app.rustbenz

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.rustbenz.core.CoreException
import app.rustbenz.core.PendingLogin
import app.rustbenz.core.Session
import app.rustbenz.core.Vehicle
import app.rustbenz.core.VehicleState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UiState {
    data object Starting : UiState

    data class SignedOut(val message: String? = null, val busy: Boolean = false) : UiState

    data class Dashboard(
        val vehicle: Vehicle?,
        val state: VehicleState? = null,
        val loading: Boolean = false,
        val message: String? = null,
    ) : UiState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = TokenStore(app)
    private var session: Session? = null
    private var pendingLogin: PendingLogin? = null
    private var loadJob: Job? = null

    private val _ui = MutableStateFlow<UiState>(UiState.Starting)
    val ui: StateFlow<UiState> = _ui

    init {
        val tokens = store.load()
        if (tokens == null) _ui.value = UiState.SignedOut() else openSession(tokens)
    }

    /** Starts a login and returns the Mercedes URL to open in the browser. */
    fun beginLogin(): String? = try {
        pendingLogin?.close()
        PendingLogin().also { pendingLogin = it }.authorizationUrl()
    } catch (e: CoreException) {
        _ui.value = UiState.SignedOut(describe(e))
        null
    }

    /** Called with the `rismycar://login-callback?...` redirect. */
    fun completeLogin(callbackUrl: String) {
        val login = pendingLogin ?: run {
            _ui.value = UiState.SignedOut("The login was interrupted. Please sign in again.")
            return
        }
        pendingLogin = null
        _ui.value = UiState.SignedOut(busy = true)
        viewModelScope.launch {
            try {
                val tokens = login.complete(callbackUrl)
                store.save(tokens)
                openSession(tokens)
            } catch (e: CoreException) {
                _ui.value = UiState.SignedOut(describe(e))
            } finally {
                login.close()
            }
        }
    }

    fun refresh() {
        val current = _ui.value as? UiState.Dashboard ?: return
        val vehicle = current.vehicle ?: return
        if (loadJob?.isActive == true) return
        _ui.value = current.copy(loading = true, message = null)
        loadJob = viewModelScope.launch {
            try {
                val s = activeSession()
                val state = s.vehicleState(vehicle.id)
                _ui.update { (it as? UiState.Dashboard)?.copy(state = state, loading = false) ?: it }
            } catch (e: CoreException) {
                fail(e)
            }
        }
    }

    fun signOut() {
        loadJob?.cancel()
        session?.close()
        session = null
        store.clear()
        _ui.value = UiState.SignedOut()
    }

    private fun openSession(tokens: String) {
        session?.close()
        session = try {
            Session(tokens)
        } catch (e: CoreException) {
            store.clear()
            _ui.value = UiState.SignedOut(describe(e))
            return
        }
        _ui.value = UiState.Dashboard(vehicle = null, loading = true)
        loadJob = viewModelScope.launch {
            try {
                // shortcut: first vehicle only; add a picker when an account has several.
                val vehicle = activeSession().vehicles().firstOrNull()
                _ui.value = UiState.Dashboard(
                    vehicle = vehicle,
                    loading = false,
                    message = if (vehicle == null) "No vehicles on this account." else null,
                )
                if (vehicle != null) refresh()
            } catch (e: CoreException) {
                fail(e)
            }
        }
    }

    /** Refreshes tokens if they are about to expire and persists the new ones. */
    private suspend fun activeSession(): Session {
        val s = session ?: throw CoreException.NotAuthenticated()
        s.refreshIfNeeded()?.let(store::save)
        return s
    }

    private fun fail(e: CoreException) {
        when (e) {
            is CoreException.NotAuthenticated, is CoreException.SessionExpired -> {
                session?.close()
                session = null
                store.clear()
                _ui.value = UiState.SignedOut(describe(e))
            }
            else -> _ui.update {
                (it as? UiState.Dashboard)?.copy(loading = false, message = describe(e))
                    ?: UiState.SignedOut(describe(e))
            }
        }
    }

    override fun onCleared() {
        session?.close()
        pendingLogin?.close()
    }
}

internal fun describe(e: CoreException): String = when (e) {
    is CoreException.NotAuthenticated -> "Please sign in."
    is CoreException.SessionExpired -> "Your session expired. Please sign in again."
    is CoreException.Forbidden -> "Mercedes denied access to this vehicle."
    is CoreException.RateLimited -> "Mercedes is limiting requests right now. Try again later."
    is CoreException.VehicleNotFound -> "Vehicle not found on this account."
    is CoreException.LoginFailed -> "Sign-in failed: ${e.reason}"
    is CoreException.Network -> "Couldn't reach Mercedes. Check your connection."
    is CoreException.Protocol -> "Unexpected response from Mercedes: ${e.reason}"
}
