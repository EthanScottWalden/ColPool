package io.github.ethanscottwalden.colpool.ui.hosting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ethanscottwalden.colpool.data.model.ProfileOptions
import io.github.ethanscottwalden.colpool.data.requests.HostProfile
import io.github.ethanscottwalden.colpool.data.requests.HostRequestsBackend
import io.github.ethanscottwalden.colpool.data.requests.IncomingRequest
import io.github.ethanscottwalden.colpool.data.requests.RequestsUpdate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A requester's profile as the host sees it. [profile] is null when it can't be read. */
data class RequesterProfileState(val riderId: String, val profile: HostProfile? = null, val loaded: Boolean = false)

data class HostRequestsUiState(
    /** Every request on the host's series (for the Hosting tab badge). */
    val all: List<IncomingRequest> = emptyList(),
    /** The open series' requests, oldest first, with at least one pending day. */
    val forSeries: List<IncomingRequest> = emptyList(),
    val seriesLoaded: Boolean = false,
    /** Rider id to the profile that was read; null when unreadable. */
    val names: Map<String, HostProfile?> = emptyMap(),
    val options: ProfileOptions = ProfileOptions(),
    /** The requester whose profile is open. */
    val opened: RequesterProfileState? = null,
) {
    override fun toString(): String = "HostRequestsUiState(redacted)"
}

/** The host's incoming requests (US-07) and requester profiles (RC-01). Read-only: accept and decline are #43. */
class HostRequestsViewModel(private val backend: HostRequestsBackend) : ViewModel() {
    private val _state = MutableStateFlow(HostRequestsUiState())
    val state: StateFlow<HostRequestsUiState> = _state.asStateFlow()
    private var allJob: Job? = null
    private var seriesJob: Job? = null
    private var openedSeriesId: String? = null
    private val requested = mutableSetOf<String>()

    /** Starts the all-requests listener (once) used for the badge. */
    fun start() {
        if (allJob?.isActive == true) return
        allJob = viewModelScope.launch {
            try {
                backend.observeRequests(null).collect { u ->
                    if (u is RequestsUpdate.Loaded) _state.update { it.copy(all = u.requests) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The badge just stays as it was.
            }
        }
    }

    /** Listens to one series' requests while its details are open. */
    fun openSeries(seriesId: String) {
        if (openedSeriesId == seriesId && seriesJob?.isActive == true) return
        closeSeries()
        openedSeriesId = seriesId
        seriesJob = viewModelScope.launch {
            try {
                backend.observeRequests(seriesId).collect { u ->
                    when (u) {
                        is RequestsUpdate.Loaded -> {
                            val list = HostRequestRules.listed(u.requests, seriesId)
                            _state.update { it.copy(forSeries = list, seriesLoaded = true) }
                            loadNames(list)
                        }
                        RequestsUpdate.Failed -> _state.update { it.copy(forSeries = emptyList(), seriesLoaded = true) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(seriesLoaded = true) }
            }
        }
    }

    fun closeSeries() {
        seriesJob?.cancel()
        seriesJob = null
        openedSeriesId = null
        _state.update { it.copy(forSeries = emptyList(), seriesLoaded = false, opened = null) }
    }

    private fun loadNames(list: List<IncomingRequest>) {
        list.map { it.riderId }.filter { requested.add(it) }.forEach { rider ->
            viewModelScope.launch {
                val p = backend.loadRequesterProfile(rider)
                _state.update { it.copy(names = it.names + (rider to p)) }
            }
        }
    }

    /** Opens the requester's profile (only reachable from a request row). */
    fun openProfile(riderId: String) {
        _state.update { it.copy(opened = RequesterProfileState(riderId, it.names[riderId], it.names.containsKey(riderId))) }
        viewModelScope.launch {
            if (_state.value.options.genders.isEmpty()) {
                try {
                    val o = backend.loadOptions()
                    _state.update { it.copy(options = o) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Optional fields without labels are skipped.
                }
            }
            val p = backend.loadRequesterProfile(riderId)
            _state.update { s ->
                if (s.opened?.riderId == riderId) s.copy(opened = RequesterProfileState(riderId, p, true), names = s.names + (riderId to p)) else s
            }
        }
    }

    fun closeProfile() = _state.update { it.copy(opened = null) }

    /** Stops listening and forgets everything (logging out, or leaving the student shell). */
    fun reset() {
        allJob?.cancel()
        seriesJob?.cancel()
        allJob = null
        seriesJob = null
        openedSeriesId = null
        requested.clear()
        _state.value = HostRequestsUiState()
    }
}
