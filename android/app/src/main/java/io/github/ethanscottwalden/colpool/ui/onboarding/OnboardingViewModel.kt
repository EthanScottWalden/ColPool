package io.github.ethanscottwalden.colpool.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ethanscottwalden.colpool.data.model.ProfileOptions
import io.github.ethanscottwalden.colpool.data.places.SelectedAddress
import io.github.ethanscottwalden.colpool.data.session.OnboardingBackend
import io.github.ethanscottwalden.colpool.data.session.OnboardingSave
import io.github.ethanscottwalden.colpool.ui.profile.AgeParse
import io.github.ethanscottwalden.colpool.ui.profile.OptionsStatus
import io.github.ethanscottwalden.colpool.ui.profile.ProfileFieldRules
import io.github.ethanscottwalden.colpool.ui.profile.ProfileLimits
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Holds private data (the starting address): never log it. */
data class OnboardingUiState(
    val optionsStatus: OptionsStatus = OptionsStatus.Loading,
    val options: ProfileOptions = ProfileOptions(),
    val address: SelectedAddress? = null,
    val age: String = "",
    val gender: String? = null,
    val year: String? = null,
    val major: String? = null,
    val interests: Set<String> = emptySet(),
    val traits: Set<String> = emptySet(),
    val aboutMe: String = "",
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    /** Field errors show once the person tried to finish, except age which shows as soon as it is typed. */
    val showErrors: Boolean = false,
) {
    /** A typed age that isn't a whole number of 18 or older (RC-30). Blank is fine (optional). */
    val ageInvalid: Boolean get() = ProfileFieldRules.parseAge(age) == AgeParse.Invalid
    val addressMissing: Boolean get() = address == null
    val canFinish: Boolean get() = address != null && !ageInvalid && !saving

    override fun toString(): String = "OnboardingUiState(redacted)"
}

/**
 * Onboarding form. [onCompleted] runs after a successful save and re-runs the routing decision
 * (SessionViewModel.resolve), which reads privateDetails and goes to Search.
 */
class OnboardingViewModel(
    private val backend: OnboardingBackend,
    private val onCompleted: suspend () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()
    private var generation = 0

    /** Loads the option lists (safe to call again to retry). */
    fun loadOptions() {
        val mine = ++generation
        _state.update { it.copy(optionsStatus = OptionsStatus.Loading) }
        viewModelScope.launch {
            try {
                val options = backend.loadOptions()
                if (mine == generation) _state.update { it.copy(optionsStatus = OptionsStatus.Loaded, options = options) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (mine == generation) _state.update { it.copy(optionsStatus = OptionsStatus.Failed) }
            }
        }
    }

    /** Clears everything (after log out or leaving Onboarding, so the next person doesn't see it). */
    fun reset() {
        generation++
        _state.value = OnboardingUiState()
    }

    fun setAddress(address: SelectedAddress) = _state.update { it.copy(address = address, saveFailed = false) }

    fun setAge(v: String) = _state.update { it.copy(age = ProfileFieldRules.sanitizeAge(v), saveFailed = false) }

    fun setGender(id: String?) = _state.update { it.copy(gender = ProfileFieldRules.pick(id, it.options.genders), saveFailed = false) }
    fun setYear(id: String?) = _state.update { it.copy(year = ProfileFieldRules.pick(id, it.options.years), saveFailed = false) }
    fun setMajor(id: String?) = _state.update { it.copy(major = ProfileFieldRules.pick(id, it.options.majors), saveFailed = false) }

    fun toggleInterest(id: String) = _state.update { it.copy(interests = ProfileFieldRules.toggle(it.interests, id, it.options.interests), saveFailed = false) }
    fun toggleTrait(id: String) = _state.update { it.copy(traits = ProfileFieldRules.toggle(it.traits, id, it.options.traits), saveFailed = false) }

    /** Text beyond the 300-character limit is refused (RC-30). */
    fun setAboutMe(v: String) = _state.update {
        if (v.length > ProfileLimits.ABOUT_ME_MAX) it else it.copy(aboutMe = v, saveFailed = false)
    }

    fun finish() {
        val s = _state.value
        if (s.saving) return
        val address = s.address
        if (address == null || s.ageInvalid) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        val data = OnboardingSave(
            address = address,
            age = (ProfileFieldRules.parseAge(s.age) as? AgeParse.Valid)?.value,
            gender = s.gender,
            year = s.year,
            major = s.major,
            // Stored in list order, ids only. Empty lists and a blank about me are omitted.
            interests = ProfileFieldRules.inListOrder(s.options.interests, s.interests),
            traits = ProfileFieldRules.inListOrder(s.options.traits, s.traits),
            aboutMe = s.aboutMe.trim().ifEmpty { null },
        )
        _state.update { it.copy(saving = true, saveFailed = false, showErrors = true) }
        viewModelScope.launch {
            try {
                backend.save(data)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, saveFailed = true) }
                return@launch
            }
            // Saved. Routing now reads privateDetails and moves to Search; a failure there shows the routing retry screen.
            _state.update { it.copy(saving = false) }
            onCompleted()
        }
    }
}
