package io.github.ethanscottwalden.colpool.ui.hosting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ethanscottwalden.colpool.data.series.HostedSeries
import io.github.ethanscottwalden.colpool.data.series.HostedSeriesUpdate
import io.github.ethanscottwalden.colpool.data.series.SeriesBackend
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday
import java.time.Clock
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HostingLoad { Loading, Loaded, Failed }

data class HostingUiState(
    val load: HostingLoad = HostingLoad.Loading,
    val series: List<HostedSeries> = emptyList(),
    val campusNames: Map<String, String> = emptyMap(),
    /** The series whose details are open. */
    val selectedId: String? = null,
    val activeDay: SeriesWeekday? = null,
    /** The year used to decide which dates show a year. */
    val currentYear: Int = 0,
) {
    val selected: HostedSeries? get() = series.firstOrNull { it.id == selectedId }
    override fun toString(): String = "HostingUiState(redacted)"
}

/** Hosting tab and Hosted Series Details (US-05). Read-only: the app never writes a series. */
class HostingViewModel(
    private val backend: SeriesBackend,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val _state = MutableStateFlow(HostingUiState(currentYear = HostingText.yearOf(clock)))
    val state: StateFlow<HostingUiState> = _state.asStateFlow()
    private var job: Job? = null

    /** Starts the live listener (once; [retry] restarts it). */
    fun start() {
        if (job?.isActive == true) return
        listen()
    }

    fun retry() {
        job?.cancel()
        _state.update { it.copy(load = HostingLoad.Loading) }
        listen()
    }

    private fun listen() {
        job = viewModelScope.launch {
            if (_state.value.campusNames.isEmpty()) {
                try {
                    val names = backend.loadCampusPoints().associate { it.id to it.name }
                    _state.update { it.copy(campusNames = names) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Timelines fall back to a neutral campus label.
                }
            }
            try {
                backend.observeHostedSeries().collect { update ->
                    _state.update { s ->
                        when (update) {
                            is HostedSeriesUpdate.Loaded -> {
                                val list = HostingList.forDisplay(update.series, clock)
                                val sel = list.firstOrNull { it.id == s.selectedId }
                                s.copy(
                                    load = HostingLoad.Loaded, series = list, currentYear = HostingText.yearOf(clock),
                                    selectedId = sel?.id,
                                    activeDay = if (sel == null) null else s.activeDay?.takeIf { it.id in sel.weekdayIds } ?: firstDay(sel),
                                )
                            }
                            HostedSeriesUpdate.Failed -> s.copy(load = HostingLoad.Failed)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(load = HostingLoad.Failed) }
            }
        }
    }

    private fun firstDay(s: HostedSeries) = SeriesWeekday.fromId(s.weekdayIds.first())

    fun open(id: String) = _state.update { s -> s.series.firstOrNull { it.id == id }?.let { s.copy(selectedId = id, activeDay = firstDay(it)) } ?: s }

    fun close() = _state.update { it.copy(selectedId = null, activeDay = null) }

    fun selectDay(w: SeriesWeekday) = _state.update { s -> if (s.selected?.weekdayIds?.contains(w.id) == true) s.copy(activeDay = w) else s }

    /** Stops listening and forgets everything (logging out, or leaving the student shell). */
    fun reset() {
        job?.cancel()
        job = null
        _state.value = HostingUiState(currentYear = HostingText.yearOf(clock))
    }
}
