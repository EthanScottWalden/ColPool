package io.github.ethanscottwalden.colpool.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ethanscottwalden.colpool.R
import io.github.ethanscottwalden.colpool.ui.hosting.HostingText
import io.github.ethanscottwalden.colpool.ui.hosting.Header
import io.github.ethanscottwalden.colpool.ui.series.DayChip
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday

object SearchTags {
    const val Pick = "search_pick_days"
    const val Empty = "search_empty"
    const val Retry = "search_retry"
    const val Count = "search_count"
    const val NoAddress = "search_no_address"
    const val Sent = "search_request_sent"
    const val SentDismiss = "search_request_sent_dismiss"
    fun day(w: SeriesWeekday) = "search_day_${w.id}"
    fun card(id: String) = "search_card_$id"
}

class SearchActions(
    val onToggleDay: (SeriesWeekday) -> Unit = {},
    val onOpen: (String) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onDismissSent: () -> Unit = {},
)

/** Stateful Search tab. Reads fresh results each time the tab is shown. */
@Composable
fun SearchTab(viewModel: SearchViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    SearchContent(state, SearchActions(viewModel::toggle, viewModel::open, viewModel::retry, viewModel::dismissRequestSent), modifier)
}

@Composable
fun SearchContent(state: SearchUiState, actions: SearchActions, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Header(stringResource(R.string.search_title))
            if (state.requestSent) SentBanner(actions.onDismissSent)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.search_days_label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SeriesWeekday.entries.forEach { w ->
                        val name = stringResource(w.fullName)
                        val cd = stringResource(R.string.search_picked_cd, name)
                        val picked = w in state.picked
                        DayChip(
                            selected = picked, enabled = true, onClick = { actions.onToggleDay(w) },
                            title = stringResource(w.shortName), sub = if (picked) stringResource(R.string.search_day_needed) else null,
                            modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp).testTag(SearchTags.day(w)).semantics { contentDescription = if (picked) cd else name },
                            role = Role.Checkbox,
                        )
                    }
                }
                Body(state, actions)
            }
        }
    }
}

@Composable
private fun Body(state: SearchUiState, actions: SearchActions) {
    val res = LocalContext.current.resources
    when (state.load) {
        SearchLoad.Idle -> Text(stringResource(R.string.search_pick_days), Modifier.testTag(SearchTags.Pick), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SearchLoad.Loading -> Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator()
            Text(stringResource(R.string.search_loading), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
        }
        SearchLoad.Failed -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.search_load_failed), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = actions.onRetry, modifier = Modifier.sizeIn(minHeight = 48.dp).testTag(SearchTags.Retry)) { Text(stringResource(R.string.search_retry)) }
        }
        SearchLoad.Loaded -> {
            if (state.hasStart) {
                Text(stringResource(R.string.search_ranking_line), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(stringResource(R.string.search_no_address), Modifier.testTag(SearchTags.NoAddress), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            if (state.results.isEmpty()) {
                Text(stringResource(R.string.search_empty), Modifier.testTag(SearchTags.Empty).semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    SearchText.count(res, state.results.size, state.hasStart), Modifier.testTag(SearchTags.Count).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                )
                state.results.forEach { ResultCard(it, state, actions.onOpen) }
            }
            Text(stringResource(R.string.search_footnote), Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ResultCard(r: SearchResult, state: SearchUiState, onOpen: (String) -> Unit) {
    val res = LocalContext.current.resources
    val s = r.series
    val host = SearchText.host(res, state.hostNames[s.hostId])
    val distance = r.distanceTenths?.let { SearchText.distance(res, it) }
    val times = SearchRules.times(s, r.firstOpen)?.let { SearchText.times(res, r.firstOpen, it) }
    val dates = HostingText.dateRange(s.startDate, s.endDate, state.currentYear)
    Surface(
        onClick = { onOpen(s.id) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(SearchTags.card(s.id)),
        color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(host, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (distance != null) Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), shape = RoundedCornerShape(50), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                Text(distance, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
            }
            Text(s.pickupName, style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                r.days.forEach { (w, st) -> StateChip(w, st, Modifier.weight(1f)) }
                // Keep chip widths equal to a five-day row.
                repeat(5 - r.days.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
            }
            if (times != null) Text(times, style = MaterialTheme.typography.bodyMedium)
            Text(SearchText.cash(res, s.priceDollars), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(dates, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A static weekday chip on a card: the day and how it stands for this rider. */
@Composable
private fun StateChip(w: SeriesWeekday, st: DayState, modifier: Modifier = Modifier) {
    val res = LocalContext.current.resources
    val label = SearchText.dayState(res, st)
    val active = st == DayState.Open
    val cd = stringResource(R.string.search_day_cd, stringResource(w.fullName), label)
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = cd },
        shape = RoundedCornerShape(8.dp),
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (st == DayState.NotNeeded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(w.shortName), maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge)
            Text(label, maxLines = 2, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, letterSpacing = 0.sp, lineHeight = 14.sp))
        }
    }
}

/** "Request sent" after returning from Series Details. A live region, dismissible. */
@Composable
private fun SentBanner(onDismiss: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag(SearchTags.Sent), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.search_request_sent), Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            androidx.compose.material3.TextButton(onClick = onDismiss, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag(SearchTags.SentDismiss)) { Text(stringResource(R.string.search_request_sent_dismiss)) }
        }
    }
}
