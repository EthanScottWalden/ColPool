package io.github.ethanscottwalden.colpool

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** JVM smoke test: the app starts (Firebase + emulator wiring) and shows Sign in when signed out. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appStartsAndShowsPlaceholder() {
        val app = ApplicationProvider.getApplicationContext<Application>() as ColPoolApp
        assertNotNull(app.container)
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Sign in").fetchSemanticsNodes().isNotEmpty() }
    }
}
