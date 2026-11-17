package io.github.ethanscottwalden.colpool.ui.address

import io.github.ethanscottwalden.colpool.data.places.AddressSession
import io.github.ethanscottwalden.colpool.data.places.AddressSuggestion
import io.github.ethanscottwalden.colpool.data.places.PlacesAutocomplete
import io.github.ethanscottwalden.colpool.data.places.SelectedAddress

/** Fake port with obviously fake data. Records which session each call used. */
class FakePlaces : PlacesAutocomplete {
    var failSuggestions = false
    var failResolve = false
    val sessionsCreated = mutableListOf<AddressSession>()
    val suggestionSessions = mutableListOf<AddressSession>()
    val resolveSessions = mutableListOf<AddressSession>()
    val queries = mutableListOf<String>()

    override fun newSession(): AddressSession = AddressSession("session-${sessionsCreated.size + 1}").also { sessionsCreated += it }

    override suspend fun suggestions(query: String, session: AddressSession): List<AddressSuggestion> {
        queries += query
        suggestionSessions += session
        if (failSuggestions) error("fake network failure")
        return listOf(
            AddressSuggestion("place-1", "100 Example Street", "Exampleville, CA"),
            AddressSuggestion("place-2", "200 Sample Avenue", "Exampleville, CA"),
        )
    }

    override suspend fun resolve(placeId: String, session: AddressSession): SelectedAddress {
        resolveSessions += session
        if (failResolve) error("fake failure")
        return SelectedAddress("100 Example Street, Exampleville, CA 90000", placeId, 12.5, -34.25)
    }
}
