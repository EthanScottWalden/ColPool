package io.github.ethanscottwalden.colpool.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.ethanscottwalden.colpool.ui.auth.AuthActions
import io.github.ethanscottwalden.colpool.ui.auth.AuthContent
import io.github.ethanscottwalden.colpool.ui.auth.AuthMode
import io.github.ethanscottwalden.colpool.ui.auth.AuthViewModel
import io.github.ethanscottwalden.colpool.ui.session.SessionState
import io.github.ethanscottwalden.colpool.ui.session.SessionViewModel
import io.github.ethanscottwalden.colpool.ui.shell.LoadingScreen
import io.github.ethanscottwalden.colpool.ui.shell.OnboardingPlaceholder
import io.github.ethanscottwalden.colpool.ui.shell.SessionErrorScreen
import io.github.ethanscottwalden.colpool.ui.shell.StaffExportPlaceholder
import io.github.ethanscottwalden.colpool.ui.shell.StudentShell

/** Picks the screen for the current [SessionState]. No navigation library yet. */
@Composable
fun ColPoolRoot(session: SessionViewModel, auth: AuthViewModel) {
    val state by session.state.collectAsState()
    val authState by auth.state.collectAsState()
    val errorDetail by session.errorDetail.collectAsState()

    LaunchedEffect(state) {
        if (state !is SessionState.SignedOut) auth.reset()
    }

    when (val s = state) {
        SessionState.Resolving -> LoadingScreen()
        is SessionState.SignedOut -> AuthContent(
            state = authState,
            notice = s.notice,
            actions = AuthActions(
                onMode = auth::setMode,
                onFirstName = auth::setFirstName,
                onLastName = auth::setLastName,
                onEmail = auth::setEmail,
                onPassword = auth::setPassword,
                onConsent = auth::setConsent,
                onSubmit = auth::submit,
            ),
        )
        SessionState.Onboarding -> OnboardingPlaceholder(onLogOut = session::logOut)
        SessionState.Search -> StudentShell(onLogOut = session::logOut)
        SessionState.Staff -> StaffExportPlaceholder(onLogOut = session::logOut)
        SessionState.Error -> SessionErrorScreen(onRetry = session::retry, onLogOut = session::logOut, detail = errorDetail)
    }
}
