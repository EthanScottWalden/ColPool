package io.github.ethanscottwalden.colpool.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ethanscottwalden.colpool.data.model.OptionItem
import io.github.ethanscottwalden.colpool.data.model.ProfileOptions
import io.github.ethanscottwalden.colpool.data.places.SelectedAddress
import io.github.ethanscottwalden.colpool.data.session.OnboardingBackend
import io.github.ethanscottwalden.colpool.data.session.OnboardingSave
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OptionsStatus { Loading, Loaded, Failed }

/** RC-30 limits used by this screen (`PROFILE_LIMITS` in functions; Rules enforce the same). */
object OnboardingLimits {
    const val AGE_MIN = 18
    const val ABOUT_ME_MAX = 300
}

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
    val ageInvalid: Boolean get() = OnboardingViewModel.parseAge(age) == AgeParse.Invalid
    val addressMissing: Boolean get() = address == null
    val canFinish: Boolean get() = address != null && !ageInvalid && !saving

    override fun toString(): String = "OnboardingUiState(redacted)"
}

sealed interface AgeParse {
    data object Blank : AgeParse
    data object Invalid : AgeParse
    data class Valid(val value: Int) : AgeParse
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

    fun setAge(v: String) = _state.update { it.copy(age = v.filter { c -> !c.isWhitespace() }.take(MAX_AGE_CHARS), saveFailed = false) }

    fun setGender(id: String?) = _state.update { it.copy(gender = pick(id, it.options.genders), saveFailed = false) }
    fun setYear(id: String?) = _state.update { it.copy(year = pick(id, it.options.years), saveFailed = false) }
    fun setMajor(id: String?) = _state.update { it.copy(major = pick(id, it.options.majors), saveFailed = false) }

    fun toggleInterest(id: String) = _state.update { it.copy(interests = toggle(it.interests, id, it.options.interests), saveFailed = false) }
    fun toggleTrait(id: String) = _state.update { it.copy(traits = toggle(it.traits, id, it.options.traits), saveFailed = false) }

    /** Text beyond the 300-character limit is refused (RC-30). */
    fun setAboutMe(v: String) = _state.update {
        if (v.length > OnboardingLimits.ABOUT_ME_MAX) it else it.copy(aboutMe = v, saveFailed = false)
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
            age = (parseAge(s.age) as? AgeParse.Valid)?.value,
            gender = s.gender,
            year = s.year,
            major = s.major,
            // Stored in list order, ids only. Empty lists and a blank about me are omitted.
            interests = s.options.interests.map { it.id }.filter { it in s.interests },
            traits = s.options.traits.map { it.id }.filter { it in s.traits },
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

    private fun pick(id: String?, list: List<OptionItem>): String? = id?.takeIf { x -> list.any { it.id == x } }

    private fun toggle(current: Set<String>, id: String, list: List<OptionItem>): Set<String> = when {
        id in current -> current - id
        list.any { it.id == id } -> current + id
        else -> current
    }

    companion object {
        /** More digits than any real age; also keeps the number inside Int. */
        const val MAX_AGE_CHARS = 9

        /** RC-30: a whole number of 18 or older, no upper bound. */
        fun parseAge(text: String): AgeParse {
            val t = text.trim()
            if (t.isEmpty()) return AgeParse.Blank
            if (!t.all { it in '0'..'9' }) return AgeParse.Invalid
            val n = t.toIntOrNull() ?: return AgeParse.Invalid
            return if (n >= OnboardingLimits.AGE_MIN) AgeParse.Valid(n) else AgeParse.Invalid
        }
    }
}
