package io.github.ethanscottwalden.colpool.ui.hosting

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.ethanscottwalden.colpool.ui.shell.HOSTING_BADGE_TAG
import io.github.ethanscottwalden.colpool.ui.shell.StudentShell
import io.github.ethanscottwalden.colpool.ui.details.DetailsTestData
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday
import io.github.ethanscottwalden.colpool.ui.theme.ColPoolTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h2400dp-xxhdpi", application = Application::class)
class HostRequestsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun details(requests: HostRequestsUiState, actions: HostingActions = HostingActions()) = composeRule.setContent {
        ColPoolTheme { HostedSeriesDetailsContent(HostingTestData.state(selectedId = "s1", activeDay = SeriesWeekday.Tue), actions, requests = requests) }
    }

    private fun withMixed() = HostRequestsUiState(forSeries = HostRequestsTestData.MIXED, seriesLoaded = true, names = HostRequestsTestData.NAMES)

    @Test fun rowsShowNamesAndEveryDayStatus() {
        details(withMixed())
        composeRule.onNodeWithTag(HostingTags.RequestsHeading).assertTextEquals("Requests · 2 with pending days")
        composeRule.onNodeWithText("Maya Okafor").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Tue · pending, Thu · accepted").assertExists()
        composeRule.onNodeWithText("Jordan Reyes").assertExists()
        composeRule.onNodeWithText("Fri · pending").assertExists()
        composeRule.onNodeWithText("No requests yet.").assertDoesNotExist()
    }

    @Test fun declinedAndUnknownStatusesAreShown() {
        val r = HostRequestsTestData.request("a", riderId = "maya", days = arrayOf("tue" to "pending", "wed" to "declined", "fri" to "on-hold"))
        details(HostRequestsUiState(forSeries = listOf(r), names = HostRequestsTestData.NAMES))
        composeRule.onNodeWithText("Tue · pending, Wed · declined, Fri · on-hold").performScrollTo().assertIsDisplayed()
    }

    @Test fun emptyState() {
        details(HostRequestsUiState())
        composeRule.onNodeWithTag(HostingTags.RequestsHeading).assertTextEquals("Requests · 0 with pending days")
        composeRule.onNodeWithTag(HostingTags.Requests).performScrollTo().assertTextEquals("No requests yet.")
    }

    @Test fun anUnreadableNameFallsBackToARider() {
        details(HostRequestsUiState(forSeries = HostRequestsTestData.MIXED, names = mapOf("maya" to null)))
        composeRule.onAllNodesWithText("A rider", useUnmergedTree = true).assertCountEquals(2)
        composeRule.onNodeWithText("Maya Okafor").assertDoesNotExist()
    }

    @Test fun failedListenerShowsTheErrorAndRetryNotTheEmptyText() {
        var retried = 0
        details(HostRequestsUiState(seriesFailed = true), HostingActions(onRetryRequests = { retried++ }))
        composeRule.onNodeWithTag(HostingTags.Requests).assertDoesNotExist()
        composeRule.onNodeWithTag(HostingTags.RequestsFailed).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(HostingTags.RequestsRetry).performScrollTo().performClick()
        assertEquals(1, retried)
    }

    @Test fun failedListenerKeepsTheLastGoodRows() {
        details(withMixed().copy(seriesFailed = true))
        composeRule.onNodeWithText("Maya Okafor").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(HostingTags.RequestsFailed).assertExists()
    }

    @Test fun noAcceptOrDeclineButtons() {
        details(withMixed())
        composeRule.onNodeWithText("Accept").assertDoesNotExist()
        composeRule.onNodeWithText("Decline").assertDoesNotExist()
    }

    @Test fun tappingProfileOpensTheRequester() {
        var opened: String? = null
        details(withMixed(), HostingActions(onOpenRequest = { opened = it }))
        composeRule.onNodeWithTag(HostingTags.request("s1_maya")).performScrollTo().performClick()
        assertEquals("maya", opened)
    }

    @Test fun rowHasADescriptionWithoutIds() {
        details(withMixed())
        composeRule.onNodeWithContentDescription("View Maya Okafor’s profile").assertExists()
        composeRule.onNodeWithText("Tue · pending, Thu · accepted").assertExists()
    }

    @Test fun requesterProfileShowsFilledFieldsOnly() {
        composeRule.setContent { ColPoolTheme { RequesterProfileContent(RequesterProfileState("maya", HostRequestsTestData.MAYA, true), DetailsTestData.OPTIONS, {}) } }
        composeRule.onNodeWithText("Requester").assertExists()
        composeRule.onNodeWithText("Maya Okafor").assertExists()
        composeRule.onNodeWithText("Junior").assertExists()
        composeRule.onNodeWithText("Nursing").assertExists()
        composeRule.onNodeWithText("Early classes, quiet rides.").assertExists()
        composeRule.onNodeWithText("Gender").assertDoesNotExist()
        composeRule.onNodeWithText("Not provided").assertDoesNotExist()
    }

    @Test fun unreadableProfileShowsTheFallback() {
        composeRule.setContent { ColPoolTheme { RequesterProfileContent(RequesterProfileState("maya", null, true), DetailsTestData.OPTIONS, {}) } }
        composeRule.onNodeWithTag(RequesterTags.Unavailable).assertTextEquals("This profile is no longer available.")
        composeRule.onNodeWithTag(RequesterTags.Card).assertDoesNotExist()
    }

    @Test fun profileBackButtonCloses() {
        var back = 0
        composeRule.setContent { ColPoolTheme { RequesterProfileContent(RequesterProfileState("maya", null, true), DetailsTestData.OPTIONS, { back++ }) } }
        composeRule.onNodeWithTag(HostingTags.Back).performClick()
        assertEquals(1, back)
    }

    private fun shell(count: Int) = composeRule.setContent {
        ColPoolTheme { StudentShell(searchTab = {}, profileTab = {}, hostingTab = { Text("hosting") }, initialTab = 1, hostingBadge = count) }
    }

    @Test fun badgeShowsTheCountWithADescription() {
        shell(2)
        composeRule.onNodeWithTag(HOSTING_BADGE_TAG, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("2 requests with pending days", useUnmergedTree = true).assertExists()
    }

    @Test fun badgeUsesTheSingularForOne() {
        shell(1)
        composeRule.onNodeWithContentDescription("1 request with pending days", useUnmergedTree = true).assertExists()
    }

    @Test fun noBadgeWithoutPendingRequests() {
        shell(0)
        composeRule.onNodeWithTag(HOSTING_BADGE_TAG, useUnmergedTree = true).assertDoesNotExist()
    }
}
