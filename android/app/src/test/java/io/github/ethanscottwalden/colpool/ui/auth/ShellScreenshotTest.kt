package io.github.ethanscottwalden.colpool.ui.auth

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.ethanscottwalden.colpool.ui.session.SignedOutNotice
import io.github.ethanscottwalden.colpool.ui.shell.SessionErrorScreen
import io.github.ethanscottwalden.colpool.ui.shell.StaffExportPlaceholder
import io.github.ethanscottwalden.colpool.ui.shell.StudentShell
import io.github.ethanscottwalden.colpool.ui.theme.ColPoolTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshot tests of sign-in routing screens. Made-up data only. Goldens: app/src/test/screenshots/auth_*.png, shell_*.png, staff_*.png */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi", application = Application::class)
class ShellScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun shoot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent { ColPoolTheme { content() } }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun signIn() = shoot("auth_sign_in") { AuthContent(AuthUiState(), null, AuthActions()) }

    @Test fun signUpEmpty() = shoot("auth_sign_up_empty") { AuthContent(AuthUiState(mode = AuthMode.SignUp), null, AuthActions()) }

    @Test fun signUpWithErrors() = shoot("auth_sign_up_errors") {
        AuthContent(
            AuthUiState(mode = AuthMode.SignUp, firstName = "Jordan", email = "jlee@example.com", password = "short", showErrors = true),
            null, AuthActions(),
        )
    }

    @Test fun noConsentError() = shoot("auth_no_consent") { AuthContent(AuthUiState(), SignedOutNotice.NoConsent, AuthActions()) }

    @Test fun studentShellProfileTab() = shoot("shell_student_profile") { StudentShell(onLogOut = {}, initialTab = 4) }

    @Test fun staffExport() = shoot("staff_export") { StaffExportPlaceholder(onLogOut = {}) }

    @Test fun sessionError() = shoot("session_error") { SessionErrorScreen({}, {}) }
}
