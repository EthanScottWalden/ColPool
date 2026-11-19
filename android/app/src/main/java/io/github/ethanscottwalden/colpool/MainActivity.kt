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
import io.github.ethanscottwalden.colpool.ui.onboarding.OnboardingViewModel
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
    private val addressSearch: AddressSearchViewModel by viewModels {
        viewModelFactory { initializer { AddressSearchViewModel((application as ColPoolApp).container.places) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { session.state.value is SessionState.Resolving }
        setContent {
            ColPoolTheme {
                ColPoolRoot(session, auth, onboarding, addressSearch)
            }
        }
    }
}
