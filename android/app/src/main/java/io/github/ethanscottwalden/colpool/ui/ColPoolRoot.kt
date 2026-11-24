package io.github.ethanscottwalden.colpool.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.ethanscottwalden.colpool.ui.auth.AuthActions
import io.github.ethanscottwalden.colpool.ui.auth.AuthContent
import io.github.ethanscottwalden.colpool.ui.auth.AuthMode
import io.github.ethanscottwalden.colpool.ui.address.AddressSearchViewModel
import io.github.ethanscottwalden.colpool.ui.auth.AuthViewModel
import io.github.ethanscottwalden.colpool.ui.details.DetailsViewModel
import io.github.ethanscottwalden.colpool.ui.details.SeriesDetailsScreen
import io.github.ethanscottwalden.colpool.ui.hosting.HostRequestRules
import io.github.ethanscottwalden.colpool.ui.hosting.HostRequestsViewModel
import io.github.ethanscottwalden.colpool.ui.hosting.HostedSeriesDetailsScreen
import io.github.ethanscottwalden.colpool.ui.hosting.RequesterProfileScreen
import io.github.ethanscottwalden.colpool.ui.hosting.HostingTab
import io.github.ethanscottwalden.colpool.ui.hosting.HostingViewModel
import io.github.ethanscottwalden.colpool.ui.onboarding.OnboardingScreen
import io.github.ethanscottwalden.colpool.ui.onboarding.OnboardingViewModel
import io.github.ethanscottwalden.colpool.ui.profile.EditProfileViewModel
import io.github.ethanscottwalden.colpool.ui.profile.ProfileTab
import io.github.ethanscottwalden.colpool.ui.profile.ProfileViewModel
import io.github.ethanscottwalden.colpool.ui.search.SearchTab
import io.github.ethanscottwalden.colpool.ui.search.SearchViewModel
import io.github.ethanscottwalden.colpool.ui.series.CreateSeriesScreen
import io.github.ethanscottwalden.colpool.ui.series.CreateSeriesViewModel
import io.github.ethanscottwalden.colpool.ui.series.ReviewScreen
import io.github.ethanscottwalden.colpool.ui.series.ReviewViewModel
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
    review: ReviewViewModel,
    hosting: HostingViewModel,
    search: SearchViewModel,
    details: DetailsViewModel,
    hostRequests: HostRequestsViewModel,
) {
    val state by session.state.collectAsState()
    val authState by auth.state.collectAsState()
    val hostingState by hosting.state.collectAsState()
    val searchState by search.state.collectAsState()
    val requestsState by hostRequests.state.collectAsState()
    // Requests with a pending day on the series the host still has active.
    val pendingBadge = remember(requestsState.all, hostingState.series) {
        HostRequestRules.pendingCount(requestsState.all, hostingState.series.map { it.id }.toSet())
    }
    val errorDetail by session.errorDetail.collectAsState()
    // No navigation library yet (ADR 0004): Create Series and the Review placeholder sit above the student shell.
    var creatingSeries by rememberSaveable { mutableStateOf(false) }
    var reviewDraftId by rememberSaveable { mutableStateOf<String?>(null) }
    // The Hosting tab starts the flow, so every way out of it lands back on Hosting (#33 replaces its placeholder).
    var shellTab by rememberSaveable { mutableStateOf(0) }

    // Returning from the details screen must land on the Hosting tab.
    LaunchedEffect(hostingState.selectedId) { if (hostingState.selectedId != null) shellTab = HOSTING_TAB }

    // The open hosted series listens to its own requests; closing it stops.
    LaunchedEffect(hostingState.selectedId) { hostingState.selectedId?.let { hostRequests.openSeries(it) } ?: hostRequests.closeSeries() }

    // Opening a series from Search fills the details; closing it forgets them.
    LaunchedEffect(searchState.openedSeriesId) {
        val r = searchState.results.firstOrNull { it.series.id == searchState.openedSeriesId }
        if (r != null) details.open(r.series, r.distanceTenths, searchState.hostNames[r.series.hostId]) else details.reset()
    }

    LaunchedEffect(state) {
        if (state !is SessionState.SignedOut) auth.reset()
        // Onboarding state (including the private address) never outlives the screen.
        if (state == SessionState.Onboarding) onboarding.loadOptions() else onboarding.reset()
        // Same for the own profile (address, email): loaded for the student shell only.
        if (state == SessionState.Search) profile.load() else { profile.reset(); editProfile.reset() }
        if (state == SessionState.Search) hosting.start() else hosting.reset()
        if (state == SessionState.Search) hostRequests.start() else hostRequests.reset()
        if (state != SessionState.Search) { search.reset(); details.reset() }
        if (state != SessionState.Search) { createSeries.reset(); review.reset(); creatingSeries = false; reviewDraftId = null; shellTab = 0 }
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
            reviewDraftId != null -> ReviewScreen(
                review, reviewDraftId!!,
                // Posted (or already posted): the form is done; show the Hosting tab.
                onPosted = { review.reset(); createSeries.reset(); reviewDraftId = null; creatingSeries = false },
                // Back to the form, kept as it was; the backend's errors, if any, show there too.
                onEdit = { review.reset(); createSeries.showReviewErrors(it.errors); reviewDraftId = null },
            )
            creatingSeries -> CreateSeriesScreen(
                createSeries, addressSearch,
                onReview = { reviewDraftId = it },
                onBack = { creatingSeries = false; createSeries.reset() },
            )
            searchState.openedSeriesId != null -> SeriesDetailsScreen(details, onBack = search::close, onSent = search::requestSent)
            hostingState.selectedId != null && requestsState.opened != null -> RequesterProfileScreen(hostRequests)
            hostingState.selectedId != null -> HostedSeriesDetailsScreen(hosting, hostRequests)
            else -> StudentShell(
                initialTab = shellTab,
                hostingBadge = pendingBadge,
                searchTab = { SearchTab(search, modifier = it) },
                profileTab = { ProfileTab(profile, editProfile, addressSearch, onLogOut = session::logOut, modifier = it) },
                hostingTab = { HostingTab(hosting, onHostSeries = { createSeries.reset(); shellTab = HOSTING_TAB; creatingSeries = true }, modifier = it) },
            )
        }
        SessionState.Staff -> StaffExportPlaceholder(onLogOut = session::logOut)
        SessionState.Error -> SessionErrorScreen(onRetry = session::retry, onLogOut = session::logOut, detail = errorDetail)
    }
}

private const val HOSTING_TAB = 1
