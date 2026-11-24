package io.github.ethanscottwalden.colpool.ui.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ethanscottwalden.colpool.R
import io.github.ethanscottwalden.colpool.data.series.CampusPoint
import io.github.ethanscottwalden.colpool.data.series.SeriesError
import io.github.ethanscottwalden.colpool.ui.address.AddressSearchSheet
import io.github.ethanscottwalden.colpool.ui.address.AddressSearchViewModel
import io.github.ethanscottwalden.colpool.ui.profile.FormErrorText
import io.github.ethanscottwalden.colpool.ui.profile.FormLabel
import io.github.ethanscottwalden.colpool.ui.profile.OptionsStatus
import java.time.LocalDate

object SeriesTags {
    const val Pickup = "series_pickup"
    const val StartDate = "series_start_date"
    const val EndDate = "series_end_date"
    const val Price = "series_price"
    const val RidersLess = "series_riders_less"
    const val RidersMore = "series_riders_more"
    const val Vehicle = "series_vehicle"
    const val Continue = "series_continue"
    const val ErrorBox = "series_error_box"
    const val Back = "series_back"
    fun weekday(w: SeriesWeekday) = "series_weekday_${w.id}"
    fun addStop(d: Direction) = "series_add_stop_${d.id}"
    fun depart(d: Direction) = "series_depart_${d.id}"
    fun campusPoint(d: Direction, id: String) = "series_campus_${d.id}_$id"
}

class CreateSeriesActions(
    val onBack: () -> Unit = {},
    val onChoosePickup: () -> Unit = {},
    val onStartDate: (String) -> Unit = {},
    val onEndDate: (String) -> Unit = {},
    val onPrice: (String) -> Unit = {},
    val onRiders: (Int) -> Unit = {},
    val onVehicle: (String) -> Unit = {},
    val onToggleWeekday: (SeriesWeekday) -> Unit = {},
    val onSelectDay: (SeriesWeekday) -> Unit = {},
    val onDepartTime: (SeriesWeekday, Direction, Int) -> Unit = { _, _, _ -> },
    val onCampusPoint: (SeriesWeekday, Direction, String) -> Unit = { _, _, _ -> },
    val onAddStop: (SeriesWeekday, Direction) -> Unit = { _, _ -> },
    val onRemoveStop: (SeriesWeekday, Direction, Int) -> Unit = { _, _, _ -> },
    val onRetryCampusPoints: () -> Unit = {},
    val onContinue: () -> Unit = {},
)

/** Which address the open search sheet is for. */
private sealed interface SheetTarget {
    data object Pickup : SheetTarget
    data class Stop(val day: SeriesWeekday, val dir: Direction) : SheetTarget
}

/**
 * Stateful wrapper: owns the address sheets. [onReview] opens Review with the new draft id (#31 builds that screen).
 * A fresh address entry starts each time a sheet opens.
 */
