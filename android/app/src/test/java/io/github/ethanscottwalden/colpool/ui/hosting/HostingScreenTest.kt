package io.github.ethanscottwalden.colpool.ui.hosting

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
@Config(sdk = [36], qualifiers = "w360dp-h2100dp-xxhdpi", application = Application::class)
class HostingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun list(state: HostingUiState, actions: HostingActions = HostingActions()) =
        composeRule.setContent { ColPoolTheme { HostingListContent(state, actions) } }

    private fun details(state: HostingUiState, actions: HostingActions = HostingActions()) =
        composeRule.setContent { ColPoolTheme { HostedSeriesDetailsContent(state, actions) } }

    @Test fun listRendersRows() {
        val other = HostingTestData.series("s2", HostingTestData.doc(weekdays = listOf("mon", "wed"), start = "2026-12-08", end = "2027-01-28", pickup = "Mission Grove Plaza, Riverside, CA", price = 5, max = 2))
        list(HostingTestData.state(listOf(HostingTestData.series("s1"), other)))
        composeRule.onNodeWithText("Host a ride series").assertIsDisplayed()
        composeRule.onNodeWithText("Series you’re hosting").assertExists()
        composeRule.onNodeWithText("Tue, Thu").assertExists()
        composeRule.onNodeWithText("Dec 1 – Jan 28, 2027 · \$4 per round trip").assertExists()
        composeRule.onNodeWithText("Tue 2 of 3 seats open · Thu 3 of 3 seats open").assertExists()
        composeRule.onNodeWithText("Mon 2 of 2 seats open · Wed 2 of 2 seats open").assertExists()
    }

    @Test fun emptyStateKeepsTheHostEntry() {
        var hosted = 0
        list(HostingTestData.state(emptyList()), HostingActions(onHostSeries = { hosted++ }))
        composeRule.onNodeWithTag(HostingTags.Empty).assertIsDisplayed()
        composeRule.onNodeWithText("You’re not hosting a series").assertExists()
        composeRule.onNodeWithTag(HostingTags.HostButton).performClick()
        assertEquals(1, hosted)
    }

    @Test fun failedShowsRetry() {
        var retried = 0
        list(HostingTestData.state(emptyList(), load = HostingLoad.Failed), HostingActions(onRetry = { retried++ }))
        composeRule.onNodeWithTag(HostingTags.Retry).performClick()
        assertEquals(1, retried)
    }

    @Test fun tappingARowOpensIt() {
        var opened: String? = null
        list(HostingTestData.state(), HostingActions(onOpen = { opened = it }))
        composeRule.onNodeWithTag(HostingTags.row("s1")).performClick()
        assertEquals("s1", opened)
    }

    @Test fun detailsShowSummaryTimelinesAndEmptySections() {
        details(HostingTestData.state(selectedId = "s1", activeDay = SeriesWeekday.Tue))
        composeRule.onNodeWithText("Tue, Thu series").assertExists()
        composeRule.onNodeWithText("Blue 2017 Honda Fit").assertExists()
        composeRule.onNodeWithText("2 open").assertExists()
        composeRule.onNodeWithText("3 open").assertExists()
        composeRule.onNodeWithTag(HostingTags.Badge).assertTextContains("2 of 3 seats open")
        composeRule.onNodeWithText("7:30 AM").assertExists()
        composeRule.onNodeWithText("Stop · 5 min · leave 8:06 AM").assertExists()
        composeRule.onNodeWithText("No riders yet").assertExists()
        composeRule.onNodeWithText("No requests yet").performScrollTo().assertIsDisplayed()
    }

    @Test fun dayTabsSwitchAndUnusedDaysAreDisabled() {
        var picked: SeriesWeekday? = null
        details(HostingTestData.state(selectedId = "s1", activeDay = SeriesWeekday.Tue), HostingActions(onSelectDay = { picked = it }))
        composeRule.onNodeWithTag(HostingTags.day(SeriesWeekday.Mon)).assertIsNotEnabled()
        composeRule.onNodeWithTag(HostingTags.day(SeriesWeekday.Thu)).performClick()
        assertEquals(SeriesWeekday.Thu, picked)
    }

    @Test fun backIsReported() {
        var back = 0
        details(HostingTestData.state(selectedId = "s1", activeDay = SeriesWeekday.Tue), HostingActions(onBack = { back++ }))
        composeRule.onNodeWithTag(HostingTags.Back).performClick()
        assertEquals(1, back)
    }
}
