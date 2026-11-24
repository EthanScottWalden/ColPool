package io.github.ethanscottwalden.colpool.ui.notifications

import io.github.ethanscottwalden.colpool.ui.notifications.NotificationsTestData.note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {
    private val backend = FakeNotificationsBackend()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun started(): NotificationsViewModel = NotificationsViewModel(backend).also { it.start() }

    @Test fun whatIsThereAtStartIsHistoryNotABanner() = runTest {
        val vm = started()
        backend.load(note("a"), note("b", at = 2_000))
        val s = vm.state.value
        assertNull(s.banner)
        assertEquals(2, s.unreadCount)
        assertEquals(listOf("b", "a"), s.items.map { it.id })
        assertTrue(s.loaded)
    }

    @Test fun aNewNotificationShowsOnceAsABanner() = runTest {
        val vm = started()
        backend.load(note("a"))
        backend.load(note("b", at = 2_000), note("a"))
        assertEquals("b", vm.state.value.banner?.id)
        assertEquals("New ride request", vm.state.value.banner?.title)
        // The same snapshot again (e.g. a metadata change) does not bring it back.
        vm.dismissBanner()
        backend.load(note("b", at = 2_000), note("a"))
        assertNull(vm.state.value.banner)
    }

    @Test fun anAlreadyReadNewRecordIsNotABanner() = runTest {
        val vm = started()
        backend.load()
        backend.load(note("a", read = true))
        assertNull(vm.state.value.banner)
    }

    @Test fun severalAtOnceShowOneAtATimeNewestFirst() = runTest {
        val vm = started()
        backend.load()
        backend.load(note("x", at = 1_000), note("z", at = 3_000), note("y", at = 2_000))
        assertEquals("z", vm.state.value.banner?.id)
        vm.dismissBanner()
        assertEquals("y", vm.state.value.banner?.id)
        vm.dismissBanner()
        assertEquals("x", vm.state.value.banner?.id)
        vm.dismissBanner()
        assertNull(vm.state.value.banner)
    }

    @Test fun aLaterArrivalWaitsBehindTheShownOneAndJumpsTheQueue() = runTest {
        val vm = started()
        backend.load()
        backend.load(note("a", at = 1_000), note("b", at = 2_000))
        assertEquals("b", vm.state.value.banner?.id)
        backend.load(note("c", at = 3_000), note("a", at = 1_000), note("b", at = 2_000))
        assertEquals("b", vm.state.value.banner?.id)
        vm.dismissBanner()
        assertEquals("c", vm.state.value.banner?.id)
        vm.dismissBanner()
        assertEquals("a", vm.state.value.banner?.id)
    }

    @Test fun noBannerWhileTheTabIsOpen() = runTest {
        val vm = started()
        backend.load(note("a"))
        vm.openTab()
        backend.load(note("b", at = 2_000), note("a", read = true))
        assertNull(vm.state.value.banner)
        // Seen on arrival: the "New" tag stays for this visit and it is marked read.
        assertTrue("b" in vm.state.value.newIds)
        assertEquals(listOf(listOf("a"), listOf("b")), backend.markedRead)
        assertEquals(0, vm.state.value.unreadCount)
    }

    @Test fun openingTheTabDropsTheBannerAndTheWaitingOnes() = runTest {
        val vm = started()
        backend.load()
        backend.load(note("a", at = 1_000), note("b", at = 2_000))
        assertEquals("b", vm.state.value.banner?.id)
        vm.openTab()
        assertNull(vm.state.value.banner)
        vm.closeTab()
        vm.dismissBanner()
        assertNull(vm.state.value.banner)
    }

    @Test fun openingTheTabClearsTheBadgeAtOnceAndKeepsNewTagsForTheVisit() = runTest {
        val vm = started()
        backend.load(note("a"), note("b", at = 2_000), note("c", at = 500, read = true))
        assertEquals(2, vm.state.value.unreadCount)
        vm.openTab()
        assertEquals(0, vm.state.value.unreadCount)
        assertEquals(setOf("a", "b"), vm.state.value.newIds)
        // The server confirms: read is true now, but the tags stay while the tab is open.
        backend.load(note("a").copy(read = true), note("b", at = 2_000).copy(read = true), note("c", at = 500, read = true))
        assertEquals(setOf("a", "b"), vm.state.value.newIds)
        vm.closeTab()
        assertEquals(emptySet<String>(), vm.state.value.newIds)
        assertEquals(0, vm.state.value.unreadCount)
    }

    @Test fun markReadSendsOnlyUnreadIdsOnceAndNothingWhenAllRead() = runTest {
        val vm = started()
        backend.load(note("a"), note("b", at = 2_000), note("c", at = 500, read = true))
        vm.openTab()
        assertEquals(listOf(listOf("b", "a")), backend.markedRead)
        vm.closeTab()
        vm.openTab()
        assertEquals(1, backend.markedRead.size)
        backend.markedRead.clear()
        vm.closeTab()
        backend.load(note("c", at = 500, read = true))
        vm.openTab()
        assertTrue(backend.markedRead.isEmpty())
    }

    @Test fun aFailedWriteIsRetriedTheNextTimeTheTabOpens() = runTest {
        val vm = started()
        backend.load(note("a"))
        backend.markError = RuntimeException("offline")
        vm.openTab()
        assertEquals(0, vm.state.value.unreadCount) // cleared at once even though the write is out
        vm.closeTab()
        assertEquals(1, vm.state.value.unreadCount) // still unread on the server, so the badge is honest again
        assertFalse(vm.state.value.banner != null)
        backend.markError = null
        vm.openTab()
        assertEquals(listOf(listOf("a")), backend.markedRead)
        assertEquals(0, vm.state.value.unreadCount)
    }

    @Test fun failureShowsErrorAndRetryListensAgainWithoutBannersForWhatWasMissed() = runTest {
        val vm = started()
        backend.fail()
        assertTrue(vm.state.value.failed)
        assertTrue(vm.state.value.loaded)
        vm.retry()
        assertEquals(2, backend.observeCalls)
        assertEquals(1, backend.listeners)
        assertFalse(vm.state.value.failed)
        backend.load(note("a"))
        assertNull(vm.state.value.banner)
        assertEquals(1, vm.state.value.unreadCount)
    }

    @Test fun oneListenerEvenIfStartedTwice() = runTest {
        val vm = started()
        vm.start()
        assertEquals(1, backend.observeCalls)
        assertEquals(1, backend.listeners)
    }

    @Test fun resetStopsListeningAndForgetsEverything() = runTest {
        val vm = started()
        backend.load()
        backend.load(note("a"))
        vm.reset()
        assertEquals(0, backend.listeners)
        assertEquals(NotificationsUiState(), vm.state.value)
        backend.load(note("late"))
        assertTrue(vm.state.value.items.isEmpty())
        assertNull(vm.state.value.banner)
    }

    @Test fun anotherAccountStartsFromNothing() = runTest {
        val vm = started()
        backend.load(note("a"))
        vm.reset()
        vm.start()
        backend.load(note("other", at = 9_000))
        // First snapshot of the new session is history, not a banner, and nothing of the old one is left.
        assertNull(vm.state.value.banner)
        assertEquals(listOf("other"), vm.state.value.items.map { it.id })
    }

    @Test fun anInFlightWriteIsCancelledByReset() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val slow = object : io.github.ethanscottwalden.colpool.data.notifications.NotificationsBackend by backend {
            override suspend fun markRead(ids: List<String>) { gate.await(); backend.markedRead += ids }
        }
        val vm = NotificationsViewModel(slow).also { it.start() }
        backend.load(note("a"))
        vm.openTab()
        vm.reset()
        gate.complete(Unit)
        assertTrue(backend.markedRead.isEmpty())
    }

    @Test fun stateToStringIsRedacted() {
        val s = NotificationsUiState(items = listOf(note("a")), banner = note("a"))
        assertFalse(s.toString().contains("Eli"))
    }
}
