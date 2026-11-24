package io.github.ethanscottwalden.colpool.ui.hosting

import io.github.ethanscottwalden.colpool.data.series.HostedSeriesUpdate
import io.github.ethanscottwalden.colpool.ui.series.FakeSeriesBackend
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HostingViewModelTest {
    private val backend = FakeSeriesBackend()
    private val clock = Clock.fixed(Instant.parse("2026-11-24T20:00:00Z"), ZoneOffset.UTC)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = HostingViewModel(backend, clock).also { it.start() }
    private suspend fun emit(vararg ids: String) = backend.hosted.emit(HostedSeriesUpdate.Loaded(ids.map { HostingTestData.series(it) }))

    @Test fun startsLoading() {
        assertEquals(HostingLoad.Loading, vm().state.value.load)
    }

    @Test fun loadsCampusNamesAndShowsTheSeries() = kotlinx.coroutines.test.runTest {
        val vm = vm()
        emit("a")
        assertEquals(HostingLoad.Loaded, vm.state.value.load)
        assertEquals(listOf("a"), vm.state.value.series.map { it.id })
        assertEquals("Yeager Center", vm.state.value.campusNames["yeager-center"])
    }

    @Test fun liveUpdatesReplaceTheList() = kotlinx.coroutines.test.runTest {
        val vm = vm()
        emit("a")
        emit("a", "b")
        assertEquals(2, vm.state.value.series.size)
        backend.hosted.emit(HostedSeriesUpdate.Loaded(emptyList()))
        assertEquals(HostingLoad.Loaded, vm.state.value.load)
        assertEquals(0, vm.state.value.series.size)
    }

    @Test fun pastEndDateIsHidden() = kotlinx.coroutines.test.runTest {
        val vm = vm()
        backend.hosted.emit(HostedSeriesUpdate.Loaded(listOf(HostingTestData.series("old", HostingTestData.doc(end = "2026-11-23")))))
        assertEquals(0, vm.state.value.series.size)
    }

    @Test fun failureShowsFailed() = kotlinx.coroutines.test.runTest {
        val vm = vm()
        backend.hosted.emit(HostedSeriesUpdate.Failed)
        assertEquals(HostingLoad.Failed, vm.state.value.load)
    }

    @Test fun openingSelectsTheFirstDayAndDaysSwitch() = kotlinx.coroutines.test.runTest {
        val vm = vm()
        emit("a")
        vm.open("a")
        assertEquals(SeriesWeekday.Tue, vm.state.value.activeDay)
        vm.selectDay(SeriesWeekday.Thu)
        assertEquals(SeriesWeekday.Thu, vm.state.value.activeDay)
        vm.selectDay(SeriesWeekday.Fri)
        assertEquals(SeriesWeekday.Thu, vm.state.value.activeDay)
        vm.close()
        assertNull(vm.state.value.selectedId)
    }

    @Test fun aSeriesThatDisappearsClosesItsDetails() = kotlinx.coroutines.test.runTest {
        val vm = vm()
        emit("a", "b")
        vm.open("a")
        emit("b")
        assertNull(vm.state.value.selectedId)
    }

    @Test fun resetForgetsEverything() = kotlinx.coroutines.test.runTest {
        val vm = vm()
        emit("a")
        vm.reset()
        assertEquals(HostingLoad.Loading, vm.state.value.load)
        assertEquals(0, vm.state.value.series.size)
    }
}
