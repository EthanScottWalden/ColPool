package io.github.ethanscottwalden.colpool.data.model

import io.github.ethanscottwalden.colpool.data.places.SelectedAddress

/** One entry of a predefined profile list (`options/profile`, RC-22). Profiles store [id], never [label]. */
data class OptionItem(val id: String, val label: String)

/** The five predefined lists from `options/profile`, each in `order`. */
data class ProfileOptions(
    val genders: List<OptionItem> = emptyList(),
    val years: List<OptionItem> = emptyList(),
    val majors: List<OptionItem> = emptyList(),
    val interests: List<OptionItem> = emptyList(),
    val traits: List<OptionItem> = emptyList(),
)

/**
 * The signed-in student's own profile: `profiles/{uid}` plus the private starting address and the email.
 * Only ever loaded for the owner (RC-01, RC-05). Holds private data: never log it.
 */
data class OwnProfile(
    val firstName: String,
    val lastName: String,
    val email: String,
    val address: SelectedAddress,
    val age: Int? = null,
    val gender: String? = null,
    val year: String? = null,
    val major: String? = null,
    val interests: List<String> = emptyList(),
    val traits: List<String> = emptyList(),
    val aboutMe: String? = null,
) {
    override fun toString(): String = "OwnProfile(redacted)"
}
