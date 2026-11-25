package io.github.ethanscottwalden.colpool.ui.search

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ethanscottwalden.colpool.data.search.HostMatchFields
import io.github.ethanscottwalden.colpool.data.search.InMemoryPreferenceStore
import io.github.ethanscottwalden.colpool.ui.profile.SeededOptions
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
@Config(sdk = [36], qualifiers = "w360dp-h4000dp-xxhdpi", application = Application::class)
class PreferencesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val backend = FakeSearchBackend().apply {
        series = listOf(SearchTestData.far(), SearchTestData.mid(), SearchTestData.near(), SearchTestData.last())
        names = mapOf("hostA" to "Ava", "hostB" to "Riley", "hostC" to "Jamal", "hostD" to "Maya")
        matchFields = mapOf(
            "hostA" to HostMatchFields("Ava", "junior", "nursing", listOf("music", "coffee"), listOf("chatty")),
            "hostD" to HostMatchFields("Maya", null, null, listOf("gaming"), emptyList()),
        )
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Composable
    private fun Wired(vm: SearchViewModel) {
        val s by vm.state.collectAsStateWithLifecycle()
        SearchContent(
            s,
            SearchActions(
                vm::toggle, vm::open, vm::retry, vm::dismissRequestSent, vm::openPanel, vm::closePanel,
                vm::toggleInterest, vm::toggleTrait, vm::toggleMajor, vm::toggleYear, vm::clearPrefs, vm::retryOptions,
            ),
        )
    }

    private fun show(): SearchViewModel {
        val vm = SearchViewModel(backend, SearchTestData.CLOCK, InMemoryPreferenceStore())
        composeRule.setContent { ColPoolTheme { Wired(vm) } }
        return vm
    }

    private fun count() = composeRule.onAllNodes(hasText("% match", substring = true)).fetchSemanticsNodes().size

