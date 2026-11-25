package io.github.ethanscottwalden.colpool.ui.details

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.ethanscottwalden.colpool.data.requests.DayFailureReason
import io.github.ethanscottwalden.colpool.ui.search.DayState
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday.Fri
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday.Mon
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday.Wed
import io.github.ethanscottwalden.colpool.ui.theme.ColPoolTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshot tests of Series Details. Made-up people only. Goldens: app/src/test/screenshots/series_details*.png */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h2100dp-xxhdpi", application = Application::class)
class DetailsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun shoot(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { ColPoolTheme { content() } }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private val campus = mapOf("yeager-center" to "Yeager Center", "lancer-plaza" to "Lancer Plaza")

    /** Monday open, Wednesday full, Friday no trips left; Tuesday and Thursday are not run. */
    private fun base(picked: Set<io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday> = setOf(Mon)): DetailsUiState {
        val s = DetailsTestData.mixed()
        return DetailsUiState(
            series = s, distanceTenths = 41, firstNameHint = "Ava", profile = DetailsTestData.AVA, profileLoaded = true,
            options = DetailsTestData.OPTIONS, campusNames = campus, currentYear = 2026, activeDay = Mon,
            dayStates = DetailsRules.dayStates(s, io.github.ethanscottwalden.colpool.ui.search.SearchTestData.NOW), picked = picked,
        )
    }

    @Test fun details() = shoot("series_details") { DetailsContent(base(), DetailsActions()) }

    @Test fun confirm() = shoot("series_details_confirm") { DetailsContent(base().copy(confirming = true), DetailsActions()) }

    @Config(sdk = [36], qualifiers = "w360dp-h2300dp-xxhdpi", application = Application::class)
    @Test fun dayErrors() = shoot("series_details_day_errors") {
        val s = DetailsTestData.open()
        val st = DetailsUiState(
            series = s, distanceTenths = 41, firstNameHint = "Ava", profile = DetailsTestData.AVA, profileLoaded = true,
            options = DetailsTestData.OPTIONS, campusNames = campus, currentYear = 2026, activeDay = Mon,
            dayStates = DetailsRules.dayStates(s, io.github.ethanscottwalden.colpool.ui.search.SearchTestData.NOW),
            picked = setOf(Fri), refused = mapOf(Mon to DayFailureReason.Full, Wed to DayFailureReason.Conflict), error = SendError.SomeDays,
        )
        DetailsContent(st, DetailsActions())
    }

    @Test fun alreadyRequested() = shoot("series_details_already_requested") {
        DetailsContent(base(emptySet()).copy(standing = Standing.AlreadyRequested), DetailsActions())
    }
}
