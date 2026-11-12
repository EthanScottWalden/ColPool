package io.github.ethanscottwalden.colpool

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.ethanscottwalden.colpool.ui.placeholder.PlaceholderScreen
import io.github.ethanscottwalden.colpool.ui.theme.ColPoolTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshot test of the placeholder. Golden: app/src/test/screenshots/placeholder.png */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi", application = Application::class)
class PlaceholderScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun placeholder() {
        composeRule.setContent { ColPoolTheme { PlaceholderScreen() } }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/placeholder.png")
    }
}
