package io.github.ethanscottwalden.colpool.ui.search

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

/** Screenshot tests of Search. Made-up data only. Goldens: app/src/test/screenshots/search_*.png */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi", application = Application::class)
class SearchScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun shoot(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { ColPoolTheme { content() } }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private val names = mapOf("hostA" to "Ava", "hostB" to "Riley", "hostC" to "Jamal", "hostD" to "Maya")

    private fun state(picked: Set<SeriesWeekday>, series: List<io.github.ethanscottwalden.colpool.data.series.HostedSeries>, load: SearchLoad = SearchLoad.Loaded): SearchUiState {
        val results = SearchRules.rank(series, picked, SearchTestData.NOW, "me", emptySet(), SearchTestData.RIDER)
        return SearchUiState(picked = picked, load = load, results = results, hostNames = names, currentYear = 2026)
    }

    @Test fun pickDays() = shoot("search_pick_days") { SearchContent(SearchUiState(currentYear = 2026), SearchActions()) }

    @Config(sdk = [36], qualifiers = "w360dp-h1750dp-xxhdpi", application = Application::class)
    @Test fun results() = shoot("search_results") {
        val picked = setOf(SeriesWeekday.Mon, SeriesWeekday.Tue, SeriesWeekday.Thu)
        SearchContent(state(picked, listOf(SearchTestData.far(), SearchTestData.mid(), SearchTestData.near(), SearchTestData.last())), SearchActions())
    }

    @Test fun noResults() = shoot("search_no_results") {
        SearchContent(state(setOf(SeriesWeekday.Fri), emptyList()), SearchActions())
    }
}
