package io.github.ethanscottwalden.colpool.ui.notifications

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.ethanscottwalden.colpool.ui.notifications.NotificationsTestData.NOW
import io.github.ethanscottwalden.colpool.ui.search.SearchActions
import io.github.ethanscottwalden.colpool.ui.search.SearchContent
import io.github.ethanscottwalden.colpool.ui.search.SearchUiState
import io.github.ethanscottwalden.colpool.ui.shell.StudentShell
import io.github.ethanscottwalden.colpool.ui.theme.ColPoolTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot tests of notifications. Made-up people only; the time is fixed (Nov 24, 2026, noon Pacific).
 * Goldens: app/src/test/screenshots/notifications_history.png, notifications_empty.png, notification_banner.png, notifications_badge.png
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi", application = Application::class)
class NotificationsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun shoot(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { ColPoolTheme { content() } }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private val history = NotificationsUiState(items = NotificationsTestData.MIXED, loaded = true, newIds = setOf("a", "b"), unreadCount = 2)

    /** Mixed read and unread, several event types. */
    @Config(sdk = [36], qualifiers = "w360dp-h1000dp-xxhdpi", application = Application::class)
    @Test fun historyList() = shoot("notifications_history") {
        StudentShell(
            searchTab = {}, profileTab = {}, hostingTab = {}, selectedTab = 3,
            notificationsTab = { NotificationsContent(history, onRetry = {}, modifier = it, nowMillis = NOW) },
        )
    }

    @Test fun empty() = shoot("notifications_empty") { NotificationsContent(NotificationsUiState(loaded = true), onRetry = {}, nowMillis = NOW) }

    /** The banner over the Search tab, with the unread badge behind it. */
    @Test fun banner() = shoot("notification_banner") {
        val n = NotificationsTestData.note("x", NOW, title = "Day accepted", body = "Sofia Ramirez accepted your request for Thursday.")
        Box(Modifier.fillMaxSize()) {
            StudentShell(
                searchTab = { SearchContent(SearchUiState(currentYear = 2026), SearchActions(), it) }, profileTab = {}, hostingTab = {},
                notificationsBadge = 4,
            )
            NotificationBanner(n, onClick = {}, onClose = {}, modifier = Modifier.align(Alignment.TopCenter))
        }
    }

    /** The footer with the unread badge. */
    @Test fun badge() = shoot("notifications_badge") {
        StudentShell(searchTab = {}, profileTab = {}, hostingTab = {}, hostingBadge = 2, notificationsBadge = 12)
    }
}
