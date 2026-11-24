package io.github.ethanscottwalden.colpool.ui.search

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
@Config(sdk = [36], qualifiers = "w360dp-h2400dp-xxhdpi", application = Application::class)
class SearchScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val backend = FakeSearchBackend().apply {
        series = listOf(SearchTestData.far(), SearchTestData.mid(), SearchTestData.near(), SearchTestData.last())
        names = mapOf("hostA" to "Ava", "hostB" to "Riley", "hostC" to "Jamal", "hostD" to "Maya")
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    /** Search plus the details placeholder, wired like ColPoolRoot. */
    @Composable
    private fun Wired(vm: SearchViewModel) {
        val state by vm.state.collectAsStateWithLifecycle()
        val opened = state.openedSeriesId
        // Series Details has its own tests (DetailsScreenTest); here the opened id just hides the list.
        if (opened != null) androidx.compose.material3.Text("details open", androidx.compose.ui.Modifier.testTag("details_stub"))
        else SearchContent(state, SearchActions(vm::toggle, vm::open, vm::retry, vm::dismissRequestSent))
    }

    private fun show(vm: SearchViewModel = SearchViewModel(backend, SearchTestData.CLOCK)): SearchViewModel {
        composeRule.setContent { ColPoolTheme { Wired(vm) } }
        return vm
    }

    @Test fun promptsToPickDaysFirst() {
        show()
        composeRule.onNodeWithText("Find a ride").assertIsDisplayed()
        composeRule.onNodeWithText("Days you need a ride").assertIsDisplayed()
        composeRule.onNodeWithTag(SearchTags.Pick).assertIsDisplayed()
        composeRule.onNodeWithText("Pick the days you need a ride.").assertExists()
    }

    @Test fun pickingDaysShowsRankedCardsAndUnpickingClearsThem() {
        show()
        composeRule.onNodeWithTag(SearchTags.day(SeriesWeekday.Mon)).performClick()
        composeRule.onNodeWithText("From your starting address · Nearest first").assertExists()
        composeRule.onNodeWithTag(SearchTags.Count).assert(hasText("2 series near you"))
        composeRule.onNodeWithText("Hosted by Ava").assertExists()
        composeRule.onNodeWithText("Hosted by Maya").assertExists()
        composeRule.onNodeWithText("Kaiser Permanente Riverside, Magnolia Ave").assertExists()
        composeRule.onNodeWithText("Monday: leaves 7:30 AM, at campus ~7:57 AM, leaves campus 3:30 PM").assertExists()
        composeRule.onNodeWithText("Series you’ve already requested don’t appear. Times are Pacific Time.").assertExists()

        composeRule.onNodeWithTag(SearchTags.day(SeriesWeekday.Thu)).performClick()
        composeRule.onNodeWithTag(SearchTags.Count).assert(hasText("4 series near you"))
        composeRule.onNodeWithText("Hosted by Riley").assertExists()

        composeRule.onNodeWithTag(SearchTags.day(SeriesWeekday.Mon)).performClick()
        composeRule.onNodeWithTag(SearchTags.Count).assert(hasText("2 series near you"))
        composeRule.onNodeWithTag(SearchTags.day(SeriesWeekday.Thu)).performClick()
        composeRule.onNodeWithTag(SearchTags.Pick).assertIsDisplayed()
    }

    @Test fun oneResultSaysSeries() {
        backend.series = listOf(SearchTestData.near())
        show()
        composeRule.onNodeWithTag(SearchTags.day(SeriesWeekday.Wed)).performClick()
        composeRule.onNodeWithTag(SearchTags.Count).assert(hasText("1 series near you"))
    }

    @Test fun noResultsShowsTheEmptyState() {
        backend.series = emptyList()
        show()
        composeRule.onNodeWithTag(SearchTags.day(SeriesWeekday.Fri)).performClick()
        composeRule.onNodeWithTag(SearchTags.Empty).assertIsDisplayed()
        composeRule.onNodeWithText("No series have an open seat on those days. Try other days.").assertExists()
    }

