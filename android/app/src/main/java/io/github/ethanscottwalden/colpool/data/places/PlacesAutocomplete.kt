package io.github.ethanscottwalden.colpool.data.places

/** An address chosen from the suggestions and resolved to coordinates (RC-13). Private data: never log it. */
data class SelectedAddress(
    val text: String,
    val placeId: String,
    val lat: Double,
    val lng: Double,
) {
    // Keep the address and coordinates out of accidental logs and crash reports.
    override fun toString(): String = "SelectedAddress(redacted)"
}

/** One suggestion in the list. Not logged. */
data class AddressSuggestion(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String,
)

/**
 * One address-entry session. Every suggestion call and the final [PlacesAutocomplete.resolve] of one
 * entry use the same session, so Places bills the entry as a single session (RC-28, RISK-08).
 */
open class AddressSession(val id: String)

/**
 * Port over the Places SDK. The real implementation is [PlacesSdkAutocomplete]; tests use a fake.
 * Implementations throw on failure (network, bad key); callers show an inline error.
 */
interface PlacesAutocomplete {
    fun newSession(): AddressSession
    suspend fun suggestions(query: String, session: AddressSession): List<AddressSuggestion>
    suspend fun resolve(placeId: String, session: AddressSession): SelectedAddress
}
