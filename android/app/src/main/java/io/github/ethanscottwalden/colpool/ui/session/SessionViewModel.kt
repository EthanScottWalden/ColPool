package io.github.ethanscottwalden.colpool.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ethanscottwalden.colpool.data.session.AuthBackend
import io.github.ethanscottwalden.colpool.data.session.PrivateDetailsResult
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SignedOutNotice { NoConsent }

/** Where the app is. Failures never route to a student or staff screen (ADR 0004). */
sealed interface SessionState {
    data object Resolving : SessionState
    data class SignedOut(val notice: SignedOutNotice? = null) : SessionState
    data object Onboarding : SessionState
    data object Search : SessionState
    data object Staff : SessionState
    /** Routing couldn't finish (network or other error). The user can retry or log out. */
    data object Error : SessionState
}

class SessionViewModel(private val backend: AuthBackend) : ViewModel() {
    private var generation = 0
    private val _state = MutableStateFlow<SessionState>(SessionState.Resolving)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    init {
        viewModelScope.launch { resolve() }
    }

    /**
     * Decides the route: signed out, staff (token claim), or student (server read of privateDetails).
     * [showProgress] puts the state back to Resolving first (used by Retry).
     */
    suspend fun resolve(showProgress: Boolean = false) {
        val mine = ++generation
        if (showProgress) _state.value = SessionState.Resolving
        val result = try {
            route()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SessionState.Error
        }
        // Latest call wins: a stale in-flight resolve never overwrites a newer one or a log out.
        if (mine == generation) _state.value = result
    }

    private suspend fun route(): SessionState {
        val user = backend.currentUser() ?: return SessionState.SignedOut()
        if (user.isStaff) return SessionState.Staff
        return when (backend.readPrivateDetails(user.uid)) {
            PrivateDetailsResult.HasStartingAddress -> SessionState.Search
            PrivateDetailsResult.Missing -> SessionState.Onboarding
            PrivateDetailsResult.NoConsent -> {
                backend.signOut()
                SessionState.SignedOut(SignedOutNotice.NoConsent)
            }
        }
    }

    fun retry() {
        viewModelScope.launch { resolve(showProgress = true) }
    }

    fun logOut() {
        generation++
        backend.signOut()
        _state.value = SessionState.SignedOut()
    }
}
