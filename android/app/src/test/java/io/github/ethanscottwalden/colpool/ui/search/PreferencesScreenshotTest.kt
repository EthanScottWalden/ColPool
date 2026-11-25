package io.github.ethanscottwalden.colpool.ui.search

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.ethanscottwalden.colpool.data.search.HostMatchFields
import io.github.ethanscottwalden.colpool.ui.details.DetailsActions
import io.github.ethanscottwalden.colpool.ui.details.DetailsContent
import io.github.ethanscottwalden.colpool.ui.details.DetailsRules
import io.github.ethanscottwalden.colpool.ui.details.DetailsTestData
import io.github.ethanscottwalden.colpool.ui.details.DetailsUiState
import io.github.ethanscottwalden.colpool.ui.profile.SeededOptions
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday
import io.github.ethanscottwalden.colpool.ui.theme.ColPoolTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshot tests of the preference panel, match badges and host card highlights (#37). Made-up data only. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h2400dp-xxhdpi", application = Application::class)
class PreferencesScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun shoot(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { ColPoolTheme { content() } }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private val names = mapOf("hostA" to "Ava", "hostB" to "Riley", "hostC" to "Jamal", "hostD" to "Maya")
    private val fields = mapOf(
        "hostA" to HostMatchFields("Ava", "junior", "nursing", listOf("music", "coffee", "hiking"), listOf("chatty", "music-friendly")),
        "hostB" to HostMatchFields("Riley", "freshman", "biology", listOf("gaming"), listOf("quiet")),
        "hostC" to null,
        "hostD" to HostMatchFields("Maya", "senior", "nursing", listOf("music"), emptyList()),
    )
    private val prefs = MatchPreferences(setOf("music", "coffee"), setOf("chatty"), "nursing", "junior")

    private fun state(panel: Boolean): SearchUiState {
        val picked = setOf(SeriesWeekday.Mon, SeriesWeekday.Tue, SeriesWeekday.Thu)
        val series = listOf(SearchTestData.far(), SearchTestData.mid(), SearchTestData.near(), SearchTestData.last())
        val results = SearchRules.rank(series, picked, SearchTestData.NOW, "me", emptySet(), SearchTestData.RIDER)
        return SearchUiState(
            picked = picked, load = SearchLoad.Loaded, results = results, hostNames = names, currentYear = 2026,
            prefs = prefs, panelOpen = panel, options = SeededOptions.ALL, hostFields = fields,
        )
    }

    @Config(sdk = [36], qualifiers = "w360dp-h3000dp-xxhdpi", application = Application::class)
    @Test fun panel() = shoot("search_preferences_panel") { SearchContent(state(panel = true), SearchActions()) }

    @Config(sdk = [36], qualifiers = "w360dp-h1800dp-xxhdpi", application = Application::class)
    @Test fun results() = shoot("search_results_match") { SearchContent(state(panel = false), SearchActions()) }

    @Config(sdk = [36], qualifiers = "w360dp-h2100dp-xxhdpi", application = Application::class)
    @Test fun detailsHighlights() = shoot("series_details_match_highlights") {
        val s = DetailsTestData.mixed()
        DetailsContent(
            DetailsUiState(
                series = s, distanceTenths = 41, firstNameHint = "Ava", profile = DetailsTestData.AVA, profileLoaded = true, prefs = prefs,
                options = DetailsTestData.OPTIONS, campusNames = mapOf("yeager-center" to "Yeager Center", "lancer-plaza" to "Lancer Plaza"),
                currentYear = 2026, activeDay = SeriesWeekday.Mon, dayStates = DetailsRules.dayStates(s, SearchTestData.NOW), picked = setOf(SeriesWeekday.Mon),
            ),
            DetailsActions(),
        )
    }
}
