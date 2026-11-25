package io.github.ethanscottwalden.colpool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.ethanscottwalden.colpool.ui.ColPoolRoot
import io.github.ethanscottwalden.colpool.ui.address.AddressSearchViewModel
import io.github.ethanscottwalden.colpool.ui.auth.AuthViewModel
import io.github.ethanscottwalden.colpool.ui.hosting.HostRequestsViewModel
import io.github.ethanscottwalden.colpool.ui.notifications.NotificationsViewModel
import io.github.ethanscottwalden.colpool.ui.hosting.HostingViewModel
import io.github.ethanscottwalden.colpool.ui.details.DetailsViewModel
import io.github.ethanscottwalden.colpool.ui.search.SearchViewModel
import io.github.ethanscottwalden.colpool.ui.onboarding.OnboardingViewModel
import io.github.ethanscottwalden.colpool.ui.profile.EditProfileViewModel
import io.github.ethanscottwalden.colpool.ui.profile.ProfileViewModel
import io.github.ethanscottwalden.colpool.ui.series.CreateSeriesViewModel
import io.github.ethanscottwalden.colpool.ui.series.ReviewViewModel
import io.github.ethanscottwalden.colpool.ui.session.SessionState
import io.github.ethanscottwalden.colpool.ui.session.SessionViewModel
import io.github.ethanscottwalden.colpool.ui.theme.ColPoolTheme

/** Single Activity. The system splash stays until routing resolves; signed-in users go straight to their route. */
class MainActivity : ComponentActivity() {
    private val backend get() = (application as ColPoolApp).container.authBackend

    private val session: SessionViewModel by viewModels {
        viewModelFactory { initializer { SessionViewModel(backend) } }
    }
    private val auth: AuthViewModel by viewModels {
        viewModelFactory { initializer { AuthViewModel(backend) { session.resolve() } } }
    }

    private val onboarding: OnboardingViewModel by viewModels {
        viewModelFactory {
            initializer { OnboardingViewModel((application as ColPoolApp).container.onboardingBackend) { session.resolve() } }
        }
    }
    private val profile: ProfileViewModel by viewModels {
        viewModelFactory { initializer { ProfileViewModel((application as ColPoolApp).container.profileBackend) } }
    }
    private val editProfile: EditProfileViewModel by viewModels {
        viewModelFactory {
            initializer { EditProfileViewModel((application as ColPoolApp).container.profileBackend) { profile.applySaved(it) } }
        }
    }
    private val addressSearch: AddressSearchViewModel by viewModels {
        viewModelFactory { initializer { AddressSearchViewModel((application as ColPoolApp).container.places) } }
    }

    private val createSeries: CreateSeriesViewModel by viewModels {
        viewModelFactory { initializer { CreateSeriesViewModel((application as ColPoolApp).container.seriesBackend) } }
    }

    private val review: ReviewViewModel by viewModels {
        viewModelFactory { initializer { ReviewViewModel((application as ColPoolApp).container.seriesBackend) } }
    }

    private val hosting: HostingViewModel by viewModels {
        viewModelFactory { initializer { HostingViewModel((application as ColPoolApp).container.seriesBackend) } }
    }

    private val search: SearchViewModel by viewModels {
        viewModelFactory { initializer { SearchViewModel((application as ColPoolApp).container.searchBackend, store = (application as ColPoolApp).container.preferenceStore) } }
    }

    private val details: DetailsViewModel by viewModels {
        viewModelFactory { initializer { DetailsViewModel((application as ColPoolApp).container.requestBackend) } }
    }

    private val hostRequests: HostRequestsViewModel by viewModels {
        viewModelFactory { initializer { HostRequestsViewModel((application as ColPoolApp).container.hostRequestsBackend) } }
    }

    private val notifications: NotificationsViewModel by viewModels {
        viewModelFactory { initializer { NotificationsViewModel((application as ColPoolApp).container.notificationsBackend) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { session.state.value is SessionState.Resolving }
        setContent {
            ColPoolTheme {
                ColPoolRoot(session, auth, onboarding, profile, editProfile, addressSearch, createSeries, review, hosting, search, details, hostRequests, notifications)
            }
        }
    }
}
