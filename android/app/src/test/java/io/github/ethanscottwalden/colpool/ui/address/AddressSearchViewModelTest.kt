package io.github.ethanscottwalden.colpool.ui.address

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddressSearchViewModelTest {
    private val fake = FakePlaces()

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun freeTextWithoutSelectionReturnsNothing() = runTest {
        val vm = AddressSearchViewModel(fake)
        vm.onQueryChange("100 Example Street")
        advanceUntilIdle()
        assertEquals(2, vm.state.value.suggestions.size)
        assertNull(vm.state.value.selected)
    }

    @Test
    fun selectingSuggestionYieldsPlaceIdAndCoordinates() = runTest {
        val vm = AddressSearchViewModel(fake)
        vm.onQueryChange("100 Example")
        advanceUntilIdle()
        vm.onSuggestionChosen(vm.state.value.suggestions.first())
        advanceUntilIdle()
        val selected = vm.state.value.selected
        assertNotNull(selected)
        assertEquals("place-1", selected!!.placeId)
        assertEquals(12.5, selected.lat, 0.0)
        assertEquals(-34.25, selected.lng, 0.0)
        assertFalse(vm.state.value.resolving)
    }

    @Test
    fun editingAfterSelectionClearsIt() = runTest {
        val vm = AddressSearchViewModel(fake)
        vm.onQueryChange("100 Example")
        advanceUntilIdle()
        vm.onSuggestionChosen(vm.state.value.suggestions.first())
        advanceUntilIdle()
        vm.onQueryChange(vm.state.value.query + "x")
        assertNull(vm.state.value.selected)
    }

    @Test
    fun oneSessionPerEntryAndNewSessionForNewEntry() = runTest {
        val vm = AddressSearchViewModel(fake)
        vm.onQueryChange("100 Exa")
        advanceUntilIdle()
        vm.onQueryChange("100 Exam")
        advanceUntilIdle()
        vm.onSuggestionChosen(vm.state.value.suggestions.first())
        advanceUntilIdle()
        val first = fake.sessionsCreated.first()
        assertEquals(setOf(first), (fake.suggestionSessions + fake.resolveSessions).toSet())
        assertTrue(fake.suggestionSessions.size >= 2)

        vm.startNewEntry()
        vm.onQueryChange("200 Sam")
        advanceUntilIdle()
        assertNotEquals(first, fake.suggestionSessions.last())
        assertNotEquals(fake.resolveSessions.last(), fake.suggestionSessions.last())
    }

    @Test
    fun shortInputDoesNotQueryAndTypingIsDebounced() = runTest {
        val vm = AddressSearchViewModel(fake)
        vm.onQueryChange("10")
        advanceUntilIdle()
        assertTrue(fake.queries.isEmpty())
        vm.onQueryChange("100")
        vm.onQueryChange("100 E")
        vm.onQueryChange("100 Ex")
        advanceUntilIdle()
        assertEquals(listOf("100 Ex"), fake.queries)
    }

    @Test
    fun searchFailureShowsErrorState() = runTest {
        fake.failSuggestions = true
        val vm = AddressSearchViewModel(fake)
        vm.onQueryChange("100 Example")
        advanceUntilIdle()
        assertEquals(AddressSearchError.SearchFailed, vm.state.value.error)
    }

    @Test
    fun searchAfterResolveUsesNewSession() = runTest {
        val vm = AddressSearchViewModel(fake)
        vm.onQueryChange("100 Example")
        advanceUntilIdle()
        vm.onSuggestionChosen(vm.state.value.suggestions.first())
        advanceUntilIdle()
        val resolveSession = fake.resolveSessions.single()
        vm.onQueryChange("100 Example St")
        advanceUntilIdle()
        assertNotEquals(resolveSession, fake.suggestionSessions.last())
    }

    @Test
    fun retypingSameTextAfterSearchErrorRetries() = runTest {
        fake.failSuggestions = true
        val vm = AddressSearchViewModel(fake)
        vm.onQueryChange("100 Example")
        advanceUntilIdle()
        fake.failSuggestions = false
        vm.onQueryChange("100 Example")
        advanceUntilIdle()
        assertNull(vm.state.value.error)
        assertEquals(2, vm.state.value.suggestions.size)
        assertEquals(2, fake.queries.size)
    }

    @Test
    fun doubleTapWhileResolvingIsIgnored() = runTest {
        val vm = AddressSearchViewModel(fake)
        vm.onQueryChange("100 Example")
        advanceUntilIdle()
        val first = vm.state.value.suggestions
        vm.onSuggestionChosen(first[0])
        vm.onSuggestionChosen(first[1])
        advanceUntilIdle()
        assertEquals(1, fake.resolveSessions.size)
        assertEquals("place-1", vm.state.value.selected!!.placeId)
    }

    @Test
    fun resolveFailureKeepsSuggestionsAndEditClearsError() = runTest {
        fake.failResolve = true
        val vm = AddressSearchViewModel(fake)
        vm.onQueryChange("100 Example")
        advanceUntilIdle()
        vm.onSuggestionChosen(vm.state.value.suggestions.first())
        advanceUntilIdle()
        assertEquals(AddressSearchError.ResolveFailed, vm.state.value.error)
        assertEquals(2, vm.state.value.suggestions.size)
        vm.onQueryChange("100 Example S")
        assertNull(vm.state.value.error)
    }

    @Test
    fun staleSearchResponseIsIgnored() = runTest {
        val gated = GatedPlaces()
        val vm = AddressSearchViewModel(gated, debounceMs = 0)
        vm.onQueryChange("aaa")
        advanceUntilIdle()
        vm.onQueryChange("bbb")
        advanceUntilIdle()
        gated.complete("bbb")
        gated.complete("aaa")
        advanceUntilIdle()
        assertEquals(listOf("bbb"), vm.state.value.suggestions.map { it.primaryText })
        assertFalse(vm.state.value.searching)
    }

    @Test
    fun resolveFailureShowsErrorAndNothingSelected() = runTest {
        fake.failResolve = true
        val vm = AddressSearchViewModel(fake)
        vm.onQueryChange("100 Example")
        advanceUntilIdle()
        vm.onSuggestionChosen(vm.state.value.suggestions.first())
        advanceUntilIdle()
        assertEquals(AddressSearchError.ResolveFailed, vm.state.value.error)
        assertNull(vm.state.value.selected)
    }
}