@Composable
fun CreateSeriesScreen(
    viewModel: CreateSeriesViewModel,
    addressSearch: AddressSearchViewModel,
    onReview: (draftId: String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheetPickup by rememberSaveable { mutableStateOf(false) }
    var stopDay by rememberSaveable { mutableStateOf<String?>(null) }
    var stopDir by rememberSaveable { mutableStateOf<String?>(null) }
    var stopMinutes by rememberSaveable { mutableStateOf(DEFAULT_STOP_MINUTES.toString()) }
    val stopTarget = SeriesWeekday.fromId(stopDay)?.let { d -> Direction.fromId(stopDir)?.let { SheetTarget.Stop(d, it) } }

    LaunchedEffect(Unit) { if (state.campusStatus != OptionsStatus.Loaded) viewModel.loadCampusPoints() }
    LaunchedEffect(state.reviewDraftId) {
        state.reviewDraftId?.let { viewModel.consumeReview(); onReview(it) }
    }
    BackHandler(onBack = onBack)

    CreateSeriesContent(
        state = state,
        today = viewModel.today(),
        actions = CreateSeriesActions(
            onBack = onBack,
            onChoosePickup = { addressSearch.startNewEntry(); sheetPickup = true },
            onStartDate = viewModel::setStartDate,
            onEndDate = viewModel::setEndDate,
            onPrice = viewModel::setPrice,
            onRiders = viewModel::setRiders,
            onVehicle = viewModel::setVehicle,
            onToggleWeekday = viewModel::toggleWeekday,
            onSelectDay = viewModel::selectDay,
            onDepartTime = viewModel::setDepartTime,
            onCampusPoint = viewModel::setCampusPoint,
            onAddStop = { w, d -> addressSearch.startNewEntry(); stopMinutes = DEFAULT_STOP_MINUTES.toString(); stopDay = w.id; stopDir = d.id },
            onRemoveStop = viewModel::removeStop,
            onRetryCampusPoints = viewModel::loadCampusPoints,
            onContinue = viewModel::submit,
        ),
    )
    if (sheetPickup) {
        AddressSearchSheet(
            title = stringResource(R.string.series_pickup_sheet_title),
            viewModel = addressSearch,
            onConfirm = { viewModel.setPickup(it); sheetPickup = false },
            onDismiss = { sheetPickup = false },
            notice = { PublicPlaceNote(stringResource(R.string.series_pickup_hint)) },
        )
    }
    if (stopTarget != null) {
        AddressSearchSheet(
            title = stringResource(R.string.series_add_stop_title),
            viewModel = addressSearch,
            onConfirm = {
                viewModel.addStop(stopTarget.day, stopTarget.dir, StopForm(it, parseStopMinutes(stopMinutes)))
                stopDay = null; stopDir = null
            },
            onDismiss = { stopDay = null; stopDir = null },
            notice = { AddStopNotice(stopMinutes) { stopMinutes = it } },
        )
    }
}

private const val DEFAULT_STOP_MINUTES = 5

/** Blank counts as 0 minutes; digits beyond 120 are refused as typed, so the result is always 0..120. */
fun parseStopMinutes(text: String): Int = (text.trim().toIntOrNull() ?: 0).coerceIn(SeriesLimits.STOP_MINUTES_MIN, SeriesLimits.STOP_MINUTES_MAX)

/** The minutes field and the stops note, shown above the address search in the Add a stop sheet. */
@Composable
fun AddStopNotice(minutes: String, onMinutes: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PublicPlaceNote(stringResource(R.string.series_stop_note))
        Text(stringResource(R.string.series_stop_minutes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        OutlinedTextField(
            value = minutes,
            onValueChange = { v ->
                val digits = v.filter { it in '0'..'9' }.take(3)
                // A value above 120 is refused as typed (RC-30).
                if (digits.isEmpty() || digits.toInt() <= SeriesLimits.STOP_MINUTES_MAX) onMinutes(digits)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(160.dp).testTag("series_stop_minutes"),
        )
        Text(stringResource(R.string.series_stop_minutes_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PublicPlaceNote(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
        Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateSeriesContent(state: CreateSeriesUiState, today: LocalDate, actions: CreateSeriesActions, modifier: Modifier = Modifier) {
    var datePicker by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end
    var timePicker by remember { mutableStateOf<Pair<SeriesWeekday, Direction>?>(null) }
    val backCd = stringResource(R.string.series_back)
    val lessCd = stringResource(R.string.series_riders_less)
    val moreCd = stringResource(R.string.series_riders_more)
    val boxErrors = state.errors.filter { it.path.isEmpty() || !SeriesErrorText.hasInlineSpot(it.path) }
    val scroll = rememberScrollState()
    // The summary box is at the top: bring it into view when it appears.
    LaunchedEffect(boxErrors) { if (boxErrors.isNotEmpty()) scroll.animateScrollTo(0) }

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.imePadding()) {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).statusBarsPadding().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = actions.onBack, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag(SeriesTags.Back), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text("←", Modifier.semantics { contentDescription = backCd }, style = MaterialTheme.typography.titleLarge)
                }
                Text(stringResource(R.string.series_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).verticalScroll(scroll).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (boxErrors.isNotEmpty()) ErrorBox(boxErrors)

                FormLabel(R.string.series_pickup_label)
                PickerButton(
                    text = state.pickup?.text, placeholder = stringResource(R.string.series_pickup_placeholder),
                    isError = state.errorsAt("pickupSpot").isNotEmpty(), onClick = actions.onChoosePickup, tag = SeriesTags.Pickup,
                )
                ErrorsAt(state, "pickupSpot")
                Text(stringResource(R.string.series_pickup_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        FormLabel(R.string.series_start_date)
                        PickerButton(state.startDate?.let(SeriesDates::display), stringResource(R.string.series_date_placeholder), state.errorsAt("startDate").isNotEmpty(), { datePicker = true }, SeriesTags.StartDate)
                        ErrorsAt(state, "startDate")
                    }
                    Column(Modifier.weight(1f)) {
                        FormLabel(R.string.series_end_date)
                        PickerButton(state.endDate?.let(SeriesDates::display), stringResource(R.string.series_date_placeholder), state.errorsAt("endDate").isNotEmpty(), { datePicker = false }, SeriesTags.EndDate)
                        ErrorsAt(state, "endDate")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        FormLabel(R.string.series_price)
                        OutlinedTextField(
                            value = state.price, onValueChange = actions.onPrice, singleLine = true,
                            prefix = { Text(stringResource(R.string.series_price_prefix)) },
                            isError = state.errorsAt("priceDollars").isNotEmpty(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag(SeriesTags.Price),
                        )
                        ErrorsAt(state, "priceDollars")
                    }
                    Column(Modifier.weight(1f)) {
                        FormLabel(R.string.series_riders)
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Button(onClick = { actions.onRiders(state.riders - 1) }, enabled = state.riders > SeriesLimits.RIDERS_MIN, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag(SeriesTags.RidersLess).semantics { contentDescription = lessCd }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("−") }
                            Text(state.riders.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Button(onClick = { actions.onRiders(state.riders + 1) }, enabled = state.riders < SeriesLimits.RIDERS_MAX, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag(SeriesTags.RidersMore).semantics { contentDescription = moreCd }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("+") }
                        }
                        ErrorsAt(state, "maxRiders")
                    }
                }

                FormLabel(R.string.series_vehicle)
                OutlinedTextField(
                    value = state.vehicle, onValueChange = actions.onVehicle, singleLine = true,
                    placeholder = { Text(stringResource(R.string.series_vehicle_placeholder)) },
                    isError = state.errorsAt("vehicle").isNotEmpty(),
                    supportingText = { Text(stringResource(R.string.series_vehicle_count, state.vehicle.length, SeriesLimits.VEHICLE_MAX), Modifier.fillMaxWidth(), textAlign = TextAlign.End) },
                    modifier = Modifier.fillMaxWidth().testTag(SeriesTags.Vehicle),
                )
                ErrorsAt(state, "vehicle")

                FormLabel(R.string.series_weekdays)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SeriesWeekday.entries.forEach { w ->
                        DayChip(
                            selected = w in state.weekdays, enabled = true, onClick = { actions.onToggleWeekday(w) },
                            title = stringResource(w.shortName),
                            sub = if (w in state.weekdays) stringResource(R.string.series_day_driving) else null,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(SeriesTags.weekday(w)),
                            role = Role.Checkbox,
                        )
                    }
                }
                ErrorsAt(state, "weekdays")

                val active = state.activeDay?.takeIf { it in state.weekdays }
                if (active != null) {
                    ScheduleCard(state, active, actions, onPickTime = { w, d -> timePicker = w to d })
                }

                Button(
                    onClick = actions.onContinue,
                    enabled = !state.submitting,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(SeriesTags.Continue),
                ) { Text(stringResource(if (state.submitting) R.string.series_checking else R.string.series_continue)) }
            }
        }
    }

    datePicker?.let { isStart ->
        val initial = (if (isStart) state.startDate else state.endDate)?.let(SeriesDates::toPickerMillis)
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initial,
            selectableDates = object : SelectableDates {
                // Past dates are disabled (the server stays authoritative). The end date can't precede the start date.
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val d = LocalDate.parse(SeriesDates.fromPickerMillis(utcTimeMillis))
                    val floor = if (!isStart) state.startDate?.let { LocalDate.parse(it) }?.takeIf { it.isAfter(today) } ?: today else today
                    return !d.isBefore(floor)
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { datePicker = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms -> SeriesDates.fromPickerMillis(ms).let { if (isStart) actions.onStartDate(it) else actions.onEndDate(it) } }
                    datePicker = null
                }) { Text(stringResource(R.string.series_date_ok)) }
            },
            dismissButton = { TextButton(onClick = { datePicker = null }) { Text(stringResource(R.string.series_cancel)) } },
        ) { DatePicker(pickerState) }
    }

    timePicker?.let { (w, d) ->
        val current = state.day(w).leg(d).departMinutes
        val pickerState = rememberTimePickerState(initialHour = (current ?: 7 * 60 + 30) / 60, initialMinute = (current ?: 7 * 60 + 30) % 60, is24Hour = false)
        AlertDialog(
            onDismissRequest = { timePicker = null },
            confirmButton = {
                TextButton(onClick = { actions.onDepartTime(w, d, SeriesTime.fromPicker(pickerState.hour, pickerState.minute)); timePicker = null }) { Text(stringResource(R.string.series_time_ok)) }
            },
            dismissButton = { TextButton(onClick = { timePicker = null }) { Text(stringResource(R.string.series_cancel)) } },
            text = { TimePicker(pickerState) },
        )
    }
}

@Composable
private fun ErrorBox(errors: List<SeriesError>) {
    Surface(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.testTag(SeriesTags.ErrorBox),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.series_errors_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
            errors.forEach { Text(resolve(SeriesErrorText.of(it)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer) }
        }
    }
}

@Composable
internal fun resolve(t: UiText): String {
    val args = t.args.map { if (it is StringRef) stringResource(it.id) else it }
    return stringResource(t.res, *args.toTypedArray())
}

/** Inline errors for exactly [path]. */
@Composable
private fun ErrorsAt(state: CreateSeriesUiState, path: String) {
    state.errorsAt(path).forEach { FormErrorText(resolve(SeriesErrorText.of(it))) }
}

@Composable
private fun PickerButton(text: String?, placeholder: String, isError: Boolean, onClick: () -> Unit, tag: String) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(tag),
        border = BorderStroke(if (isError) 2.dp else 1.dp, if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline),
    ) {
        Text(
            text ?: placeholder, Modifier.fillMaxWidth(), textAlign = TextAlign.Start, maxLines = 2,
            color = if (text == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleCard(state: CreateSeriesUiState, active: SeriesWeekday, actions: CreateSeriesActions, onPickTime: (SeriesWeekday, Direction) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.series_schedule_title, stringResource(active.fullName)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SeriesWeekday.entries.forEach { w ->
                    val on = w in state.weekdays
                    DayChip(
                        selected = w == active, enabled = on, onClick = { actions.onSelectDay(w) },
                        title = stringResource(w.shortName),
                        sub = if (on) stringResource(if (state.day(w).complete) R.string.series_day_done else R.string.series_day_todo) else null,
                        modifier = Modifier.weight(1f),
                        role = Role.Tab,
                    )
                }
            }
            ErrorsAt(state, "days.${active.id}")
            Direction.entries.forEach { dir -> LegSection(state, active, dir, actions, onPickTime) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LegSection(state: CreateSeriesUiState, w: SeriesWeekday, dir: Direction, actions: CreateSeriesActions, onPickTime: (SeriesWeekday, Direction) -> Unit) {
    val leg = state.day(w).leg(dir)
    val base = "days.${w.id}.${dir.id}"
    val toCampus = dir == Direction.ToCampus
    val removeCd = stringResource(R.string.series_stop_remove)
    @Composable fun timeField() {
        FormLabel(if (toCampus) R.string.series_leave_pickup else R.string.series_leave_campus)
        PickerButton(leg.departMinutes?.let(SeriesTime::format12), stringResource(R.string.series_time_placeholder), state.errorsAt("$base.departTime").isNotEmpty(), { onPickTime(w, dir) }, SeriesTags.depart(dir))
        ErrorsAt(state, "$base.departTime")
    }
    @Composable fun campusChips() {
        FormLabel(if (toCampus) R.string.series_dropoff_point else R.string.series_pickup_point)
        when (state.campusStatus) {
            OptionsStatus.Loading -> Text(stringResource(R.string.series_checking), style = MaterialTheme.typography.bodySmall)
            OptionsStatus.Failed -> {
                FormErrorText(stringResource(R.string.series_campus_failed))
                OutlinedButton(onClick = actions.onRetryCampusPoints) { Text(stringResource(R.string.session_retry)) }
            }
            OptionsStatus.Loaded -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.campusPoints.forEach { p: CampusPoint ->
                    FilterChip(selected = leg.campusPointId == p.id, onClick = { actions.onCampusPoint(w, dir, p.id) }, label = { Text(p.name) }, modifier = Modifier.testTag(SeriesTags.campusPoint(dir, p.id)))
                }
            }
        }
        ErrorsAt(state, "$base.campusPointId")
    }
    @Composable fun stops() {
        leg.stops.forEachIndexed { i, stop ->
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(stop.address.text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.series_stop_summary, stop.minutes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { actions.onRemoveStop(w, dir, i) }, modifier = Modifier.semantics { contentDescription = removeCd }) { Text("✕") }
                }
            }
            ErrorsAt(state, "$base.stops.$i.address")
            ErrorsAt(state, "$base.stops.$i.minutes")
        }
        ErrorsAt(state, "$base.stops")
        if (leg.stops.size >= SeriesLimits.MAX_STOPS_PER_DIRECTION) {
            Text(stringResource(R.string.series_stops_full, SeriesLimits.MAX_STOPS_PER_DIRECTION), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            OutlinedButton(onClick = { actions.onAddStop(w, dir) }, modifier = Modifier.fillMaxWidth().testTag(SeriesTags.addStop(dir))) { Text("+  " + stringResource(R.string.series_add_stop)) }
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(if (toCampus) R.string.series_to_campus else R.string.series_from_campus).uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ErrorsAt(state, base)
            if (toCampus) { timeField(); stops(); campusChips() } else { campusChips(); timeField(); stops() }
        }
    }
}

/** A weekday chip with an optional second line ("driving", "done"). Own padding so the labels fit five across a phone. */
@Composable
internal fun DayChip(
    selected: Boolean, enabled: Boolean, onClick: () -> Unit, title: String, sub: String?, modifier: Modifier = Modifier,
    /** [Role.Tab] where the chips switch the day shown (selected is announced); [Role.Checkbox] where they toggle days. Null keeps the default. */
    role: Role? = null,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics {
            if (role != null) this.role = role
            if (role == Role.Tab) this.selected = selected
            if (role == Role.Checkbox) toggleableState = if (selected) ToggleableState.On else ToggleableState.Off
        },
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge)
            if (sub != null) Text(sub, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.sp))
        }
    }
}
