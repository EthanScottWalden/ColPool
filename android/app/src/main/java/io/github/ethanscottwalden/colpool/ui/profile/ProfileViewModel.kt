package io.github.ethanscottwalden.colpool.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ethanscottwalden.colpool.data.model.OwnProfile
import io.github.ethanscottwalden.colpool.data.model.ProfileOptions
import io.github.ethanscottwalden.colpool.data.session.ProfileBackend
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The Profile tab. Holds private data (address, email): never log it. */
data class ProfileUiState(
    val status: OptionsStatus = OptionsStatus.Loading,
    val profile: OwnProfile? = null,
    val options: ProfileOptions = ProfileOptions(),
) {
    override fun toString(): String = "ProfileUiState(redacted)"
}

/** Loads the student's own profile and the option lists (to show labels, never ids). */
class ProfileViewModel(private val backend: ProfileBackend) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()
    private var generation = 0

    /** Safe to call again to retry. */
    fun load() {
        val mine = ++generation
        _state.update { it.copy(status = OptionsStatus.Loading) }
        viewModelScope.launch {
            try {
                val profile = backend.loadProfile()
                val options = backend.loadOptions()
                if (mine == generation) _state.value = ProfileUiState(OptionsStatus.Loaded, profile, options)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (mine == generation) _state.update { it.copy(status = OptionsStatus.Failed) }
            }
        }
    }

    /** After a successful edit: show the saved values without another read. */
    fun applySaved(profile: OwnProfile) {
        generation++
        _state.update { it.copy(status = OptionsStatus.Loaded, profile = profile) }
    }

    /** Clears everything (log out), so the next person never sees it. */
    fun reset() {
        generation++
        _state.value = ProfileUiState()
    }
}
