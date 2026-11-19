package io.github.ethanscottwalden.colpool.data.session

import io.github.ethanscottwalden.colpool.data.model.ProfileOptions
import io.github.ethanscottwalden.colpool.data.places.SelectedAddress

/** What Onboarding saves. Only fields the student set are present; lists are never empty. Holds private data: never log it. */
data class OnboardingSave(
    val address: SelectedAddress,
    val age: Int? = null,
    val gender: String? = null,
    val year: String? = null,
    val major: String? = null,
    val interests: List<String> = emptyList(),
    val traits: List<String> = emptyList(),
    val aboutMe: String? = null,
) {
    override fun toString(): String = "OnboardingSave(redacted)"
}

/** Firebase access for Onboarding. Faked in JVM tests. */
interface OnboardingBackend {
    /** Reads `options/profile`. Throws on failure. */
    suspend fun loadOptions(): ProfileOptions

    /**
     * Writes the profile fields and the private starting address in ONE batch, so the address (which
     * marks onboarding as done) is never saved without the profile fields. Throws on failure.
     */
    suspend fun save(data: OnboardingSave)
}
