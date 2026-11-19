package io.github.ethanscottwalden.colpool.data.model

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
