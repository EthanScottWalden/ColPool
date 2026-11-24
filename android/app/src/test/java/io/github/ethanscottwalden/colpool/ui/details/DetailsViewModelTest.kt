package io.github.ethanscottwalden.colpool.ui.details

import io.github.ethanscottwalden.colpool.data.requests.DayFailure
import io.github.ethanscottwalden.colpool.data.requests.DayFailureReason
import io.github.ethanscottwalden.colpool.data.requests.SendRequestResult
import io.github.ethanscottwalden.colpool.ui.search.DayState
import io.github.ethanscottwalden.colpool.ui.search.SearchTestData
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday.Fri
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday.Mon
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday.Wed
import kotlinx.coroutines.CompletableDeferred
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
class DetailsViewModelTest {
    private val backend = FakeRequestBackend()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(series: io.github.ethanscottwalden.colpool.data.series.HostedSeries = DetailsTestData.open()) =
        DetailsViewModel(backend, SearchTestData.CLOCK).also { it.open(series, 41, "Ava") }

    @Test fun openShowsTheSeriesAndLoadsTheProfileAndLabels() {
        val vm = vm()
        val s = vm.state.value
        assertEquals("near", s.series?.id)
        assertEquals(Mon, s.activeDay)
        assertEquals(DetailsTestData.AVA, s.profile)
        assertTrue(s.profileLoaded)
        assertEquals("Yeager Center", s.campusNames["yeager-center"])
        assertEquals(Standing.Requestable, s.standing)
        assertEquals(setOf(Mon, Wed, Fri), s.selectable)
    }

    @Test fun onlyOpenDaysCanBePicked() {
        val vm = vm(DetailsTestData.mixed())
        vm.togglePick(Wed)
        vm.togglePick(Fri)
        assertTrue(vm.state.value.picked.isEmpty())
        assertFalse(vm.state.value.canSend)
        vm.togglePick(Mon)
        assertEquals(setOf(Mon), vm.state.value.picked)
        assertTrue(vm.state.value.canSend)
        vm.togglePick(Mon)
        assertFalse(vm.state.value.canSend)
    }

    @Test fun confirmNeedsADayAndSendSendsOnceInDayOrder() {
        val vm = vm()
        vm.openConfirm()
        assertFalse(vm.state.value.confirming)
        vm.togglePick(Fri)
        vm.togglePick(Mon)
        vm.openConfirm()
        assertTrue(vm.state.value.confirming)
        backend.gate = CompletableDeferred()
        vm.send()
        vm.send() // a second tap while sending is ignored
        assertTrue(vm.state.value.sending)
        assertEquals(listOf("near" to listOf("mon", "fri")), backend.sends)
        backend.gate!!.complete(Unit)
        assertTrue(vm.state.value.sent)
        assertFalse(vm.state.value.sending)
        vm.consumeSent()
        assertFalse(vm.state.value.sent)
    }

    @Test fun sendNeedsTheConfirmSheet() {
        val vm = vm()
        vm.togglePick(Mon)
        vm.send()
        assertTrue(backend.sends.isEmpty())
    }

    @Test fun perDayFailureMarksAndDeselectsThoseDaysAndKeepsTheOthers() {
        val vm = vm()
        vm.togglePick(Mon); vm.togglePick(Wed); vm.togglePick(Fri)
        vm.openConfirm()
        backend.result = SendRequestResult.WeekdaysUnavailable(listOf(DayFailure("mon", DayFailureReason.Full), DayFailure("fri", DayFailureReason.Conflict)))
        vm.send()
        val s = vm.state.value
        assertEquals(mapOf(Mon to DayFailureReason.Full, Fri to DayFailureReason.Conflict), s.refused)
        assertEquals(setOf(Wed), s.picked)
        assertEquals(SendError.SomeDays, s.error)
        assertFalse(s.confirming)
        assertFalse(s.sent)
        assertEquals(setOf(Wed), s.selectable)
        // The rider can send again with the remaining day.
        vm.openConfirm()
        backend.result = SendRequestResult.Sent
        vm.send()
        assertEquals(listOf("mon", "wed", "fri"), backend.sends.first().second)
        assertEquals(listOf("wed"), backend.sends.last().second)
        assertTrue(vm.state.value.sent)
    }

    @Test fun failedDaysThatEmptyThePickDisableTheButton() {
        val vm = vm()
        vm.togglePick(Mon)
        vm.openConfirm()
        backend.result = SendRequestResult.WeekdaysUnavailable(listOf(DayFailure("mon", DayFailureReason.NoTripsLeft)))
        vm.send()
        assertFalse(vm.state.value.canSend)
    }

    private fun failWith(r: SendRequestResult): DetailsUiState {
        val vm = vm()
        vm.togglePick(Mon)
        vm.openConfirm()
        backend.result = r
        vm.send()
        return vm.state.value
    }

    @Test fun otherErrorsMapToStandingsAndMessages() {
        failWith(SendRequestResult.Invalid).let { assertEquals(SendError.Invalid, it.error); assertEquals(Standing.Requestable, it.standing) }
        failWith(SendRequestResult.Failed).let { assertEquals(SendError.Retry, it.error); assertEquals(setOf(Mon), it.picked) }
        failWith(SendRequestResult.NotAvailable).let { assertEquals(SendError.Gone, it.error); assertEquals(Standing.Gone, it.standing) }
        failWith(SendRequestResult.OwnSeries).let { assertEquals(SendError.OwnSeries, it.error); assertEquals(Standing.Own, it.standing) }
        failWith(SendRequestResult.AlreadyRequested).let { assertEquals(Standing.AlreadyRequested, it.standing); assertNull(it.error) }
    }

    @Test fun anAlreadyRequestedSeriesHasNoPicker() {
        backend.requested = true
        val vm = vm()
        assertEquals(Standing.AlreadyRequested, vm.state.value.standing)
        vm.togglePick(Mon)
        vm.openConfirm()
        assertTrue(vm.state.value.picked.isEmpty())
        assertFalse(vm.state.value.confirming)
    }

    @Test fun ownSeriesHasNoPickerAndReadsNoRequest() {
        backend.uid = "hostA"
        val vm = vm()
        assertEquals(Standing.Own, vm.state.value.standing)
        vm.togglePick(Mon)
        assertTrue(vm.state.value.picked.isEmpty())
        // Own series: the profile and the schedule still show.
        assertEquals(DetailsTestData.AVA, vm.state.value.profile)
    }

    @Test fun unreadableProfileKeepsTheHintName() {
        backend.profile = null
        val vm = vm()
        assertNull(vm.state.value.profile)
        assertTrue(vm.state.value.profileLoaded)
        assertEquals("Ava", vm.state.value.firstNameHint)
    }

    @Test fun resetForgetsEverything() {
        val vm = vm()
        vm.togglePick(Mon)
        vm.reset()
        assertNull(vm.state.value.series)
        assertTrue(vm.state.value.picked.isEmpty())
        assertEquals(DayState.Open, DetailsRules.dayStates(DetailsTestData.open(), SearchTestData.NOW)[Mon])
    }
}
