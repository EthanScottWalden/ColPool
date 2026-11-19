package io.github.ethanscottwalden.colpool.ui.profile

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.ethanscottwalden.colpool.ui.shell.StudentShell
import io.github.ethanscottwalden.colpool.ui.theme.ColPoolTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshot tests of Profile (prototype 23-profile). Made-up data, full seeded option lists. Goldens: app/src/test/screenshots/profile_*.png */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h1200dp-xxhdpi", application = Application::class)
class ProfileScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun shoot(name: String, state: ProfileUiState) {
        composeRule.setContent { ColPoolTheme { ProfileContent(state, ProfileActions()) } }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/profile_$name.png")
    }

    private fun loaded(p: io.github.ethanscottwalden.colpool.data.model.OwnProfile) = ProfileUiState(OptionsStatus.Loaded, p, SeededOptions.ALL)

    @Test fun allFields() = shoot("all_fields", loaded(FakeProfileBackend.FULL))

    @Test fun minimalFields() = shoot("minimal", loaded(FakeProfileBackend.MINIMAL))

    /** The student shell with the real Profile tab (footer included). */
    @Test fun inStudentShell() {
        composeRule.setContent {
            ColPoolTheme { StudentShell(profileTab = { ProfileContent(loaded(FakeProfileBackend.FULL), ProfileActions(), it) }, initialTab = 4) }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/shell_student_profile.png")
    }

    @Test fun loadError() = shoot("load_error", ProfileUiState(status = OptionsStatus.Failed))
}
