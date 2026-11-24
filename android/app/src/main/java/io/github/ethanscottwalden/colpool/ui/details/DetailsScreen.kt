package io.github.ethanscottwalden.colpool.ui.details

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ethanscottwalden.colpool.R
import io.github.ethanscottwalden.colpool.data.requests.DayFailureReason
import io.github.ethanscottwalden.colpool.data.series.DraftLeg
import io.github.ethanscottwalden.colpool.data.series.HostedSeries
import io.github.ethanscottwalden.colpool.ui.hosting.Header
import io.github.ethanscottwalden.colpool.ui.hosting.HostingText
import io.github.ethanscottwalden.colpool.ui.search.DayState
import io.github.ethanscottwalden.colpool.ui.search.SearchText
import io.github.ethanscottwalden.colpool.ui.series.DayChip
import io.github.ethanscottwalden.colpool.ui.series.Direction
import io.github.ethanscottwalden.colpool.ui.series.DirectionCard
import io.github.ethanscottwalden.colpool.ui.series.Fact
import io.github.ethanscottwalden.colpool.ui.series.ReviewCards
import io.github.ethanscottwalden.colpool.ui.series.ReviewFormat
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday

object DetailsTags {
    const val Summary = "details_summary"
    const val HostCard = "details_host_card"
    const val Safety = "details_safety"
    const val Picker = "details_picker"
    const val Request = "details_request"
    const val Warning = "details_warning"
    const val Error = "details_error"
    const val Already = "details_already"
    const val Sheet = "details_sheet"
    const val SheetSend = "details_sheet_send"
    const val SheetCancel = "details_sheet_cancel"
    const val Scrim = "details_scrim"
    fun tab(w: SeriesWeekday) = "details_tab_${w.id}"
    fun pick(w: SeriesWeekday) = "details_pick_${w.id}"
}

class DetailsActions(
    val onBack: () -> Unit = {},
    val onSelectDay: (SeriesWeekday) -> Unit = {},
    val onTogglePick: (SeriesWeekday) -> Unit = {},
    val onRequest: () -> Unit = {},
    val onSend: () -> Unit = {},
    val onDismissConfirm: () -> Unit = {},
)

/** Stateful Series Details. [onSent] runs once when the request was sent. */
@Composable
fun SeriesDetailsScreen(viewModel: DetailsViewModel, onBack: () -> Unit, onSent: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.sent) { if (state.sent) { viewModel.consumeSent(); onSent() } }
    BackHandler { if (state.confirming) viewModel.dismissConfirm() else onBack() }
    DetailsContent(
        state,
        DetailsActions(onBack, viewModel::selectDay, viewModel::togglePick, viewModel::openConfirm, viewModel::send, viewModel::dismissConfirm),
        modifier,
    )
}

@Composable
fun DetailsContent(state: DetailsUiState, actions: DetailsActions, modifier: Modifier = Modifier) {
    val s = state.series
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            // The sheet hides what is behind it from accessibility.
            Column(Modifier.fillMaxSize().then(if (state.confirming) Modifier.clearAndSetSemantics {} else Modifier)) {
                Header(stringResource(R.string.details_title), actions.onBack)
                if (s != null) Body(state, s, actions)
            }
            if (state.confirming && s != null) ConfirmSheet(state, s, actions)
        }
    }
}

