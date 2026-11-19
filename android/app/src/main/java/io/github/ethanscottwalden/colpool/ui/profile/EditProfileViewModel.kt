package io.github.ethanscottwalden.colpool.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ethanscottwalden.colpool.data.model.OwnProfile
import io.github.ethanscottwalden.colpool.data.model.ProfileOptions
import io.github.ethanscottwalden.colpool.data.places.SelectedAddress
import io.github.ethanscottwalden.colpool.data.session.ProfileBackend
import io.github.ethanscottwalden.colpool.data.session.ProfileChanges
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Edit Profile form. Holds private data (the starting address): never log it. */
data class EditProfileUiState(
    /** What is stored now. Null until [EditProfileViewModel.start] runs. */
    val original: OwnProfile? = null,
    val options: ProfileOptions = ProfileOptions(),
    val firstName: String = "",
    val lastName: String = "",
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
) {
    val ready: Boolean get() = original != null
    val firstNameInvalid: Boolean get() = !ProfileFieldRules.validName(firstName)
    val lastNameInvalid: Boolean get() = !ProfileFieldRules.validName(lastName)
    val ageInvalid: Boolean get() = ProfileFieldRules.parseAge(age) == AgeParse.Invalid
    val invalid: Boolean get() = firstNameInvalid || lastNameInvalid || ageInvalid || address == null

    /** What would be written; null while the form is invalid or not started. Empty when nothing changed. */
    val changes: ProfileChanges? get() = if (original == null || invalid) null else EditProfileViewModel.diff(this, original)
    val changed: Boolean get() = changes?.isEmpty == false
    val canSave: Boolean get() = ready && !saving && !invalid && changed

    override fun toString(): String = "EditProfileUiState(redacted)"
}

/**
 * Edit Profile. The form is open while [EditProfileUiState.ready]. [onSaved] gets the saved profile after a
 * successful write, then the form closes.
 */
class EditProfileViewModel(
    private val backend: ProfileBackend,
    private val onSaved: (OwnProfile) -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(EditProfileUiState())
    val state: StateFlow<EditProfileUiState> = _state.asStateFlow()

    /** Fills the form from the stored profile. Call each time Edit Profile opens. */
    fun start(profile: OwnProfile, options: ProfileOptions) {
        _state.value = EditProfileUiState(
            original = profile,
            options = options,
            firstName = profile.firstName,
            lastName = profile.lastName,
            address = profile.address,
            age = profile.age?.toString().orEmpty(),
            gender = profile.gender,
            year = profile.year,
            major = profile.major,
            interests = profile.interests.toSet(),
            traits = profile.traits.toSet(),
            aboutMe = profile.aboutMe.orEmpty(),
        )
    }

    /** Clears everything (leaving the screen or logging out). */
    fun reset() {
        _state.value = EditProfileUiState()
    }

    fun setFirstName(v: String) = _state.update { it.copy(firstName = v.take(ProfileLimits.NAME_MAX), saveFailed = false) }
    fun setLastName(v: String) = _state.update { it.copy(lastName = v.take(ProfileLimits.NAME_MAX), saveFailed = false) }
    fun setAddress(a: SelectedAddress) = _state.update { it.copy(address = a, saveFailed = false) }
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

    fun save() {
        val s = _state.value
        val original = s.original ?: return
        val changes = s.changes ?: return
        if (s.saving || changes.isEmpty) return
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                backend.save(changes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, saveFailed = true) }
                return@launch
            }
            val saved = merged(s, original)
            // Saved: hand the new values over and close the form (the form being closed is `!ready`).
            onSaved(saved)
            reset()
        }
    }

    companion object {
        /** Compares a valid form with what is stored. A removed optional field becomes a clear, never an empty value. */
        fun diff(s: EditProfileUiState, o: OwnProfile): ProfileChanges {
            val set = linkedMapOf<String, Any>()
            val clear = linkedSetOf<String>()

            fun <T : Any> optional(key: String, now: T?, was: T?) {
                when {
                    now == null -> if (was != null) clear += key
                    now != was -> set[key] = now
                }
            }

            val first = s.firstName.trim()
            val last = s.lastName.trim()
            if (first != o.firstName) set["firstName"] = first
            if (last != o.lastName) set["lastName"] = last
            optional("age", (ProfileFieldRules.parseAge(s.age) as? AgeParse.Valid)?.value, o.age)
            optional("gender", s.gender, o.gender)
            optional("year", s.year, o.year)
            optional("major", s.major, o.major)
            optionalList("interests", ProfileFieldRules.inListOrder(s.options.interests, s.interests), o.interests, set, clear)
            optionalList("traits", ProfileFieldRules.inListOrder(s.options.traits, s.traits), o.traits, set, clear)
            optional("aboutMe", s.aboutMe.trim().ifEmpty { null }, o.aboutMe)
            return ProfileChanges(set, clear, newAddress = s.address?.takeIf { it != o.address })
        }

        private fun optionalList(key: String, now: List<String>, was: List<String>, set: MutableMap<String, Any>, clear: MutableSet<String>) {
            when {
                now.isEmpty() -> if (was.isNotEmpty()) clear += key
                now.toSet() != was.toSet() -> set[key] = now
            }
        }

        private fun merged(s: EditProfileUiState, o: OwnProfile): OwnProfile = o.copy(
            firstName = s.firstName.trim(),
            lastName = s.lastName.trim(),
            address = s.address ?: o.address,
            age = (ProfileFieldRules.parseAge(s.age) as? AgeParse.Valid)?.value,
            gender = s.gender,
            year = s.year,
            major = s.major,
            interests = ProfileFieldRules.inListOrder(s.options.interests, s.interests),
            traits = ProfileFieldRules.inListOrder(s.options.traits, s.traits),
            aboutMe = s.aboutMe.trim().ifEmpty { null },
        )
    }
}
