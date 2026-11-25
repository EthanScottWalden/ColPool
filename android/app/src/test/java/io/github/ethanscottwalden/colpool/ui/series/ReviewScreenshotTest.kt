package io.github.ethanscottwalden.colpool.ui.series

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

/** Screenshot tests of Review. Made-up data only. Goldens: app/src/test/screenshots/review_*.png */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h1500dp-xxhdpi", application = Application::class)
class ReviewScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun shoot(name: String, state: ReviewUiState) {
        composeRule.setContent { ColPoolTheme { ReviewContent(state, ReviewActions()) } }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/review_$name.png")
    }

    @Test fun oneStopEachWay() = shoot("one_stop", ReviewTestData.loaded())

    @Test fun noStops() = shoot("no_stops", ReviewTestData.loaded(active = SeriesWeekday.Mon))

    @Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi", application = Application::class)
    @Test fun confirmationSheet() = shoot("confirm_sheet", ReviewTestData.loaded().copy(confirming = true))

    @Test fun postError() = shoot("post_error", ReviewTestData.loaded().copy(postError = PostError.Retry))

    @Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi", application = Application::class)
    @Test fun loadFailed() = shoot("load_failed", ReviewUiState(load = ReviewLoad.Failed))
}
