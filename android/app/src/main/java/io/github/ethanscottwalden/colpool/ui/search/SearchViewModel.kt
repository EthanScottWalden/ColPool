package io.github.ethanscottwalden.colpool.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ethanscottwalden.colpool.data.search.SearchBackend
import io.github.ethanscottwalden.colpool.ui.hosting.HostingText
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday
import java.time.Clock
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchLoad { Idle, Loading, Loaded, Failed }

data class SearchUiState(
    val picked: Set<SeriesWeekday> = emptySet(),
    val load: SearchLoad = SearchLoad.Idle,
    val results: List<SearchResult> = emptyList(),
    /** First names of the hosts shown (null value: not readable). */
    val hostNames: Map<String, String?> = emptyMap(),
    /** False when the rider has no starting coordinates: no distances and no ranking. */
    val hasStart: Boolean = true,
    val currentYear: Int = 0,
    /** The series whose details are open. Never shown. */
    val openedSeriesId: String? = null,
    /** True after a request was sent from the details screen: a "Request sent" banner shows until dismissed. */
    val requestSent: Boolean = false,
) {
    override fun toString(): String = "SearchUiState(redacted)"
}

/** Search (US-09): one series query, one requests list and the hosts' first names for the series shown. Read-only. */
class SearchViewModel(
    private val backend: SearchBackend,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState(currentYear = HostingText.yearOf(clock)))
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var job: Job? = null
    private val nameCache = HashMap<String, String?>()

    fun toggle(w: SeriesWeekday) {
        val picked = _state.value.picked.let { if (w in it) it - w else it + w }
        _state.update { it.copy(picked = picked, requestSent = false) }
        if (picked.isEmpty()) {
            job?.cancel()
            _state.update { it.copy(load = SearchLoad.Idle, results = emptyList()) }
        } else {
            run(showLoading = true)
        }
    }

    fun retry() = run(showLoading = true)

    /** Called when the Search tab is shown again: reads fresh results without blanking the old ones. */
    fun refresh() {
        if (_state.value.picked.isNotEmpty() && _state.value.load != SearchLoad.Loading) run(showLoading = false)
    }

    fun open(id: String) = _state.update { s -> if (s.results.any { it.series.id == id }) s.copy(openedSeriesId = id) else s }

    fun close() = _state.update { it.copy(openedSeriesId = null) }

    /** A request was sent (#38): back to the list with a confirmation, and the series is gone from the fresh results (RC-16). */
    fun requestSent() {
        _state.update { it.copy(openedSeriesId = null, requestSent = true) }
        refresh()
    }

    fun dismissRequestSent() = _state.update { it.copy(requestSent = false) }

    /** Forgets everything (logging out, or leaving the student shell). */
    fun reset() {
        job?.cancel()
        job = null
        nameCache.clear()
        _state.value = SearchUiState(currentYear = HostingText.yearOf(clock))
    }

    private fun run(showLoading: Boolean) {
        job?.cancel()
        val picked = _state.value.picked
        if (showLoading) _state.update { it.copy(load = SearchLoad.Loading) }
        job = viewModelScope.launch {
            try {
                val ids = SeriesWeekday.entries.filter { it in picked }.map { it.id }
                val (series, start, requested) = coroutineScope {
                    val s = async { backend.searchSeries(ids) }
                    val p = async { backend.loadStartingPoint() }
                    val r = async { backend.loadRequestedSeriesIds() }
                    Triple(s.await(), p.await(), r.await())
                }
                val results = SearchRules.rank(series, picked, clock.instant(), backend.currentUid(), requested, start)
                val missing = results.map { it.series.hostId }.distinct().filter { it !in nameCache }
                coroutineScope { missing.map { h -> async { nameCache[h] = backend.loadHostFirstName(h) } }.awaitAll() }
                _state.update {
                    it.copy(
                        load = SearchLoad.Loaded, results = results, hasStart = start != null,
                        hostNames = results.associate { r -> r.series.hostId to nameCache[r.series.hostId] },
                        currentYear = HostingText.yearOf(clock),
                        openedSeriesId = it.openedSeriesId?.takeIf { id -> results.any { r -> r.series.id == id } },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(load = SearchLoad.Failed) }
            }
        }
    }
}
