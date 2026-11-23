package io.github.ethanscottwalden.colpool.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.ethanscottwalden.colpool.ui.auth.AuthActions
import io.github.ethanscottwalden.colpool.ui.auth.AuthContent
import io.github.ethanscottwalden.colpool.ui.auth.AuthMode
import io.github.ethanscottwalden.colpool.ui.address.AddressSearchViewModel
import io.github.ethanscottwalden.colpool.ui.auth.AuthViewModel
import io.github.ethanscottwalden.colpool.ui.onboarding.OnboardingScreen
import io.github.ethanscottwalden.colpool.ui.onboarding.OnboardingViewModel
import io.github.ethanscottwalden.colpool.ui.profile.EditProfileViewModel
import io.github.ethanscottwalden.colpool.ui.profile.ProfileTab
import io.github.ethanscottwalden.colpool.ui.profile.ProfileViewModel
import io.github.ethanscottwalden.colpool.ui.series.CreateSeriesScreen
import io.github.ethanscottwalden.colpool.ui.series.CreateSeriesViewModel
import io.github.ethanscottwalden.colpool.ui.series.ReviewPlaceholder
import io.github.ethanscottwalden.colpool.ui.session.SessionState
import io.github.ethanscottwalden.colpool.ui.session.SessionViewModel
import io.github.ethanscottwalden.colpool.ui.shell.LoadingScreen
import io.github.ethanscottwalden.colpool.ui.shell.SessionErrorScreen
import io.github.ethanscottwalden.colpool.ui.shell.StaffExportPlaceholder
import io.github.ethanscottwalden.colpool.ui.shell.StudentShell

/** Picks the screen for the current [SessionState]. No navigation library yet. */
@Composable
fun ColPoolRoot(
    session: SessionViewModel,
    auth: AuthViewModel,
    onboarding: OnboardingViewModel,
    profile: ProfileViewModel,
    editProfile: EditProfileViewModel,
    addressSearch: AddressSearchViewModel,
    createSeries: CreateSeriesViewModel,
) {
    val state by session.state.collectAsState()
    val authState by auth.state.collectAsState()
    val errorDetail by session.errorDetail.collectAsState()
    // No navigation library yet (ADR 0004): Create Series and the Review placeholder sit above the student shell.
    var creatingSeries by rememberSaveable { mutableStateOf(false) }
    var reviewDraftId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state) {
        if (state !is SessionState.SignedOut) auth.reset()
        // Onboarding state (including the private address) never outlives the screen.
        if (state == SessionState.Onboarding) onboarding.loadOptions() else onboarding.reset()
        // Same for the own profile (address, email): loaded for the student shell only.
        if (state == SessionState.Search) profile.load() else { profile.reset(); editProfile.reset() }
        if (state != SessionState.Search) { createSeries.reset(); creatingSeries = false; reviewDraftId = null }
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
        SessionState.Onboarding -> OnboardingScreen(onboarding, addressSearch, onLogOut = session::logOut)
        SessionState.Search -> when {
            reviewDraftId != null -> ReviewPlaceholder(reviewDraftId!!, onBack = { reviewDraftId = null })
            creatingSeries -> CreateSeriesScreen(
                createSeries, addressSearch,
                onReview = { reviewDraftId = it },
                onBack = { creatingSeries = false; createSeries.reset() },
            )
            else -> StudentShell(
                profileTab = { ProfileTab(profile, editProfile, addressSearch, onLogOut = session::logOut, modifier = it) },
                onHostSeries = { createSeries.reset(); creatingSeries = true },
            )
        }
        SessionState.Staff -> StaffExportPlaceholder(onLogOut = session::logOut)
        SessionState.Error -> SessionErrorScreen(onRetry = session::retry, onLogOut = session::logOut, detail = errorDetail)
    }
}
