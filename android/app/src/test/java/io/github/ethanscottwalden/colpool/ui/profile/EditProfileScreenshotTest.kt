package io.github.ethanscottwalden.colpool.ui.profile

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.ethanscottwalden.colpool.ui.theme.ColPoolTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshot tests of Edit Profile (prototype 24-edit-profile). Made-up data, full seeded option lists. Goldens: app/src/test/screenshots/edit_profile_*.png */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h1850dp-xxhdpi", application = Application::class)
class EditProfileScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun started(p: io.github.ethanscottwalden.colpool.data.model.OwnProfile = FakeProfileBackend.FULL) =
        EditProfileUiState(
            original = p, options = SeededOptions.ALL, firstName = p.firstName, lastName = p.lastName, address = p.address,
            age = p.age?.toString().orEmpty(), gender = p.gender, year = p.year, major = p.major,
            interests = p.interests.toSet(), traits = p.traits.toSet(), aboutMe = p.aboutMe.orEmpty(),
        )

    private fun shoot(name: String, state: EditProfileUiState) {
        composeRule.setContent { ColPoolTheme { EditProfileContent(state, EditProfileActions()) } }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/edit_profile_$name.png")
    }

    /** Opened, nothing changed: Save is disabled with its hint. */
    @Test fun filled() = shoot("filled", started())

    @Test fun changed() = shoot("changed", started().copy(firstName = "Casey", year = "senior"))

    @Test fun validationErrors() = shoot("validation_errors", started().copy(firstName = "  ", age = "17"))

    @Test fun saveError() = shoot("save_error", started().copy(lastName = "Sample", saveFailed = true))
}