    @Test fun panelOpensAndClosesWithDoneAndShowsTheIntro() {
        show()
        composeRule.onNodeWithTag(SearchTags.PrefsRow).assert(hasText("Host preferences (0)"))
        assertEquals(0, composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(SearchTags.PrefsPanel)).fetchSemanticsNodes().size)
        composeRule.onNodeWithTag(SearchTags.PrefsRow).performClick()
        composeRule.onNodeWithTag(SearchTags.PrefsPanel).assertIsDisplayed()
        composeRule.onNode(hasText("Pick what you’d like in a host. You’ll see a match percentage on each ride, but distance still decides the order.")).assertExists()
        composeRule.onNodeWithTag(SearchTags.PrefsDone).performClick()
        assertEquals(0, composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(SearchTags.PrefsPanel)).fetchSemanticsNodes().size)
        composeRule.onNodeWithTag(SearchTags.PrefsRow).performClick()
        composeRule.onNodeWithTag(SearchTags.PrefsRow).performClick()
        assertEquals(0, composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(SearchTags.PrefsPanel)).fetchSemanticsNodes().size)
    }

    @Test fun countInTheRowFollowsChoicesAndClearResetsIt() {
        show()
        composeRule.onNodeWithTag(SearchTags.PrefsRow).performClick()
        composeRule.onNodeWithTag(SearchTags.interest("music")).performClick()
        composeRule.onNodeWithTag(SearchTags.trait("chatty")).performClick()
        composeRule.onNodeWithTag(SearchTags.PrefsMajorFilter).performScrollTo().performTextInput("nurs")
        composeRule.onNodeWithTag(SearchTags.major("nursing")).performScrollTo().performClick()
        composeRule.onNodeWithTag(SearchTags.year("junior")).performClick()
        composeRule.onNodeWithTag(SearchTags.PrefsRow).assert(hasText("Host preferences (4)"))
        // Picking another year replaces it; the same one again clears it.
        composeRule.onNodeWithTag(SearchTags.year("senior")).performClick()
        composeRule.onNodeWithTag(SearchTags.PrefsRow).assert(hasText("Host preferences (4)"))
        composeRule.onNodeWithTag(SearchTags.year("senior")).performClick()
        composeRule.onNodeWithTag(SearchTags.PrefsRow).assert(hasText("Host preferences (3)"))
        composeRule.onNodeWithTag(SearchTags.PrefsClear).performClick()
        composeRule.onNodeWithTag(SearchTags.PrefsRow).assert(hasText("Host preferences (0)"))
    }

    @Test fun majorPickerFilters() {
        show()
        composeRule.onNodeWithTag(SearchTags.PrefsRow).performClick()
        composeRule.onNodeWithTag(SearchTags.PrefsMajorFilter).performTextInput("nurs")
        composeRule.onNodeWithTag(SearchTags.major("nursing")).assertExists()
        assertEquals(0, composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(SearchTags.major("accounting"))).fetchSemanticsNodes().size)
    }

    private fun exists(tag: String) = composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    @Test fun majorListIsHiddenUntilTypingAndBoundedWithAHint() {
        show()
        composeRule.onNodeWithTag(SearchTags.PrefsRow).performClick()
        assertEquals(false, exists(SearchTags.PrefsMajorMatches))
        assertEquals(false, exists(SearchTags.major("nursing")))
        // "n" matches many majors: only 5 show, with a hint counting the rest.
        composeRule.onNodeWithTag(SearchTags.PrefsMajorFilter).performTextInput("n")
        val rows = composeRule.onAllNodes(SemanticsMatcher("a major row") { n ->
            val t = if (SemanticsProperties.TestTag in n.config) n.config[SemanticsProperties.TestTag] else null
            t != null && t.startsWith("search_pref_major_") && t.removePrefix("search_pref_major_") in SeededOptions.ALL.majors.map { it.id }
        }).fetchSemanticsNodes().size
        assertEquals(MAJOR_MATCHES_SHOWN, rows)
        composeRule.onNodeWithTag(SearchTags.PrefsMajorMore).assert(hasText("Keep typing to narrow (", substring = true))
        composeRule.onNodeWithTag(SearchTags.PrefsMajorFilter).performTextInput("ursing pr")
        assertEquals(false, exists(SearchTags.PrefsMajorMore))
    }

    @Test fun noMatchSaysSo() {
        show()
        composeRule.onNodeWithTag(SearchTags.PrefsRow).performClick()
        composeRule.onNodeWithTag(SearchTags.PrefsMajorFilter).performTextInput("zzzz")
        composeRule.onNodeWithText("No majors match.").assertExists()
    }

    @Test fun chosenMajorShowsAndCanBeCleared() {
        val vm = show()
        composeRule.onNodeWithTag(SearchTags.PrefsRow).performClick()
        composeRule.onNodeWithTag(SearchTags.PrefsMajorFilter).performTextInput("nursing pr")
        composeRule.onNodeWithTag(SearchTags.major("nursing-practice")).performScrollTo().performClick()
        composeRule.onNodeWithTag(SearchTags.PrefsMajorChosen).assert(hasText("Chosen: Nursing Practice"))
        composeRule.onNodeWithTag(SearchTags.PrefsMajorClear).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(null, vm.state.value.prefs.major)
        assertEquals(false, exists(SearchTags.PrefsMajorChosen))
    }

    @Test fun badgeAppearsOnlyWithPreferencesAndOrderStays() {
        show()
        composeRule.onNodeWithTag(SearchTags.day(SeriesWeekday.Mon)).performClick()
        assertEquals(0, count())
        val before = cardOrder()
        composeRule.onNodeWithTag(SearchTags.PrefsRow).performClick()
        composeRule.onNodeWithTag(SearchTags.interest("music")).performClick()
        composeRule.onNodeWithTag(SearchTags.badge("near")).assert(hasText("100% match"))
        composeRule.onNodeWithTag(SearchTags.badge("near")).assert(hasContentDescription("100 percent match"))
        composeRule.onNodeWithTag(SearchTags.badge("last")).assert(hasText("0% match"))
        assertEquals(2, count())
        assertEquals(before, cardOrder())
        composeRule.onNodeWithTag(SearchTags.PrefsClear).performClick()
        assertEquals(0, count())
        assertEquals(before, cardOrder())
    }

    private fun cardOrder(): List<String> = listOf("near", "last").sortedBy { id ->
        composeRule.onNodeWithTag(SearchTags.card(id)).fetchSemanticsNode().boundsInRoot.top
    }

    @Test fun chipsAndPanelButtonsAreCheckboxesAtLeast48dp() {
        show()
        composeRule.onNodeWithTag(SearchTags.PrefsRow).performClick()
        composeRule.onNodeWithTag(SearchTags.PrefsMajorFilter).performTextInput("nurs")
        listOf(SearchTags.interest("music"), SearchTags.trait("chatty"), SearchTags.year("junior"), SearchTags.major("nursing")).forEach {
            composeRule.onNodeWithTag(it).assertHeightIsAtLeast(48.dp).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        }
        composeRule.onNodeWithTag(SearchTags.PrefsClear).assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag(SearchTags.PrefsDone).assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag(SearchTags.PrefsRow).assertHeightIsAtLeast(48.dp)
    }

    @Test fun optionsFailureOffersRetry() {
        backend.optionsError = RuntimeException("offline")
        show()
        composeRule.onNodeWithTag(SearchTags.PrefsRow).performClick()
        composeRule.onNode(hasText("Couldn’t load the choices. Check your connection and try again.")).assertExists()
        backend.optionsError = null
        composeRule.onNodeWithTag(SearchTags.PrefsRetry).performClick()
        composeRule.onNodeWithTag(SearchTags.interest("music")).assertExists()
        assertTrue(composeRule.onAllNodes(hasText("Couldn’t load the choices", substring = true)).fetchSemanticsNodes().isEmpty())
    }
}
