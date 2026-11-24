package io.github.ethanscottwalden.colpool.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ethanscottwalden.colpool.R
import io.github.ethanscottwalden.colpool.data.notifications.AppNotification
import io.github.ethanscottwalden.colpool.ui.hosting.Header
import kotlinx.coroutines.delay

object NotificationTags {
    const val Empty = "notifications_empty"
    const val Failed = "notifications_failed"
    const val Retry = "notifications_retry"
    const val Row = "notification_row"
    const val NewTag = "notification_new_tag"
    const val Banner = "notification_banner"
    const val BannerClose = "notification_banner_close"
}

/** How long the banner stays up (US-16: "a few seconds"; the value is provisional). */
const val BANNER_MILLIS = 5_000L

/** How often the history re-reads the clock for "Today" and "Yesterday". */
const val CLOCK_TICK_MILLIS = 60_000L

private val UnreadFill = Color(0xFFFFF1CC)
private val NewTagColor = Color(0xFFB23A0A)

/** Notifications tab: the history, newest first. */
@Composable
fun NotificationsTab(viewModel: NotificationsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NotificationsContent(state, onRetry = viewModel::retry, modifier = modifier)
}

@Composable
fun NotificationsContent(state: NotificationsUiState, onRetry: () -> Unit, modifier: Modifier = Modifier, nowMillis: Long? = null, clockMillis: () -> Long = System::currentTimeMillis) {
    // "Today" and "Yesterday" come from the device clock (display only). Refreshed when the list changes and every minute,
    // so they stay right if the screen is open across Pacific midnight.
    var clock by remember { mutableLongStateOf(clockMillis()) }
    if (nowMillis == null) {
        LaunchedEffect(state.items) { clock = clockMillis() }
        LaunchedEffect(Unit) { while (true) { delay(CLOCK_TICK_MILLIS); clock = clockMillis() } }
    }
    val now = nowMillis ?: clock
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Header(stringResource(R.string.notifications_title))
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    !state.loaded -> Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.notifications_loading), style = MaterialTheme.typography.bodyMedium)
                    }
                    state.failed && state.items.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.notifications_load_failed), Modifier.testTag(NotificationTags.Failed).semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = onRetry, modifier = Modifier.sizeIn(minHeight = 48.dp).testTag(NotificationTags.Retry)) { Text(stringResource(R.string.notifications_retry)) }
                    }
                    state.items.isEmpty() -> Text(stringResource(R.string.notifications_empty), Modifier.testTag(NotificationTags.Empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> state.items.forEach { NotificationRow(it, isNew = it.id in state.newIds, now = now) }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: AppNotification, isNew: Boolean, now: Long) {
    val shape = RoundedCornerShape(20.dp)
    val time = NotificationRules.timeText(n.createdAtMillis, now)
    Row(
        Modifier.fillMaxWidth().testTag(NotificationTags.Row)
            .clip(shape)
            .background(if (isNew) UnreadFill else MaterialTheme.colorScheme.surface)
            .border(if (isNew) 2.dp else 1.dp, if (isNew) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.surfaceVariant, shape)
            .padding(16.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.padding(top = 7.dp).size(12.dp).clip(CircleShape).background(if (isNew) NewTagColor else MaterialTheme.colorScheme.surfaceVariant).clearAndSetSemantics {})
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(n.title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (isNew) Text(
                    stringResource(R.string.notifications_new_tag),
                    Modifier.testTag(NotificationTags.NewTag).clip(RoundedCornerShape(8.dp)).background(NewTagColor).padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold,
                )
            }
            if (time != null) Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(n.body, style = MaterialTheme.typography.bodyMedium)
            if (n.seriesLine.isNotEmpty()) Text(n.seriesLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Shows the banner for the notification at the front of the queue and closes it after [BANNER_MILLIS]. Put it over the screens, aligned to the top. */
@Composable
fun NotificationBannerHost(viewModel: NotificationsViewModel, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val n = state.banner ?: return
    // Restarts for each notification, so every banner gets its full time.
    LaunchedEffect(n.id) {
        delay(BANNER_MILLIS)
        viewModel.dismissBanner()
    }
    NotificationBanner(n, onClick = { viewModel.dismissBanner(); onOpen() }, onClose = viewModel::dismissBanner, modifier = modifier)
}

@Composable
fun NotificationBanner(n: AppNotification, onClick: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val closeCd = stringResource(R.string.notifications_banner_close)
    val ink = MaterialTheme.colorScheme.onBackground
    val cd = stringResource(R.string.notifications_banner_cd, n.title, n.body)
    Row(
        modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth().testTag(NotificationTags.Banner)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .clip(RoundedCornerShape(24.dp)).background(ink),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).clickable(role = Role.Button, onClick = onClick).semantics(mergeDescendants = true) { contentDescription = cd }
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Text("N", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text(n.title, color = MaterialTheme.colorScheme.background, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(n.body, color = MaterialTheme.colorScheme.background, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Box(
            Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag(NotificationTags.BannerClose).clickable(role = Role.Button, onClick = onClose).semantics { contentDescription = closeCd },
            contentAlignment = Alignment.Center,
        ) { Text("×", Modifier.clearAndSetSemantics {}, color = MaterialTheme.colorScheme.background, style = MaterialTheme.typography.titleLarge) }
    }
}
