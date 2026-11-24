package io.github.ethanscottwalden.colpool.ui.hosting

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ethanscottwalden.colpool.R
import io.github.ethanscottwalden.colpool.data.series.DraftLeg
import io.github.ethanscottwalden.colpool.data.series.HostedSeries
import io.github.ethanscottwalden.colpool.ui.series.Direction
import io.github.ethanscottwalden.colpool.ui.series.DayChip
import io.github.ethanscottwalden.colpool.ui.series.DirectionCard
import io.github.ethanscottwalden.colpool.ui.series.Fact
import io.github.ethanscottwalden.colpool.ui.series.ReviewCards
import io.github.ethanscottwalden.colpool.ui.series.ReviewFormat
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday

object HostingTags {
    const val HostButton = "hosting_host_button"
    const val Empty = "hosting_empty"
    const val Retry = "hosting_retry"
    const val Back = "hosting_details_back"
    const val Riders = "hosting_riders_none"
    const val Requests = "hosting_requests_none"
    const val Badge = "hosting_seats_badge"
    fun row(id: String) = "hosting_row_$id"
    fun day(w: SeriesWeekday) = "hosting_day_${w.id}"
}

class HostingActions(
    val onHostSeries: () -> Unit = {},
    val onOpen: (String) -> Unit = {},
    val onBack: () -> Unit = {},
    val onSelectDay: (SeriesWeekday) -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** Stateful Hosting tab. */
@Composable
fun HostingTab(viewModel: HostingViewModel, onHostSeries: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HostingListContent(state, HostingActions(onHostSeries = onHostSeries, onOpen = viewModel::open, onRetry = viewModel::retry), modifier)
}

/** Stateful details screen. */
@Composable
fun HostedSeriesDetailsScreen(viewModel: HostingViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler { viewModel.close() }
    HostedSeriesDetailsContent(state, HostingActions(onBack = viewModel::close, onSelectDay = viewModel::selectDay), modifier)
}

@Composable
private fun Header(title: String, onBack: (() -> Unit)? = null) {
    val backCd = stringResource(R.string.series_back)
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).statusBarsPadding().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) OutlinedButton(
            onClick = onBack,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag(HostingTags.Back).semantics { contentDescription = backCd },
            contentPadding = PaddingValues(0.dp),
        ) { Text("‹", Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.titleLarge) }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.semantics { heading() })
    }
}

@Composable
fun HostingListContent(state: HostingUiState, actions: HostingActions, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Header(stringResource(R.string.hosting_title))
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = actions.onHostSeries, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(HostingTags.HostButton)) {
                    Text(stringResource(R.string.series_host_button))
                }
                Text(stringResource(R.string.hosting_heading), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                when {
                    state.load == HostingLoad.Loading -> Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.hosting_loading), style = MaterialTheme.typography.bodyMedium)
                    }
                    state.load == HostingLoad.Failed -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.hosting_load_failed), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = actions.onRetry, modifier = Modifier.sizeIn(minHeight = 48.dp).testTag(HostingTags.Retry)) { Text(stringResource(R.string.hosting_retry)) }
                    }
                    state.series.isEmpty() -> EmptyState()
                    else -> state.series.forEach { SeriesRow(it, state.currentYear, actions.onOpen) }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp).testTag(HostingTags.Empty), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.hosting_empty_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(stringResource(R.string.hosting_empty_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SeriesRow(s: HostedSeries, year: Int, onOpen: (String) -> Unit) {
    val res = LocalContext.current.resources
    val days = HostingText.weekdays(res, s.weekdayIds)
    val datesPrice = stringResource(R.string.hosting_dates_price, HostingText.dateRange(s.startDate, s.endDate, year), HostingText.price(res, s.priceDollars))
    val seats = HostingText.seatsLine(res, s)
    Surface(
        onClick = { onOpen(s.id) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(HostingTags.row(s.id)),
        color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(days, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(s.pickupName, style = MaterialTheme.typography.bodyLarge)
            Text(datesPrice, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(seats, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun HostedSeriesDetailsContent(state: HostingUiState, actions: HostingActions, modifier: Modifier = Modifier) {
    val s = state.selected
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            val res = LocalContext.current.resources
            Header(if (s == null) "" else stringResource(R.string.hosting_details_title, HostingText.weekdays(res, s.weekdayIds)), actions.onBack)
            if (s != null) Details(state, s, actions)
        }
    }
}

@Composable
private fun Details(state: HostingUiState, s: HostedSeries, actions: HostingActions) {
    val res = LocalContext.current.resources
    val active = state.activeDay ?: SeriesWeekday.fromId(s.weekdayIds.first())!!
    val day = s.days[active.id]
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.series_pickup_label).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(s.pickupName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Fact(R.string.review_price, stringResource(R.string.review_price_value, s.priceDollars), stringResource(R.string.review_price_sub), Modifier.weight(1f))
                    Fact(R.string.review_dates, HostingText.dateRange(s.startDate, s.endDate, state.currentYear), null, Modifier.weight(1.3f))
                    Fact(R.string.review_riders, stringResource(R.string.review_riders_value, s.maxRiders), stringResource(R.string.review_riders_sub), Modifier.weight(1f))
                }
                Text(s.vehicle, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Text(stringResource(R.string.hosting_by_day), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SeriesWeekday.entries.forEach { w ->
                val used = w.id in s.weekdayIds
                val name = stringResource(w.fullName)
                val unusedCd = stringResource(R.string.review_day_unused, name)
                val openText = if (used) stringResource(R.string.hosting_day_open, s.openSeats[w.id] ?: 0) else null
                DayChip(
                    selected = used && w == active, enabled = used, onClick = { actions.onSelectDay(w) },
                    title = stringResource(w.shortName), sub = openText ?: "—",
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(HostingTags.day(w)).semantics {
                        contentDescription = if (used) "$name, $openText" else unusedCd
                    },
                )
            }
        }
        Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(active.fullName), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), shape = RoundedCornerShape(50), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                        Text(HostingText.seatsOpen(res, s.openSeats[active.id] ?: 0, s.maxRiders), Modifier.padding(horizontal = 12.dp, vertical = 6.dp).testTag(HostingTags.Badge), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Text(stringResource(R.string.hosting_riders_none), Modifier.testTag(HostingTags.Riders), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (day != null) {
            val campusFallback = stringResource(R.string.review_campus_fallback)
            fun campus(l: DraftLeg) = state.campusNames[l.campusPointId] ?: campusFallback
            val to = ReviewCards.build(day.toCampus, s.pickupName, campus(day.toCampus))
            val from = ReviewCards.build(day.fromCampus, campus(day.fromCampus), s.pickupName)
            DirectionCard(Direction.ToCampus, to)
            DirectionCard(Direction.FromCampus, from)
            Text(
                stringResource(R.string.review_footnote, stringResource(active.fullName), ReviewFormat.tenths(to.totalMilesTenths + from.totalMilesTenths)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(stringResource(R.string.hosting_requests_heading), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.hosting_requests_none), Modifier.testTag(HostingTags.Requests), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