    @Test fun errorShowsRetry() {
        backend.searchError = RuntimeException("offline")
        show()
        composeRule.onNodeWithTag(SearchTags.day(SeriesWeekday.Mon)).performClick()
        composeRule.onNodeWithText("Couldn’t load series. Check your connection and try again.").assertIsDisplayed()
        backend.searchError = null
        composeRule.onNodeWithTag(SearchTags.Retry).performClick()
        composeRule.onNodeWithTag(SearchTags.Count).assertIsDisplayed()
    }

    @Test fun missingStartingAddressShowsNoticeWithoutDistances() {
        backend.start = null
        show()
        composeRule.onNodeWithTag(SearchTags.day(SeriesWeekday.Mon)).performClick()
        composeRule.onNodeWithTag(SearchTags.NoAddress).assertIsDisplayed()
        composeRule.onNodeWithText("Add your starting address to see distances").assertExists()
        composeRule.onAllNodesWithTextCount("mi away", 0)
    }

    @Test fun chipsShowEachState() {
        show()
        composeRule.onNodeWithTag(SearchTags.day(SeriesWeekday.Mon)).performClick()
        composeRule.onNodeWithTag(SearchTags.day(SeriesWeekday.Tue)).performClick()
        // mid: Mon full, Tue open, Thu not needed; far (Tue has no trips left) does not qualify via Tue but Thu is not picked.
        composeRule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Tuesday, open")).fetchSemanticsNodes().isNotEmpty().let(::assertTrue)
        composeRule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Monday, full")).fetchSemanticsNodes().isNotEmpty().let(::assertTrue)
        composeRule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Thursday, not needed")).fetchSemanticsNodes().isNotEmpty().let(::assertTrue)
        composeRule.onAllNodesWithTextCount("full", 1)
        assertTrue(composeRule.onAllNodes(hasText("not needed")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodes(hasText("open")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun cardTapOpensDetailsAndRequestSentShowsTheBannerAndRefreshes() {
        val vm = show()
        composeRule.onNodeWithTag(SearchTags.day(SeriesWeekday.Mon)).performClick()
        composeRule.onNodeWithTag(SearchTags.card("near")).performClick()
        assertEquals("near", vm.state.value.openedSeriesId)
        composeRule.onNodeWithTag("details_stub").assertIsDisplayed()
        // The request went through: the server now lists it, so the series leaves the results (RC-16).
        backend.requested = setOf("near")
        vm.requestSent()
        composeRule.onNodeWithTag(SearchTags.Sent).assertIsDisplayed()
        composeRule.onNodeWithText("Request sent").assertIsDisplayed()
        composeRule.onNodeWithTag(SearchTags.Count).assert(hasText("1 series near you"))
        composeRule.onAllNodesWithTextCount("Hosted by Ava", 0)
        composeRule.onNodeWithTag(SearchTags.SentDismiss).performClick()
        composeRule.onAllNodesWithTextCount("Request sent", 0)
    }

    @Test fun dayChipsAreCheckboxesAtLeast48dp() {
        show()
        composeRule.onNodeWithTag(SearchTags.day(SeriesWeekday.Mon)).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        composeRule.onNodeWithTag(SearchTags.day(SeriesWeekday.Mon)).performClick()
        composeRule.onNodeWithTag(SearchTags.day(SeriesWeekday.Mon)).assert(hasText("needed", substring = true))
        composeRule.onNodeWithTag(SearchTags.day(SeriesWeekday.Mon)).assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, androidx.compose.ui.state.ToggleableState.On))
        composeRule.onNodeWithTag(SearchTags.day(SeriesWeekday.Mon)).assertHeightIsAtLeast(48.dp)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String, n: Int) {
        assertEquals(n, onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().size)
    }
}
