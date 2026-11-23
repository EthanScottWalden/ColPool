package io.github.ethanscottwalden.colpool.ui.series

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsDisplayed
import io.github.ethanscottwalden.colpool.ui.series.SeriesTestData.TODAY
import io.github.ethanscottwalden.colpool.ui.theme.ColPoolTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** UI checks of the limits the screen enforces. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h2100dp-xxhdpi", application = Application::class)
class CreateSeriesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(state: CreateSeriesUiState, actions: CreateSeriesActions = CreateSeriesActions()) =
        composeRule.setContent { ColPoolTheme { CreateSeriesContent(state, TODAY, actions) } }

    @Test fun eleventhStopCannotBeAdded() {
        val full = SeriesTestData.filled().let {
            val wed = it.day(SeriesWeekday.Wed)
            it.copy(days = it.days + (SeriesWeekday.Wed to wed.withLeg(Direction.ToCampus, wed.toCampus.copy(stops = List(SeriesLimits.MAX_STOPS_PER_DIRECTION) { SeriesTestData.stop() }))))
        }
        show(full)
        composeRule.onNodeWithTag(SeriesTags.addStop(Direction.ToCampus)).assertDoesNotExist()
        composeRule.onNodeWithTag(SeriesTags.addStop(Direction.FromCampus)).assertExists()
    }

    @Test fun ridersStepperStopsAtOneAndSix() {
        show(SeriesTestData.filled().copy(riders = 1))
        composeRule.onNodeWithTag(SeriesTags.RidersLess).assertIsNotEnabled()
        composeRule.onNodeWithTag(SeriesTags.RidersMore).assertIsEnabled()
    }

    @Test fun ridersMaxDisablesMore() {
        show(SeriesTestData.filled().copy(riders = 6))
        composeRule.onNodeWithTag(SeriesTags.RidersMore).assertIsNotEnabled()
    }

    @Test fun onlyMondayToFridayChipsExist() {
        show(SeriesTestData.filled())
        SeriesWeekday.entries.forEach { composeRule.onNodeWithTag(SeriesTags.weekday(it)).assertExists() }
        assertEquals(5, SeriesWeekday.entries.size)
    }

    @Test fun continueCallsTheAction() {
        var n = 0
        show(SeriesTestData.filled(), CreateSeriesActions(onContinue = { n++ }))
        composeRule.onNodeWithTag(SeriesTags.Continue).performScrollTo().performClick()
        assertEquals(1, n)
    }

    @Test fun wholeFormErrorsShowInTheBox() {
        show(SeriesTestData.filled().copy(errors = listOf(io.github.ethanscottwalden.colpool.data.series.SeriesError("unavailable", ""))))
        composeRule.onNodeWithTag(SeriesTags.ErrorBox).assertIsDisplayed()
    }
}
