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
    /** Set while a chosen suggestion is being resolved to coordinates. */
    val resolving: Boolean = false,
    /** The chosen suggestion, resolved to a place ID and coordinates. Only this can be submitted. */
    val selected: SelectedAddress? = null,
    val error: AddressSearchError? = null,
) {
    val canConfirm: Boolean get() = selected != null && !resolving
    override fun toString(): String = "AddressSearchState(redacted)"
}

enum class AddressSearchError { SearchFailed, ResolveFailed }

/**
 * One instance per address entry. It owns exactly one Places session: created here and used for
 * every suggestion call and the final resolve. Create a new instance (or call [startNewEntry])
 * for the next entry.
 */
class AddressSearchViewModel(
    private val places: PlacesAutocomplete,
    private val debounceMs: Long = DEBOUNCE_MS,
    private val minChars: Int = MIN_CHARS,
) : ViewModel() {

    private var session: AddressSession = places.newSession()
    private var job: Job? = null
    private val _state = MutableStateFlow(AddressSearchState())
    val state: StateFlow<AddressSearchState> = _state

    fun onQueryChange(text: String) {
        if (text == _state.value.query) return
        job?.cancel()
        // Any edit drops the previous selection: free text can't be submitted (RC-13).
        _state.update { it.copy(query = text, selected = null, resolving = false, error = null) }
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
                _state.update { it.copy(suggestions = found, searching = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(suggestions = emptyList(), searching = false, error = AddressSearchError.SearchFailed) }
            }
        }
    }

    fun onSuggestionChosen(suggestion: AddressSuggestion) {
        job?.cancel()
        _state.update { it.copy(searching = false, resolving = true, selected = null, error = null) }
        job = viewModelScope.launch {
            try {
                val address = places.resolve(suggestion.placeId, session)
                // Show the resolved text; editing it afterwards clears the selection.
                _state.update { it.copy(query = address.text, suggestions = emptyList(), resolving = false, selected = address) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(resolving = false, error = AddressSearchError.ResolveFailed) }
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
