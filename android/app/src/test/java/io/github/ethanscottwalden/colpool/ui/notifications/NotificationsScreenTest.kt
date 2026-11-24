package io.github.ethanscottwalden.colpool.ui.notifications

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.collectAsState
import io.github.ethanscottwalden.colpool.ui.notifications.NotificationsTestData.NOW
import io.github.ethanscottwalden.colpool.ui.notifications.NotificationsTestData.note
import io.github.ethanscottwalden.colpool.ui.shell.NOTIFICATIONS_BADGE_TAG
import io.github.ethanscottwalden.colpool.ui.shell.NOTIFICATIONS_TAB
import io.github.ethanscottwalden.colpool.ui.shell.StudentShell
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
@Config(sdk = [36], qualifiers = "w360dp-h1400dp-xxhdpi", application = Application::class)
class NotificationsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val backend = FakeNotificationsBackend()

    /** A click or an emission needs a few frames: recompose, run the effect, recompose with the new state. */
    private fun frames() = repeat(4) { composeRule.mainClock.advanceTimeByFrame() }

    private fun content(state: NotificationsUiState, onRetry: () -> Unit = {}) = composeRule.setContent {
        ColPoolTheme { NotificationsContent(state, onRetry, nowMillis = NOW) }
    }

    @Test fun historyShowsTitleTimeBodyAndSeriesLineWithNewTagsOnUnreadOnly() {
        content(NotificationsUiState(items = NotificationsTestData.MIXED, loaded = true, newIds = setOf("a", "b")))
        composeRule.onAllNodesWithTag(NotificationTags.Row).assertCountEquals(5)
        composeRule.onAllNodesWithTag(NotificationTags.NewTag, useUnmergedTree = true).assertCountEquals(2)
        composeRule.onNodeWithText("Today, 9:05 AM").assertIsDisplayed()
        composeRule.onNodeWithText("Yesterday, 6:40 PM").assertIsDisplayed()
        composeRule.onNodeWithText("Oct 28, 2:10 PM").assertIsDisplayed()
        composeRule.onNodeWithText("Eli Morgan wants to join your series on Thursday.").assertIsDisplayed()
        composeRule.onNodeWithText("Marcus Lee’s series from Moreno Valley Mall, north lot").assertExists()
        composeRule.onNodeWithText("Day declined").assertExists()
    }

    @Test fun todayBecomesYesterdayWhenThePacificDayChangesWhileOpen() {
        var now = NotificationsTestData.pacific(2026, 11, 24, 23, 59, 30)
        val items = listOf(note("a", NotificationsTestData.pacific(2026, 11, 24, 9, 5), read = true))
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { ColPoolTheme { NotificationsContent(NotificationsUiState(items = items, loaded = true), onRetry = {}, clockMillis = { now }) } }
        frames()
        composeRule.onNodeWithText("Today, 9:05 AM").assertExists()
        now = NotificationsTestData.pacific(2026, 11, 25, 0, 0, 30)
        composeRule.mainClock.advanceTimeBy(CLOCK_TICK_MILLIS + 100)
        frames()
        composeRule.onNodeWithText("Yesterday, 9:05 AM").assertExists()
    }

    @Test fun emptyState() {
        content(NotificationsUiState(loaded = true))
        composeRule.onNodeWithTag(NotificationTags.Empty).assertTextEquals("No notifications yet. You’ll see updates about your ride series and requests here.")
    }

    @Test fun errorStateWithTryAgain() {
        var retried = 0
        content(NotificationsUiState(loaded = true, failed = true), onRetry = { retried++ })
        composeRule.onNodeWithTag(NotificationTags.Failed).assertTextEquals("Couldn’t load notifications. Check your connection and try again.")
        composeRule.onNodeWithTag(NotificationTags.Retry).performClick()
        assertEquals(1, retried)
    }

    @Test fun aFailureAfterALoadKeepsTheHistory() {
        content(NotificationsUiState(items = NotificationsTestData.MIXED, loaded = true, failed = true))
        composeRule.onAllNodesWithTag(NotificationTags.Row).assertCountEquals(5)
        composeRule.onAllNodesWithTag(NotificationTags.Failed).assertCountEquals(0)
    }

    private fun shell(count: Int) = composeRule.setContent {
        ColPoolTheme { StudentShell(searchTab = {}, profileTab = {}, hostingTab = {}, notificationsBadge = count) }
    }

    @Test fun badgeShowsTheCountAndSaysItAloud() {
        shell(3)
        composeRule.onNodeWithTag(NOTIFICATIONS_BADGE_TAG, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("3 unread notifications", useUnmergedTree = true).assertExists()
    }

    @Test fun badgeIsHiddenAtZeroAndCapsAtNinePlus() {
        shell(0)
        composeRule.onAllNodesWithTag(NOTIFICATIONS_BADGE_TAG, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun badgeSaysTheRealCountEvenWhenItShowsNinePlus() {
        shell(12)
        composeRule.onNodeWithContentDescription("12 unread notifications", useUnmergedTree = true).assertExists()
    }

    /** The same wiring the app root uses: the tab being open starts and ends a visit. */
    @Composable
    private fun Harness(vm: NotificationsViewModel, openBannerCount: () -> Unit = {}) {
        var tab by remember { mutableIntStateOf(0) }
        val state by vm.state.collectAsState()
        LaunchedEffect(tab) { if (tab == NOTIFICATIONS_TAB) vm.openTab() else vm.closeTab() }
        Box(Modifier.fillMaxSize()) {
            StudentShell(
                searchTab = { Text("search screen", it) }, profileTab = {}, hostingTab = {},
                selectedTab = tab, onTabSelected = { tab = it },
                notificationsBadge = state.unreadCount,
                notificationsTab = { NotificationsTab(vm, it) },
            )
            NotificationBannerHost(vm, onOpen = { openBannerCount(); tab = NOTIFICATIONS_TAB }, modifier = Modifier.align(Alignment.TopCenter))
        }
    }

    private fun startHarness(opened: () -> Unit = {}): NotificationsViewModel {
        val vm = NotificationsViewModel(backend)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { ColPoolTheme { Harness(vm, opened) } }
        vm.start()
        backend.load(note("a", 1_000, title = "Day accepted", body = "Sofia Ramirez accepted your request for Thursday."), note("old", 500, read = true))
        frames()
        return vm
    }

    @Test fun openingTheTabClearsTheBadgeAtOnceAndKeepsTheNewTag() {
        val vm = startHarness()
        composeRule.onNodeWithTag(NOTIFICATIONS_BADGE_TAG, useUnmergedTree = true).assertExists()
        composeRule.onNode(hasText("Notifications") and hasClickAction()).performClick()
        frames()
        composeRule.onAllNodesWithTag(NOTIFICATIONS_BADGE_TAG, useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithTag(NotificationTags.NewTag, useUnmergedTree = true).assertCountEquals(1)
        assertEquals(listOf(listOf("a")), backend.markedRead)
        // Leaving and coming back: it was read, so no tag and no badge.
        backend.load(note("a", 1_000, read = true), note("old", 500, read = true))
        composeRule.onNode(hasText("Search") and hasClickAction()).performClick()
        frames()
        composeRule.onNode(hasText("Notifications") and hasClickAction()).performClick()
        frames()
        composeRule.onAllNodesWithTag(NotificationTags.NewTag, useUnmergedTree = true).assertCountEquals(0)
        assertEquals(0, vm.state.value.unreadCount)
    }

    @Test fun bannerShowsTheServersTextOverTheScreenAndClosesAfterAFewSeconds() {
        startHarness()
        backend.load(note("b", 2_000, title = "Day accepted", body = "Sofia Ramirez accepted your request for Thursday."), note("a", 1_000), note("old", 500, read = true))
        frames()
        composeRule.onNodeWithTag(NotificationTags.Banner).assertIsDisplayed()
        composeRule.onNodeWithText("Sofia Ramirez accepted your request for Thursday.").assertIsDisplayed()
        composeRule.onNodeWithText("search screen").assertExists()
        composeRule.mainClock.advanceTimeBy(BANNER_MILLIS - 500)
        composeRule.onNodeWithTag(NotificationTags.Banner).assertExists()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onAllNodesWithTag(NotificationTags.Banner).assertCountEquals(0)
    }

    @Test fun closeButtonDismissesAndTheNextOneShows() {
        startHarness()
        backend.load(
            note("c", 3_000, title = "Day declined", body = "Marcus Lee declined your request for Tuesday."),
            note("b", 2_000, title = "Day accepted", body = "Priya Nair accepted your request for Monday."),
            note("a", 1_000), note("old", 500, read = true),
        )
        frames()
        composeRule.onNodeWithText("Marcus Lee declined your request for Tuesday.").assertIsDisplayed()
        composeRule.onAllNodesWithTag(NotificationTags.Banner).assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Close notification").performClick()
        frames()
        composeRule.onNodeWithText("Priya Nair accepted your request for Monday.").assertIsDisplayed()
    }

    @Test fun tappingTheBannerOpensTheNotificationsTab() {
        var opened = 0
        val vm = startHarness { opened++ }
        backend.load(note("b", 2_000, title = "Day accepted", body = "Sofia Ramirez accepted your request for Thursday."), note("a", 1_000), note("old", 500, read = true))
        frames()
        composeRule.onNodeWithContentDescription("Day accepted. Sofia Ramirez accepted your request for Thursday.. Opens your notifications.").performClick()
        frames()
        assertEquals(1, opened)
        composeRule.onAllNodesWithTag(NotificationTags.Banner).assertCountEquals(0)
        composeRule.onAllNodesWithTag(NotificationTags.Row).assertCountEquals(3)
        assertEquals(0, vm.state.value.unreadCount)
    }

    @Test fun noBannerWhileTheNotificationsTabIsOpen() {
        startHarness()
        composeRule.onNode(hasText("Notifications") and hasClickAction()).performClick()
        frames()
        backend.load(note("b", 2_000), note("a", 1_000), note("old", 500, read = true))
        frames()
        composeRule.onAllNodesWithTag(NotificationTags.Banner).assertCountEquals(0)
        composeRule.onAllNodesWithTag(NotificationTags.Row).assertCountEquals(3)
    }
}
