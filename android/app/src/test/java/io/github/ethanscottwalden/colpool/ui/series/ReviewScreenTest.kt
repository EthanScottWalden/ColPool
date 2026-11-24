package io.github.ethanscottwalden.colpool.ui.series

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.ethanscottwalden.colpool.data.series.SeriesError
import io.github.ethanscottwalden.colpool.ui.theme.ColPoolTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** UI checks of Review: what it shows, and the buttons. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h2100dp-xxhdpi", application = Application::class)
class ReviewScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(state: ReviewUiState, actions: ReviewActions = ReviewActions()) =
        composeRule.setContent { ColPoolTheme { ReviewContent(state, actions) } }

    @Test fun showsStoredValuesForTheActiveDay() {
        show(ReviewTestData.loaded())
        composeRule.onNodeWithText("Review your ride series").assertIsDisplayed()
        composeRule.onNodeWithText("7:30 AM").assertExists()
        composeRule.onNodeWithText("Yeager Center").assertExists()
        composeRule.onNodeWithText("Stop · 5 min · leave 8:06 AM").assertExists()
        // 16093.44 m + 15932 m = 19.9 mi to campus; per-card total = 31 + 19 + 5 min.
        composeRule.onNodeWithText("19.9 mi · 55 min").assertExists()
        composeRule.onNodeWithText("Wednesday: 29.3 miles round trip.", substring = true).assertExists()
    }

    @Test fun unselectedWeekdaysAreDisabledAndSelectedOnesSwitch() {
        var picked: SeriesWeekday? = null
        show(ReviewTestData.loaded(), ReviewActions(onSelectDay = { picked = it }))
        composeRule.onNodeWithTag(ReviewTags.day(SeriesWeekday.Tue)).assertIsNotEnabled()
        composeRule.onNodeWithTag(ReviewTags.day(SeriesWeekday.Mon)).performClick()
        assertEquals(SeriesWeekday.Mon, picked)
    }

    @Test fun postOpensTheSheetThenConfirmPosts() {
        var opened = 0
        var posted = 0
        val actions = ReviewActions(onPost = { opened++ }, onConfirmPost = { posted++ })
        show(ReviewTestData.loaded(), actions)
        composeRule.onNodeWithTag(ReviewTags.Post).performScrollTo().performClick()
        assertEquals(1, opened)
        assertEquals(0, posted)
    }

    @Test fun sheetButtonsAreDisabledWhilePosting() {
        show(ReviewTestData.loaded().copy(confirming = true, posting = true))
        composeRule.onNodeWithText("Post this series?").assertIsDisplayed()
        composeRule.onNodeWithTag(ReviewTags.SheetPost).assertIsNotEnabled().assertTextContains("Posting…")
        composeRule.onNodeWithTag(ReviewTags.SheetBack).assertIsNotEnabled()
    }

    @Test fun sheetCallsConfirmAndGoBack() {
        var posted = 0
        var dismissed = 0
        show(ReviewTestData.loaded().copy(confirming = true), ReviewActions(onConfirmPost = { posted++ }, onDismissConfirm = { dismissed++ }))
        composeRule.onNodeWithTag(ReviewTags.SheetPost).performClick()
        composeRule.onNodeWithTag(ReviewTags.SheetBack).performClick()
        assertEquals(1, posted)
        assertEquals(1, dismissed)
    }

    @Test fun goBackAndEditCallsBack() {
        var back = 0
        show(ReviewTestData.loaded(), ReviewActions(onBack = { back++ }))
        composeRule.onNodeWithTag(ReviewTags.GoBackEdit).performScrollTo().performClick()
        assertEquals(1, back)
    }

    @Test fun retryErrorShowsFriendlyTextAndKeepsPostEnabled() {
        show(ReviewTestData.loaded().copy(postError = PostError.Retry))
        composeRule.onNodeWithText("Couldn’t post your series right now. Check your connection and try again.").assertExists()
        composeRule.onNodeWithText("PostError").assertDoesNotExist()
    }

    @Test fun notFoundAndInvalidDisablePost() {
        show(ReviewTestData.loaded().copy(postError = PostError.NotFound))
        composeRule.onNodeWithText("We couldn’t find this review. Go back and review again.").assertExists()
        composeRule.onNodeWithTag(ReviewTags.Post).assertIsNotEnabled()
    }

    @Test fun invalidSeriesShowsMappedTextNotCodes() {
        show(ReviewTestData.loaded().copy(postError = PostError.Invalid(listOf(SeriesError("startInPast", "startDate")))))
        composeRule.onNodeWithText("Start date can’t be in the past.").assertExists()
        composeRule.onNodeWithText("startInPast", substring = true).assertDoesNotExist()
    }

    @Test fun loadFailureOffersRetry() {
        var retried = 0
        show(ReviewUiState(load = ReviewLoad.Failed), ReviewActions(onRetryLoad = { retried++ }))
        composeRule.onNodeWithTag(ReviewTags.Retry).performClick()
        assertEquals(1, retried)
        composeRule.onNode(hasText("Couldn’t load your review", substring = true)).assertExists()
    }
}
