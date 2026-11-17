package io.github.ethanscottwalden.colpool.data.places

import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Wraps one SDK session token. */
private class SdkSession(val token: AutocompleteSessionToken) : AddressSession(token.toString())

/**
 * Places SDK (new Places API) implementation.
 * - Suggestions are limited to the US. No device-location bias, no GPS (SDD §2.3).
 * - The place lookup requests only the minimal fields: ID, formatted address, location.
 * - Nothing typed, suggested or resolved is logged (RC-05).
 * [client] is lazy so a Places start-up failure surfaces as an inline error, not a crash.
 */
class PlacesSdkAutocomplete(private val client: () -> PlacesClient) : PlacesAutocomplete {

    override fun newSession(): AddressSession = SdkSession(AutocompleteSessionToken.newInstance())

    override suspend fun suggestions(query: String, session: AddressSession): List<AddressSuggestion> {
        val cancel = CancellationTokenSource()
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .setCancellationToken(cancel.token)
            .setSessionToken(session.sdkToken())
            .setCountries(COUNTRIES)
            .build()
        return client().findAutocompletePredictions(request).await(cancel).autocompletePredictions.map {
            AddressSuggestion(
                placeId = it.placeId,
                primaryText = it.getPrimaryText(null).toString(),
                secondaryText = it.getSecondaryText(null).toString(),
            )
        }
    }

    override suspend fun resolve(placeId: String, session: AddressSession): SelectedAddress {
        val cancel = CancellationTokenSource()
        val request = FetchPlaceRequest.builder(placeId, FIELDS)
            .setCancellationToken(cancel.token)
            .setSessionToken(session.sdkToken())
            .build()
        val place = client().fetchPlace(request).await(cancel).place
        val location = place.location ?: error("Place has no location")
        val text = place.formattedAddress ?: error("Place has no address")
        return SelectedAddress(text = text, placeId = place.id ?: placeId, lat = location.latitude, lng = location.longitude)
    }

    private fun AddressSession.sdkToken(): AutocompleteSessionToken =
        (this as? SdkSession)?.token ?: error("Session was not created by this client")

    private companion object {
        val COUNTRIES = listOf("US")
        // Minimal field mask: keeps the lookup in the cheapest tier.
        val FIELDS = listOf(Place.Field.ID, Place.Field.FORMATTED_ADDRESS, Place.Field.LOCATION)
    }
}

private suspend fun <T> Task<T>.await(source: CancellationTokenSource): T = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { source.cancel() }
    addOnSuccessListener { if (cont.isActive) cont.resume(it) }
    addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
