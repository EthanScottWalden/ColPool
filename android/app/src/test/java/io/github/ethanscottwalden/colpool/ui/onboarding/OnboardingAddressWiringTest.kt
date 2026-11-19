package io.github.ethanscottwalden.colpool.ui.onboarding

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.ethanscottwalden.colpool.ui.address.AddressSearchTags
import io.github.ethanscottwalden.colpool.ui.address.AddressSearchViewModel
import io.github.ethanscottwalden.colpool.ui.address.FakePlaces
import io.github.ethanscottwalden.colpool.ui.theme.ColPoolTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Stateful wiring: tapping the address field on Onboarding opens the address search sheet. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class OnboardingAddressWiringTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun tappingTheAddressFieldOpensTheSheet() {
        val onboarding = OnboardingViewModel(FakeOnboardingBackend()) {}
        val search = AddressSearchViewModel(FakePlaces(), debounceMs = 0)
        rule.setContent { ColPoolTheme { OnboardingScreen(onboarding, search, onLogOut = {}) } }
        rule.onNodeWithTag(OnboardingTags.AddressField).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(AddressSearchTags.Field).assertExists()
    }
}
