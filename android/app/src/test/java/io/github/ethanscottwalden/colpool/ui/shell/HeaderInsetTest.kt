package io.github.ethanscottwalden.colpool.ui.shell

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.ethanscottwalden.colpool.ui.auth.AuthActions
import io.github.ethanscottwalden.colpool.ui.auth.AuthContent
import io.github.ethanscottwalden.colpool.ui.auth.AuthUiState
import io.github.ethanscottwalden.colpool.ui.onboarding.OnboardingActions
import io.github.ethanscottwalden.colpool.ui.onboarding.OnboardingContent
import io.github.ethanscottwalden.colpool.ui.onboarding.OnboardingUiState
import io.github.ethanscottwalden.colpool.ui.profile.EditProfileActions
import io.github.ethanscottwalden.colpool.ui.profile.EditProfileContent
import io.github.ethanscottwalden.colpool.ui.profile.EditProfileUiState
import io.github.ethanscottwalden.colpool.ui.profile.ProfileActions
import io.github.ethanscottwalden.colpool.ui.profile.ProfileContent
import io.github.ethanscottwalden.colpool.ui.profile.ProfileUiState
import io.github.ethanscottwalden.colpool.ui.search.SearchActions
import io.github.ethanscottwalden.colpool.ui.search.SearchContent
import io.github.ethanscottwalden.colpool.ui.search.SearchUiState
import io.github.ethanscottwalden.colpool.ui.theme.ColPoolTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The shared header pads for the status bar itself, so the student shell must not pass that inset down too
 * (the owner saw a big blank gap above the Search header on a device). Screenshot tests have no status bar, so this
 * one dispatches a 96 px status-bar inset and checks the header is pushed down once, not twice.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi", application = Application::class)
class HeaderInsetTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val statusBarPx = 96

    /** Shows [content], applies the inset, and checks the text [text] sits below the inset once, not twice. */
    private fun assertPushedDownOnce(text: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent { ColPoolTheme { content() } }
        val view = composeRule.activity.window.decorView
        composeRule.runOnUiThread {
            val insets = WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, statusBarPx, 0, 0)).build()
            ViewCompat.dispatchApplyWindowInsets(view, insets)
        }
        composeRule.waitForIdle()
        val top = composeRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top
        // Own padding is 16-24dp (48-72 px here); a doubled inset would add another 96 px.
        assertTrue("\"$text\" at $top should be below the inset", top >= statusBarPx)
        assertTrue("\"$text\" at $top shows a doubled inset", top < statusBarPx + 90)
    }

    @Test fun searchHeaderSitsDirectlyUnderTheStatusBar() = assertPushedDownOnce("Find a ride") {
        StudentShell(
            searchTab = { SearchContent(SearchUiState(currentYear = 2026), SearchActions(), it) },
            profileTab = {}, hostingTab = {},
        )
    }

    @Test fun profileTabInTheShell() = assertPushedDownOnce("Your profile") {
        StudentShell(searchTab = {}, hostingTab = {}, profileTab = { ProfileContent(ProfileUiState(), ProfileActions(), it) }, initialTab = 4)
    }

    @Test fun signIn() = assertPushedDownOnce("ColPool") { AuthContent(AuthUiState(), null, AuthActions()) }

    @Test fun onboarding() = assertPushedDownOnce("Welcome to ColPool!") { OnboardingContent(OnboardingUiState(), OnboardingActions()) }

    @Test fun editProfile() = assertPushedDownOnce("Edit profile") { EditProfileContent(EditProfileUiState(), EditProfileActions()) }
}
