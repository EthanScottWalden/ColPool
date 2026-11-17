package io.github.ethanscottwalden.colpool.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ethanscottwalden.colpool.data.session.AuthBackend
import io.github.ethanscottwalden.colpool.data.session.AuthFailure
import io.github.ethanscottwalden.colpool.data.session.AuthFailureKind
import io.github.ethanscottwalden.colpool.data.session.SignUpRequest
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { SignIn, SignUp }

data class AuthUiState(
    val mode: AuthMode = AuthMode.SignIn,
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val password: String = "",
    val consent: Boolean = false,
    val busy: Boolean = false,
    /** Field errors show only after a submit attempt. */
    val showErrors: Boolean = false,
    val formError: AuthFailureKind? = null,
) {
    val fieldErrors: Set<SignUpFieldError>
        get() = if (mode == AuthMode.SignUp && showErrors) SignUpForm.validate(firstName, lastName, email, password, consent) else emptySet()
}

/**
 * Sign-in and sign-up form. [onAuthenticated] runs after a successful sign-in and re-runs the routing
 * decision (SessionViewModel.resolve), so the screen goes where the account belongs.
 */
class AuthViewModel(
    private val backend: AuthBackend,
    private val onAuthenticated: suspend () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun setMode(mode: AuthMode) = _state.update { it.copy(mode = mode, showErrors = false, formError = null) }
    fun setFirstName(v: String) = _state.update { it.copy(firstName = v, formError = null) }
    fun setLastName(v: String) = _state.update { it.copy(lastName = v, formError = null) }
    fun setEmail(v: String) = _state.update { it.copy(email = v, formError = null) }
    fun setPassword(v: String) = _state.update { it.copy(password = v, formError = null) }
    fun setConsent(v: Boolean) = _state.update { it.copy(consent = v, formError = null) }

    /** Clears the form (after log out, so the next person doesn't see it). */
    fun reset() {
        _state.value = AuthUiState()
    }

    fun submit() {
        val s = _state.value
        if (s.busy) return
        when (s.mode) {
            AuthMode.SignIn -> {
                if (s.email.isBlank() || s.password.isEmpty()) {
                    _state.update { it.copy(formError = AuthFailureKind.FieldsRequired) }
                    return
                }
                run { backend.signIn(s.email.trim(), s.password); true }
            }
            AuthMode.SignUp -> {
                _state.update { it.copy(showErrors = true) }
                if (SignUpForm.validate(s.firstName, s.lastName, s.email, s.password, s.consent).isNotEmpty()) return
                val email = SignUpForm.normalizeEmail(s.email)
                run {
                    try {
                        backend.signUp(SignUpRequest(email, s.password, s.firstName.trim(), s.lastName.trim()))
                    } catch (e: AuthFailure) {
                        // Timed out: the account may exist, so retrying Create would say "already exists".
                        if (e.kind != AuthFailureKind.CouldNotConfirm) throw e
                        switchToSignIn(email, AuthFailureKind.CouldNotConfirm)
                        return@run false
                    }
                    try {
                        backend.signIn(email, s.password)
                    } catch (e: AuthFailure) {
                        // The account exists now, whatever the failure (network included): never stay in Create mode.
                        switchToSignIn(email, AuthFailureKind.AccountCreatedSignIn)
                        return@run false
                    }
                    true
                }
            }
        }
    }

    private fun switchToSignIn(email: String, error: AuthFailureKind) {
        _state.update {
            it.copy(mode = AuthMode.SignIn, email = email, password = "", firstName = "", lastName = "", consent = false, showErrors = false, formError = error)
        }
    }

    /** [block] returns true to continue to routing. */
    private fun run(block: suspend () -> Boolean) {
        _state.update { it.copy(busy = true, formError = null) }
        viewModelScope.launch {
            try {
                if (block()) {
                    onAuthenticated()
                    // Never keep the password after routing (a no-consent sign-out returns here with the email kept).
                    _state.update { it.copy(password = "") }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthFailure) {
                _state.update { it.copy(formError = e.kind) }
            } catch (e: Exception) {
                _state.update { it.copy(formError = AuthFailureKind.Unknown) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
