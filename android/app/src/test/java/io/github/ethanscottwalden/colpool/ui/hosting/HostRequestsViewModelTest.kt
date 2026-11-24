package io.github.ethanscottwalden.colpool.ui.hosting

import io.github.ethanscottwalden.colpool.data.requests.RequestsUpdate
import io.github.ethanscottwalden.colpool.ui.hosting.HostRequestsTestData.request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class HostRequestsViewModelTest {
    private val backend = FakeHostRequestsBackend().also { it.profiles["maya"] = HostRequestsTestData.MAYA; it.profiles["jordan"] = null }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = HostRequestsViewModel(backend)

    @Test fun seriesRequestsAreFilteredOrderedAndNamed() = runTest {
        val vm = vm()
        vm.openSeries("s1")
        assertEquals(listOf("s1"), backend.observed)
        backend.bySeries.emit(RequestsUpdate.Loaded(listOf(request("b", riderId = "jordan", created = 2_000, days = arrayOf("fri" to "pending")), request("a", riderId = "maya", created = 1_000, days = arrayOf("tue" to "pending")), request("c", riderId = "x", days = arrayOf("mon" to "accepted")))))
        val s = vm.state.value
        assertTrue(s.seriesLoaded)
        assertEquals(listOf("s1_maya", "s1_jordan"), s.forSeries.map { it.id })
        assertEquals("Maya", s.names["maya"]?.firstName)
        assertTrue(s.names.containsKey("jordan") && s.names["jordan"] == null)
    }

    @Test fun aNewRequestAppearsLive() = runTest {
        val vm = vm()
        vm.openSeries("s1")
        backend.bySeries.emit(RequestsUpdate.Loaded(emptyList()))
        assertEquals(0, vm.state.value.forSeries.size)
        backend.bySeries.emit(RequestsUpdate.Loaded(HostRequestsTestData.MIXED))
        assertEquals(2, vm.state.value.forSeries.size)
    }

    @Test fun badgeListenerKeepsEveryRequest() = runTest {
        val vm = vm()
        vm.start()
        vm.start()
        assertEquals(listOf<String?>(null), backend.observed)
        backend.all.emit(RequestsUpdate.Loaded(HostRequestsTestData.MIXED))
        assertEquals(2, vm.state.value.all.size)
        backend.all.emit(RequestsUpdate.Failed)
        assertEquals(2, vm.state.value.all.size)
    }

    @Test fun failedSeriesListenerShowsNoRequests() = runTest {
        val vm = vm()
        vm.openSeries("s1")
        backend.bySeries.emit(RequestsUpdate.Failed)
        assertTrue(vm.state.value.seriesLoaded)
        assertTrue(vm.state.value.forSeries.isEmpty())
    }

    @Test fun profileOpensWithLabelsAndCloses() = runTest {
        val vm = vm()
        vm.openProfile("maya")
        val s = vm.state.value
        assertEquals("Maya", s.opened?.profile?.firstName)
        assertTrue(s.opened?.loaded == true)
        assertTrue(s.options.years.isNotEmpty())
        vm.closeProfile()
        assertNull(vm.state.value.opened)
    }

    @Test fun unreadableProfileIsLoadedButEmpty() = runTest {
        val vm = vm()
        vm.openProfile("jordan")
        assertNull(vm.state.value.opened?.profile)
        assertTrue(vm.state.value.opened?.loaded == true)
    }

    @Test fun missingOptionsDoNotBreakTheProfile() = runTest {
        backend.optionsError = IllegalStateException("x")
        val vm = vm()
        vm.openProfile("maya")
        assertNotNull(vm.state.value.opened?.profile)
    }

    @Test fun closingTheSeriesStopsAndClears() = runTest {
        val vm = vm()
        vm.openSeries("s1")
        backend.bySeries.emit(RequestsUpdate.Loaded(HostRequestsTestData.MIXED))
        vm.openProfile("maya")
        vm.closeSeries()
        assertTrue(vm.state.value.forSeries.isEmpty())
        assertFalse(vm.state.value.seriesLoaded)
        assertNull(vm.state.value.opened)
    }

    @Test fun resetForgetsEverything() = runTest {
        val vm = vm()
        vm.start()
        backend.all.emit(RequestsUpdate.Loaded(HostRequestsTestData.MIXED))
        vm.reset()
        assertEquals(HostRequestsUiState().all, vm.state.value.all)
        assertEquals("HostRequestsUiState(redacted)", vm.state.value.toString())
    }
}
