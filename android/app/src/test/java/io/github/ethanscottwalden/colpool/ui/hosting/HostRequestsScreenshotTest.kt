package io.github.ethanscottwalden.colpool.ui.hosting

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.ethanscottwalden.colpool.ui.details.DetailsTestData
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday
import io.github.ethanscottwalden.colpool.ui.shell.StudentShell
import io.github.ethanscottwalden.colpool.ui.theme.ColPoolTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshot tests of host requests. Made-up people only. Goldens: app/src/test/screenshots/hosted_series_requests.png, requester_profile*.png, hosting_list_badge.png */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi", application = Application::class)
class HostRequestsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun shoot(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { ColPoolTheme { content() } }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private val threeRequests = listOf(
        HostRequestsTestData.request("a", riderId = "maya", created = 1_000, days = arrayOf("tue" to "pending", "thu" to "accepted")),
        HostRequestsTestData.request("b", riderId = "jordan", created = 2_000, days = arrayOf("tue" to "pending", "thu" to "pending")),
        HostRequestsTestData.request("c", riderId = "gone", created = 3_000, days = arrayOf("thu" to "declined", "fri" to "pending")),
    )

    @Config(sdk = [36], qualifiers = "w360dp-h1700dp-xxhdpi", application = Application::class)
    @Test fun requests() = shoot("hosted_series_requests") {
        HostedSeriesDetailsContent(
            HostingTestData.state(selectedId = "s1", activeDay = SeriesWeekday.Tue), HostingActions(),
            requests = HostRequestsUiState(forSeries = threeRequests, seriesLoaded = true, names = HostRequestsTestData.NAMES + ("gone" to null)),
        )
    }

    @Config(sdk = [36], qualifiers = "w360dp-h1700dp-xxhdpi", application = Application::class)
    @Test fun requestsFailed() = shoot("hosted_series_requests_failed") {
        HostedSeriesDetailsContent(HostingTestData.state(selectedId = "s1", activeDay = SeriesWeekday.Tue), HostingActions(), requests = HostRequestsUiState(seriesFailed = true, seriesLoaded = true))
    }

    @Test fun profile() = shoot("requester_profile") {
        RequesterProfileContent(RequesterProfileState("maya", HostRequestsTestData.MAYA, true), DetailsTestData.OPTIONS, {})
    }

    @Test fun profileUnavailable() = shoot("requester_profile_unavailable") {
        RequesterProfileContent(RequesterProfileState("maya", null, true), DetailsTestData.OPTIONS, {})
    }

    /** The student shell on the Hosting tab with two requests with pending days. */
    @Test fun hostingListBadge() = shoot("hosting_list_badge") {
        StudentShell(
            searchTab = {}, profileTab = {}, initialTab = 1, hostingBadge = 2,
            hostingTab = { HostingListContent(HostingTestData.state(listOf(HostingTestData.series("s1"))), HostingActions(), it) },
        )
    }
}
