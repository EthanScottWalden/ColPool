package io.github.ethanscottwalden.colpool.ui.details

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ethanscottwalden.colpool.data.requests.DayFailure
import io.github.ethanscottwalden.colpool.data.requests.DayFailureReason
import io.github.ethanscottwalden.colpool.data.requests.SendRequestResult
import io.github.ethanscottwalden.colpool.ui.search.FakeSearchBackend
import io.github.ethanscottwalden.colpool.ui.search.SearchActions
import io.github.ethanscottwalden.colpool.ui.search.SearchContent
import io.github.ethanscottwalden.colpool.ui.search.SearchTags
import io.github.ethanscottwalden.colpool.ui.search.SearchTestData
import io.github.ethanscottwalden.colpool.ui.search.SearchViewModel
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday
import io.github.ethanscottwalden.colpool.ui.theme.ColPoolTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h3600dp-xxhdpi", application = Application::class)
class DetailsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val searchBackend = FakeSearchBackend().apply {
        series = listOf(SearchTestData.near(), SearchTestData.last())
        names = mapOf("hostA" to "Ava", "hostD" to "Maya")
    }
    private val requests = FakeRequestBackend()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    /** Search plus Series Details, wired like ColPoolRoot. */
    @Composable
    private fun Wired(search: SearchViewModel, details: DetailsViewModel) {
        val s by search.state.collectAsStateWithLifecycle()
        LaunchedEffect(s.openedSeriesId) {
            val r = s.results.firstOrNull { it.series.id == s.openedSeriesId }
            if (r != null) details.open(r.series, r.distanceTenths, s.hostNames[r.series.hostId]) else details.reset()
        }
        if (s.openedSeriesId != null) SeriesDetailsScreen(details, onBack = search::close, onSent = search::requestSent)
        else SearchContent(s, SearchActions(search::toggle, search::open, search::retry, search::dismissRequestSent))
    }

    private fun openNear(): Pair<SearchViewModel, DetailsViewModel> {
        val search = SearchViewModel(searchBackend, SearchTestData.CLOCK)
        val details = DetailsViewModel(requests, SearchTestData.CLOCK)
        composeRule.setContent { ColPoolTheme { Wired(search, details) } }
        composeRule.onNodeWithTag(SearchTags.day(SeriesWeekday.Mon)).performClick()
        composeRule.onNodeWithTag(SearchTags.card("near")).performClick()
        return search to details
    }

    private fun pick(w: SeriesWeekday) = composeRule.onNodeWithTag(DetailsTags.pick(w)).performClick()

    @Test fun showsHostSummaryScheduleSafetyNoteAndNeverVerified() {
        openNear()
        composeRule.onNodeWithTag(DetailsTags.HostCard).assertIsDisplayed()
        composeRule.onNode(hasText("Ava Thompson") and hasAnyAncestor(hasTestTag(DetailsTags.HostCard))).assertExists()
        composeRule.onNodeWithText("Clinicals keep me busy, so I love a fun drive in.").assertExists()
        composeRule.onNodeWithText("$4 cash per round trip").assertExists()
        composeRule.onNodeWithText("Blue 2017 Honda Fit").assertExists()
        assertTrue(composeRule.onAllNodes(hasText("Kaiser Permanente Riverside, Magnolia Ave")).fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("To campus").assertExists()
        composeRule.onNodeWithText("Back to pickup spot").assertExists()
        composeRule.onNodeWithText("ColPool only checks hosts have a CBU email. It does not check driver’s licenses, insurance or vehicles.").assertExists()
        assertEquals(0, composeRule.onAllNodes(hasText("verified", substring = true, ignoreCase = true)).fetchSemanticsNodes().size)
        // Never the email or an id.
        assertEquals(0, composeRule.onAllNodes(hasText("@", substring = true)).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodes(hasText("hostA", substring = true)).fetchSemanticsNodes().size)
    }

    @Test fun minimalProfileShowsNoPlaceholders() {
        requests.profile = DetailsTestData.MINIMAL
        openNear()
        composeRule.onNode(hasText("Riley Chen") and hasAnyAncestor(hasTestTag(DetailsTags.HostCard))).assertExists()
        assertEquals(0, composeRule.onAllNodes(hasText("provided", substring = true, ignoreCase = true)).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodes(hasText("Interests")).fetchSemanticsNodes().size)
    }

    @Test fun pickingDaysEnablesTheButtonAndTheSheetListsThem() {
        openNear()
        composeRule.onNodeWithTag(DetailsTags.Request).assertIsNotEnabled()
        composeRule.onNodeWithTag(DetailsTags.pick(SeriesWeekday.Mon)).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        composeRule.onNodeWithTag(DetailsTags.pick(SeriesWeekday.Mon)).assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag(DetailsTags.Warning).assert(hasText("You can send only one request to this series. You can’t add days or ask again later, even if a day is declined."))
        pick(SeriesWeekday.Mon)
        pick(SeriesWeekday.Fri)
        composeRule.onNodeWithTag(DetailsTags.Request).assertIsEnabled().performClick()
        composeRule.onNodeWithTag(DetailsTags.Sheet).assertIsDisplayed()
        composeRule.onNodeWithText("Days: Monday, Friday").assertExists()
        composeRule.onNodeWithText("The host decides each day separately.").assertExists()
        // The warning shows again in the sheet, and the page behind it is hidden from accessibility (only the sheet's copy is exposed).
        composeRule.onNode(hasText("You can send only one request", substring = true) and hasAnyAncestor(hasTestTag(DetailsTags.Sheet))).assertExists()
        assertEquals(1, composeRule.onAllNodes(hasText("You can send only one request", substring = true)).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodes(hasTestTag(DetailsTags.Request)).fetchSemanticsNodes().size)
        composeRule.onNodeWithTag(DetailsTags.SheetCancel).performClick()
        assertEquals(0, composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(DetailsTags.Sheet)).fetchSemanticsNodes().size)
        assertTrue(requests.sends.isEmpty())
    }

    @Test fun fullAndNoTripsDaysAreDisabledWithReasons() {
        val details = DetailsViewModel(requests, SearchTestData.CLOCK)
        val s = DetailsTestData.mixed()
        details.open(s, 12, "Ava")
        composeRule.setContent { ColPoolTheme { val st by details.state.collectAsStateWithLifecycle(); DetailsContent(st, DetailsActions(onSelectDay = details::selectDay, onTogglePick = details::togglePick)) } }
        composeRule.onNodeWithTag(DetailsTags.pick(SeriesWeekday.Mon)).assertIsEnabled()
        composeRule.onNodeWithTag(DetailsTags.pick(SeriesWeekday.Wed)).assertIsNotEnabled().assert(hasText("Full"))
        composeRule.onNodeWithTag(DetailsTags.pick(SeriesWeekday.Fri)).assertIsNotEnabled().assert(hasText("No trips left"))
        composeRule.onNodeWithTag(DetailsTags.pick(SeriesWeekday.Tue)).assertIsNotEnabled().assert(hasText("—"))
        composeRule.onNodeWithTag(DetailsTags.tab(SeriesWeekday.Wed)).assert(hasText("full"))
        composeRule.onNodeWithTag(DetailsTags.tab(SeriesWeekday.Fri)).assert(hasText("no trips left"))
        composeRule.onNodeWithTag(DetailsTags.tab(SeriesWeekday.Mon)).assert(hasText("2 open"))
        composeRule.onNodeWithTag(DetailsTags.tab(SeriesWeekday.Mon)).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        // Switching the schedule day changes the tab, not the picks.
        composeRule.onNodeWithTag(DetailsTags.tab(SeriesWeekday.Wed)).performClick()
        assertEquals(SeriesWeekday.Wed, details.state.value.activeDay)
    }

    @Test fun successReturnsToSearchWithTheConfirmationAndTheSeriesIsGone() {
        val (search, _) = openNear()
        pick(SeriesWeekday.Wed)
        composeRule.onNodeWithTag(DetailsTags.Request).performClick()
        // The server will list the request from now on.
        searchBackend.requested = setOf("near")
        composeRule.onNodeWithTag(DetailsTags.SheetSend).performClick()
        assertEquals(listOf("near" to listOf("wed")), requests.sends)
        composeRule.onNodeWithTag(SearchTags.Sent).assertIsDisplayed()
        composeRule.onNodeWithText("Request sent").assertIsDisplayed()
        assertEquals(null, search.state.value.openedSeriesId)
        assertEquals(listOf("last"), search.state.value.results.map { it.series.id })
    }

    @Test fun perDayErrorMarksTheDaysAndKeepsTheOthers() {
        openNear()
        pick(SeriesWeekday.Mon); pick(SeriesWeekday.Wed); pick(SeriesWeekday.Fri)
        requests.result = SendRequestResult.WeekdaysUnavailable(listOf(DayFailure("mon", DayFailureReason.Full), DayFailure("wed", DayFailureReason.Conflict)))
        composeRule.onNodeWithTag(DetailsTags.Request).performClick()
        composeRule.onNodeWithTag(DetailsTags.SheetSend).performClick()
        composeRule.onNodeWithTag(DetailsTags.Error).assertIsDisplayed()
        composeRule.onNodeWithText("Monday: Full").assertExists()
        composeRule.onNodeWithText("Wednesday: Conflicts with a series you ride or host", substring = false).assertExists()
        composeRule.onNodeWithTag(DetailsTags.pick(SeriesWeekday.Mon)).assertIsNotEnabled()
        composeRule.onNodeWithTag(DetailsTags.Request).assertIsEnabled()
        requests.result = SendRequestResult.Sent
        composeRule.onNodeWithTag(DetailsTags.Request).performClick()
        composeRule.onNodeWithText("Days: Friday").assertExists()
    }

    @Test fun alreadyRequestedReplacesThePicker() {
        requests.requested = true
        openNear()
        composeRule.onNodeWithTag(DetailsTags.Already).assertIsDisplayed()
        composeRule.onNodeWithText("You’ve already requested this series.").assertExists()
        assertEquals(0, composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(DetailsTags.Request)).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodes(hasText("Send request")).fetchSemanticsNodes().size)
    }

    @Test fun ownSeriesShowsDetailsWithoutThePicker() {
        requests.uid = "hostA"
        openNear()
        composeRule.onNodeWithTag(DetailsTags.HostCard).assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(DetailsTags.Request)).fetchSemanticsNodes().size)
        composeRule.onNodeWithText("You can’t request your own series.").assertExists()
    }

    @Test fun networkErrorShowsTheRetryMessageAndKeepsThePicks() {
        openNear()
        pick(SeriesWeekday.Mon)
        requests.result = SendRequestResult.Failed
        composeRule.onNodeWithTag(DetailsTags.Request).performClick()
        composeRule.onNodeWithTag(DetailsTags.SheetSend).performClick()
        composeRule.onNodeWithText("Couldn’t send your request. Check your connection and try again.").assertIsDisplayed()
        composeRule.onNodeWithTag(DetailsTags.Request).assertIsEnabled()
    }

    @Test fun backReturnsToSearch() {
        val (search, _) = openNear()
        composeRule.onNodeWithTag(io.github.ethanscottwalden.colpool.ui.hosting.HostingTags.Back).performClick()
        assertEquals(null, search.state.value.openedSeriesId)
        composeRule.onNodeWithTag(SearchTags.Count).assertIsDisplayed()
    }
}
