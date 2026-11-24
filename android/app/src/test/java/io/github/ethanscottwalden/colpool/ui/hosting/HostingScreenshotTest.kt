package io.github.ethanscottwalden.colpool.ui.hosting

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday
import io.github.ethanscottwalden.colpool.ui.theme.ColPoolTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshot tests of Hosting. Made-up data only. Goldens: app/src/test/screenshots/hosting_*.png, hosted_series_details.png */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi", application = Application::class)
class HostingScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun shoot(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { ColPoolTheme { content() } }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun threeSeries() = listOf(
        HostingTestData.series("s1", HostingTestData.doc(weekdays = listOf("tue", "thu"), start = "2026-12-01", end = "2027-01-28", pickup = "Mission Grove Plaza, Ralphs lot, Riverside, CA")),
        HostingTestData.series("s2", HostingTestData.doc(weekdays = listOf("mon", "wed", "fri"), start = "2026-12-07", end = "2027-01-29", pickup = ReviewPickup, price = 5, max = 2, open = mapOf("mon" to 0, "wed" to 1, "fri" to 2))),
        HostingTestData.series("s3", HostingTestData.doc(weekdays = listOf("thu"), start = "2027-01-11", end = "2027-04-29", pickup = "Tyler Mall, Magnolia Ave, Riverside, CA", price = 3, max = 1, open = mapOf("thu" to 1))),
    )

    @Test fun empty() = shoot("hosting_empty") { HostingListContent(HostingTestData.state(emptyList()), HostingActions()) }

    @Test fun list() = shoot("hosting_list") { HostingListContent(HostingTestData.state(threeSeries()), HostingActions()) }

    @Config(sdk = [36], qualifiers = "w360dp-h1150dp-xxhdpi", application = Application::class)
    @Test fun details() = shoot("hosted_series_details") {
        HostedSeriesDetailsContent(HostingTestData.state(threeSeries(), selectedId = "s1", activeDay = SeriesWeekday.Tue), HostingActions())
    }

    private companion object { const val ReviewPickup = "Norco Towne Center, Hamner Ave, Norco, CA" }
}
