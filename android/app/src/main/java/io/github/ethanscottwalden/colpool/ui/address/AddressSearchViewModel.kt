package io.github.ethanscottwalden.colpool.ui.address

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ethanscottwalden.colpool.data.places.AddressSession
import io.github.ethanscottwalden.colpool.data.places.AddressSuggestion
import io.github.ethanscottwalden.colpool.data.places.PlacesAutocomplete
import io.github.ethanscottwalden.colpool.data.places.SelectedAddress
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI state of the address search sheet. Holds private text: never log it. */
data class AddressSearchState(
    val query: String = "",
    val suggestions: List<AddressSuggestion> = emptyList(),
    val searching: Boolean = false,
    /** Place ID of the tapped suggestion while it is being resolved to coordinates; taps are ignored meanwhile. */
    val resolvingPlaceId: String? = null,
    /** The tapped suggestion, resolved to a place ID and coordinates. Only this can be returned (RC-13). */
    val selected: SelectedAddress? = null,
    val error: AddressSearchError? = null,
) {
    val resolving: Boolean get() = resolvingPlaceId != null
    override fun toString(): String = "AddressSearchState(redacted)"
}

enum class AddressSearchError { SearchFailed, ResolveFailed }

/**
 * One instance per address entry. It owns one Places session: created here and used for every
 * suggestion call and the final resolve. After a successful resolve (which ends the session) a new
 * session is started, so an ended token is never reused. Create a new instance (or call [startNewEntry])
 * for the next entry.
 */
class AddressSearchViewModel(
    private val places: PlacesAutocomplete,
    private val debounceMs: Long = DEBOUNCE_MS,
    private val minChars: Int = MIN_CHARS,
) : ViewModel() {

    private var session: AddressSession = places.newSession()
    private var job: Job? = null
    private var searchSeq = 0
    private val _state = MutableStateFlow(AddressSearchState())
    val state: StateFlow<AddressSearchState> = _state

    fun onQueryChange(text: String) {
        // Retyping the same text after a search error retries the search.
        if (text == _state.value.query && _state.value.error != AddressSearchError.SearchFailed) return
        job?.cancel()
        // Any edit invalidates in-flight work, even if a port ignores cancellation, and drops the
        // previous selection and error: free text can't be returned (RC-13).
        val seq = ++searchSeq
        _state.update { it.copy(query = text, selected = null, resolvingPlaceId = null, error = null) }
        val trimmed = text.trim()
        if (trimmed.length < minChars) {
            _state.update { it.copy(suggestions = emptyList(), searching = false) }
            return
        }
        _state.update { it.copy(searching = true) }
        job = viewModelScope.launch {
            delay(debounceMs)
            try {
                val found = places.suggestions(trimmed, session)
                if (seq == searchSeq) _state.update { it.copy(suggestions = found, searching = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (seq == searchSeq) {
                    _state.update { it.copy(suggestions = emptyList(), searching = false, error = AddressSearchError.SearchFailed) }
                }
            }
        }
    }

    /** Tap on a suggestion: resolve it. On success [AddressSearchState.selected] is set and the sheet closes. */
    fun onSuggestionChosen(suggestion: AddressSuggestion) {
        if (_state.value.resolving) return
        job?.cancel()
        val seq = ++searchSeq
        _state.update { it.copy(searching = false, resolvingPlaceId = suggestion.placeId, selected = null, error = null) }
        job = viewModelScope.launch {
            try {
                val address = places.resolve(suggestion.placeId, session)
                // The lookup ends the Places session: a later edit and search starts a new one.
                session = places.newSession()
                // An edit while the lookup was in flight drops its late result.
                if (seq == searchSeq) _state.update { it.copy(query = address.text, resolvingPlaceId = null, selected = address) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep the suggestions so the person can pick again without retyping.
                if (seq == searchSeq) _state.update { it.copy(resolvingPlaceId = null, error = AddressSearchError.ResolveFailed) }
            }
        }
    }

    /** Resets the sheet for a new address entry, with a new Places session. */
    fun startNewEntry() {
        job?.cancel()
        session = places.newSession()
        _state.value = AddressSearchState()
    }

    companion object {
        const val DEBOUNCE_MS = 300L
        const val MIN_CHARS = 3
    }
}
