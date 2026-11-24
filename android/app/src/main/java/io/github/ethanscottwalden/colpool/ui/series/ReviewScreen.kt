package io.github.ethanscottwalden.colpool.ui.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
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
import io.github.ethanscottwalden.colpool.data.series.DraftLeg
import io.github.ethanscottwalden.colpool.data.series.ReviewDraft

object ReviewTags {
    const val Back = "review_back"
    const val GoBackEdit = "review_go_back_edit"
    const val Post = "review_post"
    const val Retry = "review_retry"
    const val Error = "review_error"
    const val Sheet = "review_sheet"
    const val SheetPost = "review_sheet_post"
    const val SheetBack = "review_sheet_back"
    const val Scrim = "review_scrim"
    fun day(w: SeriesWeekday) = "review_day_${w.id}"
    fun card(d: Direction) = "review_card_${d.id}"
}

class ReviewActions(
    val onBack: () -> Unit = {},
    val onSelectDay: (SeriesWeekday) -> Unit = {},
    val onPost: () -> Unit = {},
    val onConfirmPost: () -> Unit = {},
    val onDismissConfirm: () -> Unit = {},
    val onRetryLoad: () -> Unit = {},
)

/** Stateful wrapper: loads the draft, and hands off when the view model says it is done. */
@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel,
    draftId: String,
    onPosted: () -> Unit,
    onEdit: (ReviewExit.Edit) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(draftId) { viewModel.load(draftId) }
    LaunchedEffect(state.exit) {
        when (val e = state.exit) {
            ReviewExit.Posted -> { viewModel.consumeExit(); onPosted() }
            is ReviewExit.Edit -> { viewModel.consumeExit(); onEdit(e) }
            null -> Unit
        }
    }
    BackHandler { if (state.confirming) viewModel.dismissConfirm() else viewModel.goBack() }
    ReviewContent(
        state,
        ReviewActions(
            onBack = viewModel::goBack,
            onSelectDay = viewModel::selectDay,
            onPost = viewModel::openConfirm,
            onConfirmPost = viewModel::post,
            onDismissConfirm = viewModel::dismissConfirm,
            onRetryLoad = viewModel::retry,
        ),
    )
}

@Composable
fun ReviewContent(state: ReviewUiState, actions: ReviewActions, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().then(if (state.confirming) Modifier.clearAndSetSemantics {} else Modifier)) {
                ReviewHeader(actions.onBack)
                when (state.load) {
                    ReviewLoad.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.review_loading), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    ReviewLoad.Failed -> Notice(R.string.review_load_failed, Modifier.padding(16.dp)) {
                        Button(onClick = actions.onRetryLoad, modifier = Modifier.sizeIn(minHeight = 48.dp).testTag(ReviewTags.Retry)) { Text(stringResource(R.string.review_retry)) }
                        OutlinedButton(onClick = actions.onBack, modifier = Modifier.sizeIn(minHeight = 48.dp).testTag(ReviewTags.GoBackEdit)) { Text(stringResource(R.string.review_go_back_edit)) }
                    }
                    ReviewLoad.NotFound -> Notice(R.string.review_err_not_found, Modifier.padding(16.dp)) {
                        OutlinedButton(onClick = actions.onBack, modifier = Modifier.sizeIn(minHeight = 48.dp).testTag(ReviewTags.GoBackEdit)) { Text(stringResource(R.string.review_go_back_edit)) }
                    }
                    ReviewLoad.Loaded -> if (state.draft != null) Loaded(state, state.draft, actions)
                }
            }
            if (state.confirming) ConfirmSheet(state.posting, actions)
        }
    }
}

@Composable
private fun ReviewHeader(onBack: () -> Unit) {
    val backCd = stringResource(R.string.series_back)
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).statusBarsPadding().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag(ReviewTags.Back).semantics { contentDescription = backCd },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) { Text("‹", Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.titleLarge) }
        Text(stringResource(R.string.review_title), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.semantics { heading() })
    }
}

