package io.github.ethanscottwalden.colpool.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ethanscottwalden.colpool.data.model.ProfileOptions
import io.github.ethanscottwalden.colpool.data.requests.DayFailureReason
import io.github.ethanscottwalden.colpool.data.requests.HostProfile
import io.github.ethanscottwalden.colpool.data.requests.RequestBackend
import io.github.ethanscottwalden.colpool.data.requests.SendRequestResult
import io.github.ethanscottwalden.colpool.data.series.HostedSeries
import io.github.ethanscottwalden.colpool.ui.hosting.HostingText
import io.github.ethanscottwalden.colpool.ui.search.DayState
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday
import java.time.Clock
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailsUiState(
    val series: HostedSeries? = null,
    val distanceTenths: Int? = null,
    /** From Search, shown while (or if) the profile can't be read. */
    val firstNameHint: String? = null,
    val profile: HostProfile? = null,
    val profileLoaded: Boolean = false,
    val options: ProfileOptions = ProfileOptions(),
    val campusNames: Map<String, String> = emptyMap(),
    val currentYear: Int = 0,
    val activeDay: SeriesWeekday? = null,
    val dayStates: Map<SeriesWeekday, DayState> = emptyMap(),
    val standing: Standing = Standing.Requestable,
    val picked: Set<SeriesWeekday> = emptySet(),
    /** Days the server refused, with why. They are not selectable again. */
    val refused: Map<SeriesWeekday, DayFailureReason> = emptyMap(),
    val confirming: Boolean = false,
    val sending: Boolean = false,
    val error: SendError? = null,
    /** Set once when the request was sent; the root consumes it and goes back to Search. */
    val sent: Boolean = false,
) {
    val selectable: Set<SeriesWeekday> get() = DetailsRules.selectable(dayStates, refused.keys)
    val canSend: Boolean get() = standing == Standing.Requestable && picked.isNotEmpty() && !sending
    override fun toString(): String = "DetailsUiState(redacted)"
}

/** Series Details (US-11) and the request flow (US-14). Reads the host's profile; the only write is the `sendRequest` call. */
class DetailsViewModel(
    private val backend: RequestBackend,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val _state = MutableStateFlow(DetailsUiState(currentYear = HostingText.yearOf(clock)))
    val state: StateFlow<DetailsUiState> = _state.asStateFlow()
    private var job: Job? = null
    private var sendJob: Job? = null
    private var openedId: String? = null

    /** Shows [series] now and reads the host's profile, the labels, the campus names and this rider's request in the background. */
    fun open(series: HostedSeries, distanceTenths: Int?, firstNameHint: String?) {
        if (openedId == series.id) return
        reset()
        openedId = series.id
        val own = series.hostId.isNotBlank() && series.hostId == backend.currentUid()
        val states = DetailsRules.dayStates(series, clock.instant())
        _state.value = DetailsUiState(
            series = series, distanceTenths = distanceTenths, firstNameHint = firstNameHint, currentYear = HostingText.yearOf(clock),
            activeDay = SeriesWeekday.entries.first { it.id in series.weekdayIds }, dayStates = states,
            standing = if (own) Standing.Own else Standing.Requestable,
        )
        job = viewModelScope.launch {
            coroutineScope {
                launch { optional { backend.loadOptions() }?.let { o -> _state.update { it.copy(options = o) } } }
                launch { optional { backend.loadCampusNames() }?.let { n -> _state.update { it.copy(campusNames = n) } } }
                launch {
                    val p = backend.loadHostProfile(series.hostId)
                    _state.update { it.copy(profile = p, profileLoaded = true) }
                }
                if (!own) launch {
                    if (backend.hasRequested(series.id)) _state.update { if (it.standing == Standing.Requestable) it.copy(standing = Standing.AlreadyRequested, picked = emptySet(), confirming = false) else it }
                }
            }
        }
    }

    private suspend fun <T> optional(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    fun selectDay(w: SeriesWeekday) = _state.update { s -> if (s.series?.weekdayIds?.contains(w.id) == true) s.copy(activeDay = w) else s }

    fun togglePick(w: SeriesWeekday) = _state.update { s ->
        if (s.standing != Standing.Requestable || s.sending || w !in s.selectable) s
        else s.copy(picked = if (w in s.picked) s.picked - w else s.picked + w, error = null)
    }

    fun openConfirm() = _state.update { if (it.canSend) it.copy(confirming = true) else it }

    fun dismissConfirm() = _state.update { if (it.sending) it else it.copy(confirming = false) }

    /** One call per tap sequence: ignored while a call is in flight. */
    fun send() {
        val s = _state.value
        val series = s.series ?: return
        if (!s.canSend || !s.confirming) return
        val ids = SeriesWeekday.entries.filter { it in s.picked }.map { it.id }
        _state.update { it.copy(sending = true, error = null) }
        sendJob = viewModelScope.launch {
            val result = try {
                backend.sendRequest(series.id, ids)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SendRequestResult.Failed
            }
            _state.update { apply(it, result) }
        }
    }

    private fun apply(s: DetailsUiState, r: SendRequestResult): DetailsUiState {
        val done = s.copy(sending = false, confirming = false)
        return when (r) {
            SendRequestResult.Sent -> done.copy(sent = true)
            SendRequestResult.Invalid -> done.copy(error = SendError.Invalid)
            SendRequestResult.Failed -> done.copy(error = SendError.Retry)
            SendRequestResult.NotAvailable -> done.copy(standing = Standing.Gone, picked = emptySet(), error = SendError.Gone)
            SendRequestResult.OwnSeries -> done.copy(standing = Standing.Own, picked = emptySet(), error = SendError.OwnSeries)
            SendRequestResult.AlreadyRequested -> done.copy(standing = Standing.AlreadyRequested, picked = emptySet(), error = null)
            is SendRequestResult.WeekdaysUnavailable -> {
                val failed = r.failures.mapNotNull { f -> SeriesWeekday.fromId(f.weekdayId)?.let { it to f.reason } }.toMap()
                // Failed days are marked and deselected; the others stay picked so the rider can send again.
                done.copy(refused = s.refused + failed, picked = s.picked - failed.keys, error = SendError.SomeDays)
            }
        }
    }

    /** The root has handled [DetailsUiState.sent]. */
    fun consumeSent() = _state.update { it.copy(sent = false) }

    /** Forgets everything (leaving the screen, logging out). */
    fun reset() {
        job?.cancel()
        sendJob?.cancel()
        job = null
        sendJob = null
        openedId = null
        _state.value = DetailsUiState(currentYear = HostingText.yearOf(clock))
    }
}
