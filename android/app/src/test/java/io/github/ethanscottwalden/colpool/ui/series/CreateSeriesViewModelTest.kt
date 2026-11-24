package io.github.ethanscottwalden.colpool.ui.series

import io.github.ethanscottwalden.colpool.data.series.RoutePreviewResult
import io.github.ethanscottwalden.colpool.data.series.SeriesError
import io.github.ethanscottwalden.colpool.ui.profile.OptionsStatus
import io.github.ethanscottwalden.colpool.ui.series.SeriesTestData.TODAY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateSeriesViewModelTest {
    private val backend = FakeSeriesBackend()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = CreateSeriesViewModel(backend) { TODAY }.also { it.loadCampusPoints() }

    /** A view model holding the complete sample form. */
    private fun filledVm(): CreateSeriesViewModel {
        val vm = vm()
        vm.setPickup(SeriesTestData.NORCO)
        vm.setStartDate("2026-11-30"); vm.setEndDate("2026-12-11")
        vm.setPrice("5"); vm.setRiders(2); vm.setVehicle("Blue 2017 Honda Fit")
        vm.toggleWeekday(SeriesWeekday.Mon)
        for (d in Direction.entries) {
            vm.setDepartTime(SeriesWeekday.Mon, d, if (d == Direction.ToCampus) 450 else 930)
            vm.setCampusPoint(SeriesWeekday.Mon, d, "yeager-center")
        }
        return vm
    }

    @Test fun campusPointsLoadAndFailureCanRetry() {
        backend.campusError = RuntimeException("offline")
        val vm = vm()
        assertEquals(OptionsStatus.Failed, vm.state.value.campusStatus)
        backend.campusError = null
        vm.loadCampusPoints()
        assertEquals(OptionsStatus.Loaded, vm.state.value.campusStatus)
        assertEquals(5, vm.state.value.campusPoints.size)
    }

    @Test fun weekdayToggleKeepsCardsAndActiveDay() {
        val vm = vm()
        vm.toggleWeekday(SeriesWeekday.Wed)
        vm.toggleWeekday(SeriesWeekday.Mon)
        assertEquals(SeriesWeekday.Mon, vm.state.value.activeDay)
        assertEquals(listOf(SeriesWeekday.Mon, SeriesWeekday.Wed), vm.state.value.orderedWeekdays)
        vm.selectDay(SeriesWeekday.Wed)
        vm.toggleWeekday(SeriesWeekday.Wed)
        assertEquals(SeriesWeekday.Mon, vm.state.value.activeDay)
        assertFalse(SeriesWeekday.Wed in vm.state.value.days)
        vm.toggleWeekday(SeriesWeekday.Mon)
        assertNull(vm.state.value.activeDay)
    }

    @Test fun limitsAreEnforcedWhileTyping() {
        val vm = vm()
        vm.setRiders(9); assertEquals(6, vm.state.value.riders)
        vm.setRiders(0); assertEquals(1, vm.state.value.riders)
        vm.setPrice("1a2b3c4"); assertEquals("123", vm.state.value.price)
        vm.setVehicle("x".repeat(60)); vm.setVehicle("x".repeat(61))
        assertEquals(60, vm.state.value.vehicle.length)
    }

    @Test fun stopCapIsTenPerDirectionAndIndependent() {
        val vm = filledVm()
        repeat(12) { vm.addStop(SeriesWeekday.Mon, Direction.ToCampus, SeriesTestData.stop()) }
        assertEquals(10, vm.state.value.day(SeriesWeekday.Mon).toCampus.stops.size)
        vm.addStop(SeriesWeekday.Mon, Direction.FromCampus, SeriesTestData.stop())
        assertEquals(1, vm.state.value.day(SeriesWeekday.Mon).fromCampus.stops.size)
        vm.removeStop(SeriesWeekday.Mon, Direction.ToCampus, 0)
        assertEquals(9, vm.state.value.day(SeriesWeekday.Mon).toCampus.stops.size)
        vm.addStop(SeriesWeekday.Mon, Direction.ToCampus, SeriesTestData.stop(minutes = 500))
        assertEquals(120, vm.state.value.day(SeriesWeekday.Mon).toCampus.stops.last().minutes)
    }

    @Test fun unknownCampusPointIsRefused() {
        val vm = filledVm()
        vm.setCampusPoint(SeriesWeekday.Mon, Direction.ToCampus, "nowhere")
        assertEquals("yeager-center", vm.state.value.day(SeriesWeekday.Mon).toCampus.campusPointId)
    }

    @Test fun clientChecksBlockTheCall() {
        val vm = vm()
        vm.submit()
        assertTrue(backend.calls.isEmpty())
        assertTrue(vm.state.value.errors.any { it.code == "required" && it.path == "pickupSpot" })
        assertFalse(vm.state.value.submitting)
    }

    @Test fun fixingAFieldClearsItsError() {
        val vm = vm()
        vm.submit()
        vm.setPickup(SeriesTestData.NORCO)
        assertTrue(vm.state.value.errors.none { it.path == "pickupSpot" })
        assertTrue(vm.state.value.errors.any { it.path == "vehicle" })
    }

    @Test fun successSendsThePayloadOnceAndNavigatesWithTheDraftId() {
        val vm = filledVm()
        vm.submit()
        assertEquals(1, backend.calls.size)
        assertEquals(listOf("mon"), backend.calls.single()["weekdays"])
        assertEquals("draft1234567890123456", vm.state.value.reviewDraftId)
        assertFalse(vm.state.value.submitting)
        vm.consumeReview()
        assertNull(vm.state.value.reviewDraftId)
    }

    @Test fun backendErrorsAreShownAndJumpToTheDay() {
        val vm = filledVm()
        vm.toggleWeekday(SeriesWeekday.Wed)
        for (d in Direction.entries) { vm.setDepartTime(SeriesWeekday.Wed, d, 450); vm.setCampusPoint(SeriesWeekday.Wed, d, "yeager-center") }
        vm.selectDay(SeriesWeekday.Mon)
        val errors = listOf(SeriesError("hostingWeekdayOverlap", "days.wed", mapOf("weekday" to "wed")), SeriesError("hostingLimitReached", "", mapOf("limit" to 5)))
        backend.result = RoutePreviewResult.Invalid(errors)
        vm.submit()
        assertEquals(errors, vm.state.value.errors)
        assertEquals(SeriesWeekday.Wed, vm.state.value.activeDay)
        assertNull(vm.state.value.reviewDraftId)
        assertFalse(vm.state.value.submitting)
    }

    @Test fun busyAndFailedBecomeWholeFormErrors() {
        val vm = filledVm()
        backend.result = RoutePreviewResult.Unavailable
        vm.submit()
        assertEquals(listOf(SeriesError("unavailable", "")), vm.state.value.errors)
        backend.result = RoutePreviewResult.Failed
        vm.submit()
        assertEquals(listOf(SeriesError("internal", "")), vm.state.value.errors)
    }

    @Test fun nextContinueOverwritesTheSameDraft() {
        val vm = filledVm()
        vm.submit()
        vm.consumeReview()
        vm.submit()
        assertEquals(listOf(null, "draft1234567890123456"), backend.draftIdsSent)
    }

    @Test fun rejectedDraftIsForgottenAndRetriedOnceWithoutIt() {
        val vm = filledVm()
        vm.submit()
        backend.draftIdsSent.clear()
        backend.queue += RoutePreviewResult.DraftRejected
        backend.queue += RoutePreviewResult.Success("newdraft000000000000", "h")
        vm.submit()
        assertEquals(listOf("draft1234567890123456", null), backend.draftIdsSent)
        assertEquals("newdraft000000000000", vm.state.value.draftId)
        assertEquals("newdraft000000000000", vm.state.value.reviewDraftId)
        assertTrue(vm.state.value.errors.isEmpty())
    }

    @Test fun rejectedWithNoDraftIsAnError() {
        val vm = filledVm()
        backend.result = RoutePreviewResult.DraftRejected
        vm.submit()
        assertEquals(1, backend.calls.size)
        assertEquals(listOf(SeriesError("internal", "")), vm.state.value.errors)
    }

    @Test fun retryHappensOnlyOnce() {
        val vm = filledVm()
        vm.submit()
        backend.draftIdsSent.clear()
        backend.result = RoutePreviewResult.DraftRejected
        vm.submit()
        assertEquals(listOf("draft1234567890123456", null), backend.draftIdsSent)
        assertEquals(listOf(SeriesError("internal", "")), vm.state.value.errors)
        assertNull(vm.state.value.draftId)
    }

    @Test fun resetForgetsTheDraft() {
        val vm = filledVm()
        vm.submit()
        vm.reset()
        assertNull(vm.state.value.draftId)
    }

    @Test fun resetClearsTheForm() {
        val vm = filledVm()
        vm.reset()
        assertEquals(CreateSeriesUiState().copy(), vm.state.value)
    }
}