/** A message in a live region with actions under it. */
@Composable
private fun Notice(text: Int, modifier: Modifier = Modifier, actions: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(text), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyLarge)
        actions()
    }
}

@Composable
private fun Loaded(state: ReviewUiState, draft: ReviewDraft, actions: ReviewActions) {
    val active = state.activeDay ?: SeriesWeekday.fromId(draft.weekdayIds.first())!!
    val day = draft.days[active.id]
    val dayName = stringResource(active.fullName)
    val busy = state.posting
    val locked = state.postError is PostError.NotFound || state.postError is PostError.Invalid
    val scroll = rememberScrollState()
    // The error sits above the buttons: bring it into view when it appears.
    LaunchedEffect(state.postError) { if (state.postError != null) scroll.animateScrollTo(scroll.maxValue) }
    Column(Modifier.fillMaxSize().verticalScroll(scroll).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        WarningNote()
        SummaryCard(draft)
        Text(stringResource(R.string.review_heading), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        DayTabs(draft, active, actions.onSelectDay)
        if (day != null) {
            val pickup = draft.pickupName
            val campusFallback = stringResource(R.string.review_campus_fallback)
            fun campus(l: DraftLeg) = state.campusNames[l.campusPointId] ?: campusFallback
            val to = ReviewCards.build(day.toCampus, pickup, campus(day.toCampus))
            val from = ReviewCards.build(day.fromCampus, campus(day.fromCampus), pickup)
            DirectionCard(Direction.ToCampus, to)
            DirectionCard(Direction.FromCampus, from)
            Text(
                stringResource(R.string.review_footnote, dayName, ReviewFormat.tenths(to.totalMilesTenths + from.totalMilesTenths)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.postError?.let { PostErrorBox(it) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = actions.onBack, enabled = !busy,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(ReviewTags.GoBackEdit),
            ) { Text(stringResource(R.string.review_go_back_edit)) }
            Button(
                onClick = actions.onPost, enabled = !busy && !locked,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(ReviewTags.Post),
            ) { Text(stringResource(R.string.review_post)) }
        }
    }
}

@Composable
private fun WarningNote() {
    Surface(
        Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) { Text(stringResource(R.string.review_warning), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun SummaryCard(draft: ReviewDraft) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.series_pickup_label).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(draft.pickupName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Fact(R.string.review_price, stringResource(R.string.review_price_value, draft.priceDollars), stringResource(R.string.review_price_sub), Modifier.weight(1f))
                Fact(R.string.review_dates, stringResource(R.string.review_dates_value, SeriesDates.display(draft.startDate), SeriesDates.display(draft.endDate)), null, Modifier.weight(1.3f))
                Fact(R.string.review_riders, stringResource(R.string.review_riders_value, draft.maxRiders), stringResource(R.string.review_riders_sub), Modifier.weight(1f))
            }
            Text(draft.vehicle, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun Fact(label: Int, value: String, sub: String?, modifier: Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(10.dp).semantics(mergeDescendants = true) {}) {
            Text(stringResource(label).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DayTabs(draft: ReviewDraft, active: SeriesWeekday, onSelect: (SeriesWeekday) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SeriesWeekday.entries.forEach { w ->
            val used = w.id in draft.weekdayIds
            val name = stringResource(w.fullName)
            val unusedCd = stringResource(R.string.review_day_unused, name)
            DayChip(
                selected = used && w == active,
                enabled = used,
                onClick = { onSelect(w) },
                title = stringResource(w.shortName),
                sub = if (used) null else "—",
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(ReviewTags.day(w))
                    .semantics { if (!used) contentDescription = unusedCd else contentDescription = name },
            )
        }
    }
}

@Composable
private fun DirectionCard(direction: Direction, card: ReviewCard) {
    val toCampus = direction == Direction.ToCampus
    val nextDay = stringResource(R.string.review_next_day)
    Surface(
        Modifier.fillMaxWidth().testTag(ReviewTags.card(direction)),
        color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(stringResource(if (toCampus) R.string.series_to_campus else R.string.series_from_campus), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.review_card_total, ReviewFormat.tenths(card.totalMilesTenths), card.totalMinutes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            card.points.forEachIndexed { i, p ->
                PointRow(p, toCampus, nextDay)
                card.hops.getOrNull(i)?.let { HopRow(it) }
            }
        }
    }
}

private val TimeColumn = 84.dp
private val LineColumn = 24.dp

@Composable
private fun PointRow(p: ReviewPoint, toCampus: Boolean, nextDay: String) {
    val leaving = p.role == ReviewRole.Start
    val label = stringResource(if (leaving) R.string.review_leave else R.string.review_arrive)
    val time = ReviewFormat.time(p.time, nextDay)
    val sub = when (p.role) {
        ReviewRole.Start -> stringResource(if (toCampus) R.string.review_pickup_spot else R.string.series_pickup_point)
        ReviewRole.Stop -> stringResource(R.string.review_stop, p.stopMinutes, p.leaveMin?.let { ReviewFormat.time(it, nextDay) } ?: "")
        ReviewRole.End -> stringResource(if (toCampus) R.string.series_dropoff_point else R.string.review_back_at_pickup)
    }
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.width(TimeColumn)) {
            Text(time, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Dot(p.role)
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Dot(role: ReviewRole) {
    val ink = MaterialTheme.colorScheme.onSurface
    val fill = when (role) {
        ReviewRole.Start -> MaterialTheme.colorScheme.primary
        ReviewRole.Stop -> MaterialTheme.colorScheme.surface
        ReviewRole.End -> ink
    }
    Box(Modifier.padding(top = 4.dp).size(16.dp).background(fill, CircleShape).border(2.dp, ink, CircleShape).clearAndSetSemantics {})
}

@Composable
private fun HopRow(h: ReviewHopRow) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Spacer(Modifier.width(TimeColumn))
        Box(Modifier.width(16.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.onSurface).clearAndSetSemantics {})
        }
        Text(
            stringResource(R.string.review_hop, h.minutes, ReviewFormat.tenths(h.milesTenths)),
            Modifier.padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PostErrorBox(error: PostError) {
    Surface(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.testTag(ReviewTags.Error),
        color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (error) {
                PostError.NotFound -> ErrorLine(stringResource(R.string.review_err_not_found))
                PostError.Retry -> ErrorLine(stringResource(R.string.review_err_retry))
                is PostError.Invalid -> {
                    Text(stringResource(R.string.review_err_invalid_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    error.errors.forEach { ErrorLine(resolve(SeriesErrorText.of(it))) }
                }
            }
        }
    }
}

@Composable
private fun ErrorLine(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)

/** The "Post this series?" sheet: a scrim over the review and a panel at the bottom. Disabled while posting. */
@Composable
private fun ConfirmSheet(posting: Boolean, actions: ReviewActions) {
    val sheetTitle = stringResource(R.string.review_confirm_title)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(enabled = !posting, onClickLabel = stringResource(R.string.review_confirm_go_back), onClick = actions.onDismissConfirm).testTag(ReviewTags.Scrim).clearAndSetSemantics {})
        Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().testTag(ReviewTags.Sheet).semantics { paneTitle = sheetTitle },
            color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(Modifier.navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(sheetTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.review_confirm_body), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = actions.onConfirmPost, enabled = !posting, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(ReviewTags.SheetPost)) {
                    Text(stringResource(if (posting) R.string.review_posting else R.string.review_post))
                }
                OutlinedButton(onClick = actions.onDismissConfirm, enabled = !posting, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(ReviewTags.SheetBack)) {
                    Text(stringResource(R.string.review_confirm_go_back))
                }
            }
        }
    }
}
