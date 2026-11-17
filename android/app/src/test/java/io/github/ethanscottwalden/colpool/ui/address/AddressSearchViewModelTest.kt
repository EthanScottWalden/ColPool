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
    fun freeTextWithoutSelectionCannotBeSubmitted() = runTest {
        val vm = AddressSearchViewModel(fake)
        vm.onQueryChange("100 Example Street")
        advanceUntilIdle()
        assertEquals(2, vm.state.value.suggestions.size)
        assertFalse(vm.state.value.canConfirm)
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
        assertTrue(vm.state.value.canConfirm)
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
        assertFalse(vm.state.value.canConfirm)
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
        assertEquals(1, fake.sessionsCreated.size)
        val used = (fake.suggestionSessions + fake.resolveSessions).toSet()
        assertEquals(setOf(fake.sessionsCreated.single()), used)
        assertTrue(fake.suggestionSessions.size >= 2)

        vm.startNewEntry()
        vm.onQueryChange("200 Sam")
        advanceUntilIdle()
        assertEquals(2, fake.sessionsCreated.size)
        assertEquals(fake.sessionsCreated[1], fake.suggestionSessions.last())
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
        assertFalse(vm.state.value.canConfirm)
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
