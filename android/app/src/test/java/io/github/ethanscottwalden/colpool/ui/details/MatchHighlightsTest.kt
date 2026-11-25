package io.github.ethanscottwalden.colpool.ui.details

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.ethanscottwalden.colpool.data.requests.HostProfile
import io.github.ethanscottwalden.colpool.ui.search.MatchPreferences
import io.github.ethanscottwalden.colpool.ui.search.SearchTestData
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday.Mon
import io.github.ethanscottwalden.colpool.ui.theme.ColPoolTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Host card highlights for matched preferences (#37). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h2100dp-xxhdpi", application = Application::class)
class MatchHighlightsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val matches = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "matches your preference")

    private fun fields(prefs: MatchPreferences, p: HostProfile = DetailsTestData.AVA) = DetailsRules.hostFields(p, DetailsTestData.OPTIONS, prefs)

    private fun show(prefs: MatchPreferences) {
        val s = DetailsTestData.mixed()
        val st = DetailsUiState(
            series = s, distanceTenths = 41, firstNameHint = "Ava", profile = DetailsTestData.AVA, profileLoaded = true, prefs = prefs,
            options = DetailsTestData.OPTIONS, currentYear = 2026, activeDay = Mon,
            dayStates = DetailsRules.dayStates(s, SearchTestData.NOW),
        )
        composeRule.setContent { ColPoolTheme { DetailsContent(st, DetailsActions()) } }
    }

    private fun highlighted() = composeRule.onAllNodes(matches and hasAnyAncestor(hasTestTag(DetailsTags.HostCard))).fetchSemanticsNodes().size

    @Test fun nothingIsHighlightedWithoutPreferences() {
        show(MatchPreferences())
        assertEquals(0, highlighted())
        composeRule.onNode(hasText("Music") and hasAnyAncestor(hasTestTag(DetailsTags.HostCard))).assertExists()
    }

    @Test fun matchedInterestsTraitsMajorAndExactYearAreHighlighted() {
        show(MatchPreferences(setOf("music", "gaming"), setOf("chatty"), "nursing", "junior"))
        // music, chatty, nursing, junior. Gaming is not the host's, so not shown at all.
        assertEquals(4, highlighted())
        composeRule.onNode(matches and hasText("✓ Music", substring = true)).assertExists()
        composeRule.onNode(hasText("Coffee") and hasAnyAncestor(hasTestTag(DetailsTags.HostCard))).assertExists()
    }

    @Test fun partialYearCreditIsNotHighlighted() {
        show(MatchPreferences(year = "senior"))
        assertEquals(0, highlighted())
    }

    @Test fun fieldsMarkOnlyExactIds() {
        val f = fields(MatchPreferences(setOf("music"), setOf("quiet"), "biology", "senior"))
        assertTrue(f.filterIsInstance<HostField.Text>().none { it.matched })
        val chips = f.filterIsInstance<HostField.Chips>()
        assertEquals(setOf("Music"), chips[0].matched)
        assertEquals(emptySet<String>(), chips[1].matched)
    }
}
