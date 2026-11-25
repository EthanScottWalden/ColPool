package io.github.ethanscottwalden.colpool.ui.shell

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

    @Test fun searchHeaderSitsDirectlyUnderTheStatusBar() {
        composeRule.setContent {
            ColPoolTheme {
                StudentShell(
                    searchTab = { SearchContent(SearchUiState(currentYear = 2026), SearchActions(), it) },
                    profileTab = {}, hostingTab = {},
                )
            }
        }
        val view = composeRule.activity.window.decorView
        composeRule.runOnUiThread {
            val insets = WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, statusBarPx, 0, 0)).build()
            ViewCompat.dispatchApplyWindowInsets(view, insets)
        }
        composeRule.waitForIdle()
        val titleTop = composeRule.onNodeWithText("Find a ride").fetchSemanticsNode().boundsInRoot.top
        // Header padding plus the eyebrow line is about 30 px here; a doubled inset adds another 96 px.
        assertTrue("title at $titleTop should be below the inset", titleTop >= statusBarPx)
        assertTrue("title at $titleTop shows a doubled inset", titleTop < statusBarPx + 60)
    }
}
