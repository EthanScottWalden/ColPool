package io.github.ethanscottwalden.colpool

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented smoke test: launches the app to the placeholder. Owner-run on the emulator; CI after #54. */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesToPlaceholder() {
        rule.onNodeWithText("ColPool").assertExists()
    }
}
