package io.github.ethanscottwalden.colpool.ui.onboarding

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.ethanscottwalden.colpool.data.places.SelectedAddress
import io.github.ethanscottwalden.colpool.ui.theme.ColPoolTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshot tests of Onboarding. Made-up data only. Goldens: app/src/test/screenshots/onboarding_*.png */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h1280dp-xxhdpi", application = Application::class)
class OnboardingScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val loaded = OnboardingUiState(optionsStatus = OptionsStatus.Loaded, options = FakeOnboardingBackend.TEST_OPTIONS)
    private val address = SelectedAddress("100 Example Street, Exampleville, CA 90000", "place-1", 12.5, -34.25)

    private fun shoot(name: String, state: OnboardingUiState) {
        composeRule.setContent { ColPoolTheme { OnboardingContent(state, OnboardingActions()) } }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/onboarding_$name.png")
    }

    @Test fun empty() = shoot("empty", loaded)

    @Test fun filled() = shoot(
        "filled",
        loaded.copy(
            address = address, age = "21", gender = "woman", year = "junior", major = "biology",
            interests = setOf("music", "hiking"), traits = setOf("quiet"), aboutMe = "Hi! I commute on weekday mornings.",
        ),
    )

    @Test fun validationErrors() = shoot("validation_errors", loaded.copy(age = "17", showErrors = true))

    @Test fun saveError() = shoot("save_error", loaded.copy(address = address, saveFailed = true))

    @Test fun optionsFailed() = shoot("options_failed", loaded.copy(optionsStatus = OptionsStatus.Failed, address = address))
}
