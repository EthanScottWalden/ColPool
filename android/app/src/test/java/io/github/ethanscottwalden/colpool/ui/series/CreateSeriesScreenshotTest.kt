package io.github.ethanscottwalden.colpool.ui.series

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.ethanscottwalden.colpool.data.places.AddressSuggestion
import io.github.ethanscottwalden.colpool.data.series.SeriesError
import io.github.ethanscottwalden.colpool.ui.address.AddressSearchContent
import io.github.ethanscottwalden.colpool.ui.address.AddressSearchState
import io.github.ethanscottwalden.colpool.ui.profile.OptionsStatus
import io.github.ethanscottwalden.colpool.ui.series.SeriesTestData.TODAY
import io.github.ethanscottwalden.colpool.ui.theme.ColPoolTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshot tests of Create Series. Made-up data only. Goldens: app/src/test/screenshots/create_series_*.png */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h2100dp-xxhdpi", application = Application::class)
class CreateSeriesScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun shoot(name: String, state: CreateSeriesUiState) {
        composeRule.setContent { ColPoolTheme { CreateSeriesContent(state, TODAY, CreateSeriesActions()) } }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/create_series_$name.png")
    }

    private val empty = CreateSeriesUiState(campusStatus = OptionsStatus.Loaded, campusPoints = SeriesTestData.CAMPUS_POINTS)

    @Config(sdk = [36], qualifiers = "w360dp-h900dp-xxhdpi", application = Application::class)
    @Test fun empty() = shoot("empty", empty)

    @Test fun filled() = shoot("filled", SeriesTestData.filled())

    @Test fun errors() {
        val s = SeriesTestData.filled().let {
            it.copy(
                vehicle = "",
                price = "150",
                endDate = "2026-11-27",
                days = it.days + (SeriesWeekday.Wed to it.day(SeriesWeekday.Wed).let { d -> d.withLeg(Direction.FromCampus, d.fromCampus.copy(departMinutes = null)) }),
                errors = listOf(
                    SeriesError("hostingLimitReached", "", mapOf("limit" to 5)),
                    SeriesError("noDateOnWeekdays", ""),
                    SeriesError("required", "vehicle"),
                    SeriesError("outOfRange", "priceDollars", mapOf("min" to 1, "max" to 99)),
                    SeriesError("endBeforeStart", "endDate"),
                    SeriesError("hostingWeekdayOverlap", "days.wed", mapOf("weekday" to "wed")),
                    SeriesError("routeNotFound", "days.wed.toCampus.stops.0.address"),
                    SeriesError("required", "days.wed.fromCampus.departTime"),
                ),
            )
        }
        shoot("errors", s)
    }

    @Test fun addStopSheet() {
        val state = AddressSearchState(
            query = "Starbucks",
            suggestions = listOf(
                AddressSuggestion("p1", "Starbucks", "3750 University Ave, Riverside, CA"),
                AddressSuggestion("p2", "Starbucks", "Magnolia Ave, Riverside, CA"),
            ),
        )
        composeRule.setContent {
            ColPoolTheme {
                Surface {
                    AddressSearchContent("Add a stop", state, {}, {}, {}, notice = { AddStopNotice("5") {} })
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/create_series_add_stop_sheet.png")
    }
}
