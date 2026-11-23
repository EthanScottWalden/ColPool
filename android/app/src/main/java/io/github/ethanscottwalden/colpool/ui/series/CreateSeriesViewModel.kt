package io.github.ethanscottwalden.colpool.ui.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ethanscottwalden.colpool.data.places.SelectedAddress
import io.github.ethanscottwalden.colpool.data.series.RoutePreviewResult
import io.github.ethanscottwalden.colpool.data.series.SeriesBackend
import io.github.ethanscottwalden.colpool.data.series.SeriesError
import io.github.ethanscottwalden.colpool.ui.profile.OptionsStatus
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

val PacificZone: ZoneId = ZoneId.of("America/Los_Angeles")

/**
 * Create Series form (US-03). Continue runs the client pre-checks, then `routePreview`; success sets
 * [CreateSeriesUiState.reviewDraftId] so the screen can open Review (#31). The backend stays authoritative.
 */
class CreateSeriesViewModel(
    private val backend: SeriesBackend,
    private val todayPacific: () -> LocalDate = { LocalDate.now(PacificZone) },
) : ViewModel() {
    private val _state = MutableStateFlow(CreateSeriesUiState())
    val state: StateFlow<CreateSeriesUiState> = _state.asStateFlow()
    private var generation = 0

    /** Pacific "today": the earliest date the pickers allow. */
    fun today(): LocalDate = todayPacific()

    fun loadCampusPoints() {
        val mine = ++generation
        _state.update { it.copy(campusStatus = OptionsStatus.Loading) }
        viewModelScope.launch {
            try {
                val points = backend.loadCampusPoints()
                if (mine == generation) _state.update { it.copy(campusStatus = OptionsStatus.Loaded, campusPoints = points) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (mine == generation) _state.update { it.copy(campusStatus = OptionsStatus.Failed) }
            }
        }
    }

    /** Clears the form (opening it fresh, or after log out). */
    fun reset() {
        generation++
        _state.value = CreateSeriesUiState()
    }

    private fun edit(vararg clearPaths: String, block: (CreateSeriesUiState) -> CreateSeriesUiState) = _state.update {
        val next = block(it)
        next.copy(errors = next.errors.filterNot { e -> clearPaths.any { p -> e.path == p || e.path.startsWith("$p.") } })
    }

    fun setPickup(a: SelectedAddress) = edit("pickupSpot") { it.copy(pickup = a) }
    fun setStartDate(iso: String) = edit("startDate", "endDate", "") { it.copy(startDate = iso) }
    fun setEndDate(iso: String) = edit("endDate", "startDate", "") { it.copy(endDate = iso) }

    fun setPrice(v: String) = edit("priceDollars") { it.copy(price = v.filter { c -> c in '0'..'9' }.take(3)) }

    fun setRiders(n: Int) = edit("maxRiders") { it.copy(riders = n.coerceIn(SeriesLimits.RIDERS_MIN, SeriesLimits.RIDERS_MAX)) }

    /** Input beyond 60 characters is refused (RC-30). */
    fun setVehicle(v: String) = edit("vehicle") { if (v.length > SeriesLimits.VEHICLE_MAX) it else it.copy(vehicle = v) }

    fun toggleWeekday(w: SeriesWeekday) = edit("weekdays", "") { s ->
        if (w in s.weekdays) {
            val left = s.weekdays - w
            s.copy(
                weekdays = left,
                days = s.days - w,
                activeDay = if (s.activeDay == w) SeriesWeekday.entries.firstOrNull { it in left } else s.activeDay,
            )
        } else {
            s.copy(weekdays = s.weekdays + w, days = s.days + (w to DayForm()), activeDay = w)
        }
    }

    fun selectDay(w: SeriesWeekday) = _state.update { if (w in it.weekdays) it.copy(activeDay = w) else it }

    private fun editLeg(w: SeriesWeekday, d: Direction, clear: String, block: (LegForm) -> LegForm) {
        val base = "days.${w.id}.${d.id}"
        edit("$base.$clear") { s ->
            if (w !in s.weekdays) s else {
                val day = s.day(w)
                s.copy(days = s.days + (w to day.withLeg(d, block(day.leg(d)))))
            }
        }
    }

    fun setDepartTime(w: SeriesWeekday, d: Direction, minutes: Int) =
        editLeg(w, d, "departTime") { it.copy(departMinutes = minutes.coerceIn(0, 24 * 60 - 1)) }

    fun setCampusPoint(w: SeriesWeekday, d: Direction, id: String) =
        editLeg(w, d, "campusPointId") { if (_state.value.campusPoints.any { p -> p.id == id }) it.copy(campusPointId = id) else it }

    /** Adds a stop at the end. The 11th stop in a direction is refused. */
    fun addStop(w: SeriesWeekday, d: Direction, stop: StopForm) = editLeg(w, d, "stops") { leg ->
        if (leg.stops.size >= SeriesLimits.MAX_STOPS_PER_DIRECTION) leg
        else leg.copy(stops = leg.stops + stop.copy(minutes = stop.minutes.coerceIn(SeriesLimits.STOP_MINUTES_MIN, SeriesLimits.STOP_MINUTES_MAX)))
    }

    fun removeStop(w: SeriesWeekday, d: Direction, index: Int) = editLeg(w, d, "stops") { leg ->
        if (index !in leg.stops.indices) leg else leg.copy(stops = leg.stops.filterIndexed { i, _ -> i != index })
    }

    fun submit() {
        val s = _state.value
        if (s.submitting) return
        val local = SeriesChecks.run(s, todayPacific())
        if (local.isNotEmpty()) {
            showErrors(local)
            return
        }
        val payload = SeriesPayload.build(s)
        _state.update { it.copy(submitting = true, errors = emptyList()) }
        viewModelScope.launch {
            suspend fun call(draftId: String?) = try {
                backend.routePreview(payload, draftId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RoutePreviewResult.Failed
            }
            val sent = s.draftId
            var result = call(sent)
            // The old draft is gone or already posted: forget it and retry once as a new draft.
            if (result == RoutePreviewResult.DraftRejected && sent != null) {
                _state.update { it.copy(draftId = null) }
                result = call(null)
            }
            when (result) {
                is RoutePreviewResult.Success -> _state.update { it.copy(submitting = false, draftId = result.draftId, reviewDraftId = result.draftId) }
                is RoutePreviewResult.Invalid -> showErrors(result.errors)
                RoutePreviewResult.Unavailable -> showErrors(listOf(SeriesError("unavailable", "")))
                RoutePreviewResult.Failed, RoutePreviewResult.DraftRejected -> showErrors(listOf(SeriesError("internal", "")))
            }
        }
    }

    /** Shows the errors and jumps the schedule card to the first weekday that has one. */
    private fun showErrors(errors: List<SeriesError>) = _state.update { s ->
        val day = errors.firstNotNullOfOrNull { SeriesErrorText.weekdayOf(it.path) }?.takeIf { it in s.weekdays }
        s.copy(submitting = false, errors = errors, activeDay = day ?: s.activeDay)
    }

    /** The screen has opened Review. */
    fun consumeReview() = _state.update { it.copy(reviewDraftId = null) }
}
