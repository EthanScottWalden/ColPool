package io.github.ethanscottwalden.colpool.ui.series

import io.github.ethanscottwalden.colpool.data.series.DraftLoadResult
import io.github.ethanscottwalden.colpool.data.series.PostSeriesResult
import io.github.ethanscottwalden.colpool.data.series.SeriesError
import kotlinx.coroutines.CompletableDeferred
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
class ReviewViewModelTest {
    private val backend = FakeSeriesBackend()
    private val id = "draft1234567890123456"

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = ReviewViewModel(backend).also { it.load(id) }

    @Test fun loadsTheStoredDraftAndCampusNamesAndSelectsTheFirstDay() {
        val vm = vm()
        val s = vm.state.value
        assertEquals(ReviewLoad.Loaded, s.load)
        assertEquals(SeriesWeekday.Mon, s.activeDay)
        assertEquals("Yeager Center", s.campusNames["yeager-center"])
        assertEquals(listOf(id), backend.draftLoads)
    }

    @Test fun loadingTwiceForTheSameIdReadsOnce() {
        val vm = vm()
        vm.load(id)
        assertEquals(1, backend.draftLoads.size)
    }

    @Test fun missingDraftAndFailureAreDifferentStates() {
        backend.draftResult = DraftLoadResult.NotFound
        assertEquals(ReviewLoad.NotFound, vm().state.value.load)
        backend.draftResult = DraftLoadResult.Failed
        val vm = vm()
        assertEquals(ReviewLoad.Failed, vm.state.value.load)
        backend.draftResult = DraftLoadResult.Loaded(ReviewTestData.draft())
        vm.retry()
        assertEquals(ReviewLoad.Loaded, vm.state.value.load)
    }

    @Test fun campusPointFailureIsALoadFailure() {
        backend.campusError = RuntimeException("offline")
        assertEquals(ReviewLoad.Failed, vm().state.value.load)
    }

    @Test fun onlySelectedDaysCanBeActive() {
        val vm = vm()
        vm.selectDay(SeriesWeekday.Tue)
        assertEquals(SeriesWeekday.Mon, vm.state.value.activeDay)
        vm.selectDay(SeriesWeekday.Wed)
        assertEquals(SeriesWeekday.Wed, vm.state.value.activeDay)
    }

    @Test fun postSendsTheStoredInputUnchangedAndExitsToHosting() {
        val vm = vm()
        vm.openConfirm()
        vm.post()
        assertEquals(1, backend.posts.size)
        assertEquals(id, backend.posts[0].first)
        assertEquals(ReviewTestData.draft().input, backend.posts[0].second)
        assertEquals(ReviewExit.Posted, vm.state.value.exit)
        assertTrue(!vm.state.value.posting)
    }

    @Test fun alreadyPostedAlsoExitsToHosting() {
        backend.postResult = PostSeriesResult.Posted(id, alreadyPosted = true)
        val vm = vm()
        vm.post()
        assertEquals(ReviewExit.Posted, vm.state.value.exit)
    }

    @Test fun repeatTapsWhilePostingSendOnePost() {
        val gate = CompletableDeferred<Unit>()
        backend.postGate = gate
        val vm = vm()
        vm.openConfirm()
        vm.post(); vm.post(); vm.post()
        assertTrue(vm.state.value.posting)
        assertEquals(1, backend.posts.size)
        // The sheet and Go back can't be dismissed while posting.
        vm.dismissConfirm(); vm.goBack()
        assertTrue(vm.state.value.confirming)
        assertNull(vm.state.value.exit)
        gate.complete(Unit)
        assertEquals(ReviewExit.Posted, vm.state.value.exit)
    }

    @Test fun draftChangedGoesBackToTheFormWithAMessage() {
        backend.postResult = PostSeriesResult.DraftChanged
        val vm = vm()
        vm.post()
        assertEquals(ReviewExit.Edit(listOf(SeriesError("draftChanged", ""))), vm.state.value.exit)
    }

    @Test fun invalidSeriesStaysThenGoBackCarriesTheErrors() {
        val errors = listOf(SeriesError("startInPast", "startDate"), SeriesError("hostingWeekdayOverlap", "days.wed", mapOf("weekday" to "wed")))
        backend.postResult = PostSeriesResult.Invalid(errors)
        val vm = vm()
        vm.openConfirm()
        vm.post()
        assertEquals(PostError.Invalid(errors), vm.state.value.postError)
        assertNull(vm.state.value.exit)
        assertTrue(!vm.state.value.confirming)
        // Posting again is blocked until the host goes back and fixes it.
        vm.openConfirm()
        assertTrue(!vm.state.value.confirming)
        vm.goBack()
        assertEquals(ReviewExit.Edit(errors), vm.state.value.exit)
    }

    @Test fun notFoundStaysWithAMessageAndCannotBePostedAgain() {
        backend.postResult = PostSeriesResult.NotFound
        val vm = vm()
        vm.post()
        assertEquals(PostError.NotFound, vm.state.value.postError)
        vm.openConfirm()
        assertTrue(!vm.state.value.confirming)
        vm.goBack()
        assertEquals(ReviewExit.Edit(), vm.state.value.exit)
    }

    @Test fun unavailableOrNetworkStaysAndCanRetry() {
        backend.postResult = PostSeriesResult.Failed
        val vm = vm()
        vm.openConfirm()
        vm.post()
        assertEquals(PostError.Retry, vm.state.value.postError)
        assertNull(vm.state.value.exit)
        backend.postResult = PostSeriesResult.Posted(id, false)
        vm.openConfirm()
        assertTrue(vm.state.value.confirming)
        vm.post()
        assertNull(vm.state.value.postError)
        assertEquals(ReviewExit.Posted, vm.state.value.exit)
        assertEquals(2, backend.posts.size)
    }

    @Test fun consumeExitAndResetClearTheState() {
        val vm = vm()
        vm.post()
        vm.consumeExit()
        assertNull(vm.state.value.exit)
        vm.reset()
        assertEquals(ReviewLoad.Loading, vm.state.value.load)
        assertNull(vm.state.value.draft)
    }
}
