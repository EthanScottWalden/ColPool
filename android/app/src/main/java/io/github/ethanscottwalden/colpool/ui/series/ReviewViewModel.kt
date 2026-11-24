package io.github.ethanscottwalden.colpool.ui.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ethanscottwalden.colpool.data.series.DraftLoadResult
import io.github.ethanscottwalden.colpool.data.series.PostSeriesResult
import io.github.ethanscottwalden.colpool.data.series.ReviewDraft
import io.github.ethanscottwalden.colpool.data.series.SeriesBackend
import io.github.ethanscottwalden.colpool.data.series.SeriesError
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReviewLoad { Loading, Loaded, NotFound, Failed }

/** Why the last post did not finish. Never shows a backend code. */
sealed interface PostError {
    /** The draft is gone: nothing to retry; go back and review again. */
    data object NotFound : PostError

    /** Busy, offline or unknown: stay here and try again. */
    data object Retry : PostError

    /** The backend refused the series (ADR 0007 codes): go back and fix it. */
    data class Invalid(val errors: List<SeriesError>) : PostError
}

/** Where Review hands off to. The screen calls [ReviewViewModel.consumeExit] once the root has acted. */
sealed interface ReviewExit {
    /** Posted, or it already was (`alreadyPosted`): on to the Hosting tab. */
    data object Posted : ReviewExit

    /** Back to Create Series with the form and draft kept; [errors] show there too. */
    data class Edit(val errors: List<SeriesError> = emptyList()) : ReviewExit
}

data class ReviewUiState(
    val load: ReviewLoad = ReviewLoad.Loading,
    val draft: ReviewDraft? = null,
    val campusNames: Map<String, String> = emptyMap(),
    val activeDay: SeriesWeekday? = null,
    val confirming: Boolean = false,
    val posting: Boolean = false,
    val postError: PostError? = null,
    val exit: ReviewExit? = null,
) {
    override fun toString(): String = "ReviewUiState(redacted)"
}

/**
 * Review Calculated Route (US-04). Reads the stored draft so the host sees exactly what was saved (RC-12), then
 * posts it with the stored input. The app never sends routes or times.
 */
class ReviewViewModel(private val backend: SeriesBackend) : ViewModel() {
    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()
    private var draftId: String? = null
    private var generation = 0

    /** Loads the draft once per id; [retry] forces a reload. */
    fun load(id: String) {
        if (draftId == id && _state.value.load == ReviewLoad.Loaded) return
        fetch(id)
    }

    fun retry() { draftId?.let { fetch(it) } }

    private fun fetch(id: String) {
        draftId = id
        val mine = ++generation
        _state.value = ReviewUiState()
        viewModelScope.launch {
            val next = try {
                when (val r = backend.loadDraft(id)) {
                    is DraftLoadResult.Loaded -> {
                        val names = backend.loadCampusPoints().associate { it.id to it.name }
                        ReviewUiState(load = ReviewLoad.Loaded, draft = r.draft, campusNames = names, activeDay = SeriesWeekday.fromId(r.draft.weekdayIds.first()))
                    }
                    DraftLoadResult.NotFound -> ReviewUiState(load = ReviewLoad.NotFound)
                    DraftLoadResult.Failed -> ReviewUiState(load = ReviewLoad.Failed)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ReviewUiState(load = ReviewLoad.Failed)
            }
            if (mine == generation) _state.value = next
        }
    }

    fun selectDay(w: SeriesWeekday) = _state.update { s ->
        if (s.draft?.weekdayIds?.contains(w.id) == true) s.copy(activeDay = w) else s
    }

    fun openConfirm() = _state.update { if (it.load == ReviewLoad.Loaded && !it.posting && it.postError !is PostError.NotFound && it.postError !is PostError.Invalid) it.copy(confirming = true) else it }

    fun dismissConfirm() = _state.update { if (it.posting) it else it.copy(confirming = false) }

    /** Posts the reviewed draft. Repeat taps while posting do nothing. */
    fun post() {
        val s = _state.value
        val id = draftId
        val draft = s.draft
        if (s.posting || id == null || draft == null) return
        _state.update { it.copy(posting = true, postError = null) }
        viewModelScope.launch {
            val result = try {
                backend.postSeries(id, draft.input)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PostSeriesResult.Failed
            }
            _state.update {
                val done = it.copy(posting = false, confirming = false)
                when (result) {
                    is PostSeriesResult.Posted -> done.copy(exit = ReviewExit.Posted)
                    PostSeriesResult.DraftChanged -> done.copy(exit = ReviewExit.Edit(listOf(SeriesError("draftChanged", ""))))
                    is PostSeriesResult.Invalid -> done.copy(postError = PostError.Invalid(result.errors))
                    PostSeriesResult.NotFound -> done.copy(postError = PostError.NotFound)
                    PostSeriesResult.Failed -> done.copy(postError = PostError.Retry)
                }
            }
        }
    }

    /** Go back and edit. Carries the backend's errors to the form when the series was refused. */
    fun goBack() = _state.update {
        if (it.posting) it else it.copy(confirming = false, exit = ReviewExit.Edit((it.postError as? PostError.Invalid)?.errors.orEmpty()))
    }

    fun consumeExit() = _state.update { it.copy(exit = null) }

    /** Forgets the draft (leaving Review or logging out). */
    fun reset() {
        generation++
        draftId = null
        _state.value = ReviewUiState()
    }
}
