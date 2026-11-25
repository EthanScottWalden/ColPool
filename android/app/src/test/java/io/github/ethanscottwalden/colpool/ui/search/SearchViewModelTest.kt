package io.github.ethanscottwalden.colpool.ui.search

import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday.Mon
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday.Thu
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday.Tue
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday.Wed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val backend = FakeSearchBackend().apply {
        series = listOf(SearchTestData.far(), SearchTestData.mid(), SearchTestData.near(), SearchTestData.last())
        names = mapOf("hostA" to "Ava", "hostB" to "Riley", "hostC" to "Jamal", "hostD" to "Maya")
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = SearchViewModel(backend, SearchTestData.CLOCK)

    @Test fun startsWithNothingPickedAndNoReads() {
        val vm = vm()
        assertEquals(SearchLoad.Idle, vm.state.value.load)
        assertTrue(backend.searches.isEmpty())
    }

    @Test fun pickingDaysRunsOneSeriesQueryOneRequestsListAndNamesForShownHosts() {
        val vm = vm()
        vm.toggle(Mon)
        assertEquals(listOf(listOf("mon")), backend.searches)
        assertEquals(1, backend.requestLists)
        assertEquals(SearchLoad.Loaded, vm.state.value.load)
        // Monday: near (open), last (open); mid is full on Monday so the query drops it; far has no Monday.
        assertEquals(listOf("near", "last"), vm.state.value.results.map { it.series.id })
        assertEquals(listOf("hostA", "hostD"), backend.nameReads.sorted())
        assertEquals("Ava", vm.state.value.hostNames["hostA"])
        assertTrue(vm.state.value.hasStart)
    }

    @Test fun changingDaysUpdatesResultsAndReusesNames() {
        val vm = vm()
        vm.toggle(Mon)
        vm.toggle(Thu)
        assertEquals(listOf("mon", "thu"), backend.searches.last())
        assertEquals(listOf("near", "last", "mid", "far"), vm.state.value.results.map { it.series.id })
        // hostA and hostD were already read.
        assertEquals(listOf("hostA", "hostB", "hostC", "hostD"), backend.nameReads.sorted())
        vm.toggle(Mon)
        assertEquals(listOf("mid", "far"), vm.state.value.results.map { it.series.id })
    }

    @Test fun unpickingEverythingGoesBackToThePrompt() {
        val vm = vm()
        vm.toggle(Mon)
        vm.toggle(Mon)
        assertEquals(SearchLoad.Idle, vm.state.value.load)
        assertTrue(vm.state.value.results.isEmpty())
    }

    @Test fun excludesOwnAndRequested() {
        backend.requested = setOf("near")
        val vm = vm()
        vm.toggle(Mon)
        assertEquals(listOf("last"), vm.state.value.results.map { it.series.id })
        backend.uid = "hostD"
        vm.retry()
        assertTrue(vm.state.value.results.isEmpty())
        assertEquals(SearchLoad.Loaded, vm.state.value.load)
    }

    @Test fun noStartingAddressMeansNoDistances() {
        backend.start = null
        val vm = vm()
        vm.toggle(Mon)
        assertEquals(false, vm.state.value.hasStart)
        assertTrue(vm.state.value.results.all { it.distanceTenths == null })
    }

    @Test fun failureThenRetry() {
        backend.searchError = RuntimeException("offline")
        val vm = vm()
        vm.toggle(Wed)
        assertEquals(SearchLoad.Failed, vm.state.value.load)
        backend.searchError = null
        vm.retry()
        assertEquals(SearchLoad.Loaded, vm.state.value.load)
    }

    @Test fun unreadableHostNameStillShowsTheSeries() {
        backend.names = emptyMap()
        val vm = vm()
        vm.toggle(Mon)
        assertEquals(2, vm.state.value.results.size)
        assertNull(vm.state.value.hostNames["hostA"])
    }

    @Test fun openAndCloseKeepTheResults() {
        val vm = vm()
        vm.toggle(Mon)
        vm.open("near")
        assertEquals("near", vm.state.value.openedSeriesId)
        vm.close()
        assertNull(vm.state.value.openedSeriesId)
        assertEquals(2, vm.state.value.results.size)
        vm.open("not-shown")
        assertNull(vm.state.value.openedSeriesId)
    }

    @Test fun refreshReadsAgainOnlyWhenDaysArePicked() {
        val vm = vm()
        vm.refresh()
        assertTrue(backend.searches.isEmpty())
        vm.toggle(Mon)
        vm.refresh()
        assertEquals(2, backend.searches.size)
        assertEquals(SearchLoad.Loaded, vm.state.value.load)
    }

    @Test fun resetForgetsEverything() {
        val vm = vm()
        vm.toggle(Tue)
        vm.reset()
        assertTrue(vm.state.value.picked.isEmpty())
        assertEquals(SearchLoad.Idle, vm.state.value.load)
    }
}