@Composable
private fun Body(state: DetailsUiState, s: HostedSeries, actions: DetailsActions) {
    val active = state.activeDay ?: SeriesWeekday.fromId(s.weekdayIds.first())!!
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Summary(state, s)
        Text(stringResource(R.string.details_schedule), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        DayTabs(state, s, active, actions.onSelectDay)
        s.days[active.id]?.let { day ->
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
        Text(stringResource(R.string.details_your_host), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        HostCard(state)
        when (state.standing) {
            Standing.Requestable -> Picker(state, s, actions)
            else -> StandingCard(state)
        }
        Surface(
            Modifier.fillMaxWidth().testTag(DetailsTags.Safety),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) { Text(stringResource(R.string.details_safety), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun Summary(state: DetailsUiState, s: HostedSeries) {
    val res = LocalContext.current.resources
    val name = state.profile?.let { "${it.firstName} ${it.lastName}" } ?: state.firstNameHint ?: stringResource(R.string.details_host_unknown)
    Surface(Modifier.fillMaxWidth().testTag(DetailsTags.Summary), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            state.distanceTenths?.let {
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), shape = RoundedCornerShape(50), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                    Text(SearchText.distance(res, it), Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
            Text(stringResource(R.string.series_pickup_label).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(s.pickupName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(SearchText.cash(res, s.priceDollars), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Fact(R.string.review_dates, HostingText.dateRange(s.startDate, s.endDate, state.currentYear), null, Modifier.weight(1.3f))
                Fact(R.string.review_riders, stringResource(R.string.review_riders_value, s.maxRiders), stringResource(R.string.review_riders_sub), Modifier.weight(1f))
            }
            Text(s.vehicle, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun DayTabs(state: DetailsUiState, s: HostedSeries, active: SeriesWeekday, onSelect: (SeriesWeekday) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SeriesWeekday.entries.forEach { w ->
            val used = w.id in s.weekdayIds
            val name = stringResource(w.fullName)
            val sub = if (used) dayText(state.dayStates[w] ?: DayState.Full, s.openSeats[w.id] ?: 0) else "—"
            val offCd = stringResource(R.string.details_day_off_cd, name)
            val cd = stringResource(R.string.details_day_cd, name, sub)
            DayChip(
                selected = used && w == active, enabled = used, onClick = { onSelect(w) },
                title = stringResource(w.shortName), sub = sub, subMaxLines = 2,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(DetailsTags.tab(w)).semantics { contentDescription = if (used) cd else offCd },
                role = Role.Tab,
            )
        }
    }
}

@Composable
private fun dayText(st: DayState, open: Int): String = when (st) {
    DayState.Open -> stringResource(R.string.details_day_open, open)
    DayState.Full -> stringResource(R.string.details_day_full)
    else -> stringResource(R.string.details_day_no_trips)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HostCard(state: DetailsUiState) {
    val fields = state.profile?.let { DetailsRules.hostFields(it, state.options) }.orEmpty()
    val name = state.profile?.let { "${it.firstName} ${it.lastName}" } ?: state.firstNameHint ?: stringResource(R.string.details_host_unknown)
    Surface(Modifier.fillMaxWidth().testTag(DetailsTags.HostCard), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            fields.forEach { f ->
                when (f) {
                    is HostField.Text -> Column {
                        Text(stringResource(f.label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(f.value, style = MaterialTheme.typography.bodyLarge)
                    }
                    is HostField.Chips -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(f.label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            f.values.forEach {
                                Surface(shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = Color.Transparent) {
                                    Text(it, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StandingCard(state: DetailsUiState) {
    val (text, tag) = when (state.standing) {
        Standing.Own -> R.string.details_err_own to DetailsTags.Error
        Standing.Gone -> R.string.details_err_gone to DetailsTags.Error
        else -> R.string.details_err_already to DetailsTags.Already
    }
    Surface(Modifier.fillMaxWidth().testTag(tag), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Text(stringResource(text), Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Picker(state: DetailsUiState, s: HostedSeries, actions: DetailsActions) {
    Surface(Modifier.fillMaxWidth().testTag(DetailsTags.Picker), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.details_picker_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.details_picker_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SeriesWeekday.entries.forEach { w ->
                    val used = w.id in s.weekdayIds
                    val name = stringResource(w.fullName)
                    val reason = state.refused[w]
                    val sub = when {
                        !used -> "—"
                        reason == DayFailureReason.Conflict -> stringResource(R.string.details_chip_conflict)
                        reason != null -> stringResource(DetailsRules.failureText(reason))
                        else -> when (state.dayStates[w]) {
                            DayState.Open -> stringResource(R.string.details_picker_open, s.openSeats[w.id] ?: 0)
                            DayState.NoTripsLeft -> stringResource(R.string.details_picker_no_trips)
                            else -> stringResource(R.string.details_picker_full)
                        }
                    }
                    val cd = if (used) stringResource(R.string.details_day_cd, name, reason?.let { stringResource(DetailsRules.failureText(it)) } ?: sub) else stringResource(R.string.details_day_off_cd, name)
                    DayChip(
                        selected = w in state.picked, enabled = w in state.selectable && !state.sending, onClick = { actions.onTogglePick(w) },
                        title = stringResource(w.shortName), sub = sub, subMaxLines = 2,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(DetailsTags.pick(w)).semantics { contentDescription = cd },
                        role = Role.Checkbox,
                    )
                }
            }
            RefusedLines(state)
            state.error?.let { ErrorBox(it) }
            Text(stringResource(R.string.details_one_request), Modifier.testTag(DetailsTags.Warning), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Button(onClick = actions.onRequest, enabled = state.canSend, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(DetailsTags.Request)) {
                Text(stringResource(R.string.details_request_button))
            }
        }
    }
}

/** The long conflict reason does not fit in a chip, so each refused day also gets a full line. */
@Composable
private fun RefusedLines(state: DetailsUiState) {
    if (state.refused.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        state.refused.entries.sortedBy { it.key.ordinal }.forEach { (w, r) ->
            Text(stringResource(R.string.details_day_cd, stringResource(w.fullName), stringResource(DetailsRules.failureText(r))), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorBox(e: SendError) {
    val text = when (e) {
        SendError.Invalid -> R.string.details_err_invalid
        SendError.Retry -> R.string.details_err_retry
        SendError.OwnSeries -> R.string.details_err_own
        SendError.AlreadyRequested -> R.string.details_err_already
        SendError.Gone -> R.string.details_err_gone
        SendError.SomeDays -> R.string.details_err_days
    }
    Surface(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.testTag(DetailsTags.Error),
        color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
    ) { Text(stringResource(text), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer) }
}

/** The confirmation sheet: a scrim and a panel at the bottom (in layout, like Review's, so screenshots capture it). */
@Composable
private fun ConfirmSheet(state: DetailsUiState, s: HostedSeries, actions: DetailsActions) {
    val res = LocalContext.current.resources
    val title = stringResource(R.string.details_confirm_title)
    val days = SeriesWeekday.entries.filter { it in state.picked }.joinToString(", ") { res.getString(it.fullName) }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(enabled = !state.sending, onClickLabel = stringResource(R.string.details_cancel), onClick = actions.onDismissConfirm).testTag(DetailsTags.Scrim).clearAndSetSemantics {})
        Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().testTag(DetailsTags.Sheet).semantics { paneTitle = title },
            color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(Modifier.navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.details_confirm_days, days), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(SearchText.cash(res, s.priceDollars), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.details_confirm_decides), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.details_one_request), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Button(onClick = actions.onSend, enabled = !state.sending, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(DetailsTags.SheetSend)) {
                    Text(stringResource(if (state.sending) R.string.details_sending else R.string.details_send))
                }
                OutlinedButton(onClick = actions.onDismissConfirm, enabled = !state.sending, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(DetailsTags.SheetCancel)) {
                    Text(stringResource(R.string.details_cancel))
                }
            }
        }
    }
}
