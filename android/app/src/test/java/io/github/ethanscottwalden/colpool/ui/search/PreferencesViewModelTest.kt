package io.github.ethanscottwalden.colpool.ui.search

import io.github.ethanscottwalden.colpool.data.search.HostMatchFields
import io.github.ethanscottwalden.colpool.data.search.InMemoryPreferenceStore
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday.Mon
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday.Thu
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday.Tue
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
class PreferencesViewModelTest {
    private val backend = FakeSearchBackend().apply {
        series = listOf(SearchTestData.far(), SearchTestData.mid(), SearchTestData.near(), SearchTestData.last())
        names = mapOf("hostA" to "Ava", "hostB" to "Riley", "hostC" to "Jamal", "hostD" to "Maya")
        matchFields = mapOf(
            "hostA" to HostMatchFields("Ava", "junior", "nursing", listOf("music", "coffee"), listOf("chatty")),
            "hostB" to HostMatchFields("Riley", "freshman", "biology", listOf("gaming"), listOf("quiet")),
            "hostD" to HostMatchFields("Maya", "senior", "nursing", listOf("music"), emptyList()),
        )
    }
    private val store = InMemoryPreferenceStore()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = SearchViewModel(backend, SearchTestData.CLOCK, store)

    @Test fun orderIsIdenticalWithPreferencesOnAndOff() {
        val vm = vm()
        vm.toggle(Mon); vm.toggle(Tue); vm.toggle(Thu)
        val off = vm.state.value.results.map { it.series.id }
        assertTrue(off.size > 1)
        vm.toggleInterest("music"); vm.toggleTrait("chatty"); vm.toggleMajor("nursing"); vm.toggleYear("junior")
        vm.retry()
        val on = vm.state.value.results.map { it.series.id }
        assertEquals(off, on)
        vm.clearPrefs()
        vm.retry()
        assertEquals(off, vm.state.value.results.map { it.series.id })
        // The same cards, with different percentages: nothing filtered either.
        assertEquals(off.size, vm.state.value.results.size)
    }

    @Test fun orderStaysByDistanceWhenTheNearestMatchesWorstAndTheFarthestBest() {
        // near=hostA (nearest), last=hostD, mid=hostB, far=hostC (farthest).
        backend.matchFields = mapOf(
            "hostA" to HostMatchFields("Ava", null, "biology", listOf("gaming"), listOf("quiet")),
            "hostB" to HostMatchFields("Riley", "freshman", "biology", listOf("gaming"), listOf("quiet")),
            "hostD" to HostMatchFields("Maya", "junior", "biology", listOf("music"), listOf("quiet")),
            "hostC" to HostMatchFields("Jamal", "junior", "nursing", listOf("music"), listOf("chatty")),
        )
        val vm = vm()
        vm.toggle(Mon); vm.toggle(Tue); vm.toggle(Thu)
        val off = vm.state.value.results.map { it.series.id }
        assertEquals(listOf("near", "last", "mid", "far"), off)
        vm.toggleInterest("music"); vm.toggleTrait("chatty"); vm.toggleMajor("nursing"); vm.toggleYear("junior")
        vm.retry()
        assertEquals(off, vm.state.value.results.map { it.series.id })
        assertEquals(0, vm.state.value.matchPercent("hostA"))
        assertEquals(100, vm.state.value.matchPercent("hostC"))
        assertTrue(vm.state.value.matchPercent("hostA")!! < vm.state.value.matchPercent("hostC")!!)
    }

    @Test fun failedProfileReadIsRetriedOnTheNextRunAndFillsIn() {
        backend.matchFields = emptyMap()
        backend.names = emptyMap()
        val vm = vm()
        vm.toggle(Mon)
        vm.toggleInterest("music")
        assertNull(vm.state.value.matchPercent("hostA"))
        assertNull(vm.state.value.hostNames["hostA"])
        val first = backend.nameReads.count { it == "hostA" }
        backend.matchFields = mapOf("hostA" to HostMatchFields("Ava", interests = listOf("music")))
        vm.retry()
        assertEquals(first + 1, backend.nameReads.count { it == "hostA" })
        assertEquals(100, vm.state.value.matchPercent("hostA"))
        assertEquals("Ava", vm.state.value.hostNames["hostA"])
        // A successful read is cached: no further read.
        vm.retry()
        assertEquals(first + 1, backend.nameReads.count { it == "hostA" })
    }

    @Test fun matchPercentComesFromTheHostsProfileAndHidesWithoutPreferences() {
        val vm = vm()
        vm.toggle(Mon)
        assertNull(vm.state.value.matchPercent("hostA"))
        vm.toggleInterest("music")
        vm.toggleMajor("nursing")
        assertEquals(100, vm.state.value.matchPercent("hostA"))
        assertNull(vm.state.value.matchPercent("hostZ"))
        assertEquals(100, vm.state.value.matchPercent("hostD"))
        // An unreadable host gets no badge at all (not 0%); a readable one with empty fields scores 0.
        assertNull(vm.state.value.matchPercent("hostC"))
        vm.clearPrefs()
        assertNull(vm.state.value.matchPercent("hostA"))
    }

    @Test fun oneProfileReadPerHostEvenWhenPreferencesChange() {
        val vm = vm()
        vm.toggle(Mon)
        val reads = backend.nameReads.size
        vm.toggleInterest("music"); vm.toggleYear("junior"); vm.toggleYear("junior")
        vm.retry()
        assertEquals(reads, backend.nameReads.size)
        assertEquals(backend.nameReads.distinct(), backend.nameReads)
    }

    @Test fun oneMajorAndOneYearAtMostAndTheSameChoiceClears() {
        val vm = vm()
        vm.toggleMajor("nursing"); vm.toggleMajor("biology")
        assertEquals("biology", vm.state.value.prefs.major)
        vm.toggleMajor("biology")
        assertNull(vm.state.value.prefs.major)
        vm.toggleYear("junior"); vm.toggleYear("senior")
        assertEquals("senior", vm.state.value.prefs.year)
        vm.toggleInterest("music"); vm.toggleInterest("coffee")
        assertEquals(setOf("music", "coffee"), vm.state.value.prefs.interests)
        assertEquals(3, vm.state.value.prefs.count)
    }

    @Test fun preferencesAreStoredPerUidAndComeBack() {
        val vm = vm()
        vm.toggleInterest("music"); vm.toggleYear("junior")
        vm.reset()
        val again = vm()
        again.refresh()
        assertEquals(MatchPreferences(setOf("music"), year = "junior"), again.state.value.prefs)

        backend.uid = "someone-else"
        val other = vm()
        other.refresh()
        assertTrue(other.state.value.prefs.isEmpty)
        other.toggleTrait("quiet")
        assertEquals(MatchPreferences(setOf("music"), year = "junior"), store.load("me"))
        assertEquals(MatchPreferences(traits = setOf("quiet")), store.load("someone-else"))
    }

    @Test fun logoutClearsThatAccountsStoredPreferencesOnly() {
        val vm = vm()
        vm.toggleInterest("music")
        store.save("other", MatchPreferences(major = "nursing"))
        vm.clearStoredPreferences()
        assertTrue(store.load("me").isEmpty)
        assertTrue(vm.state.value.prefs.isEmpty)
        assertEquals("nursing", store.load("other").major)
        vm.reset()
        val again = vm()
        again.refresh()
        assertTrue(again.state.value.prefs.isEmpty)
    }

    @Test fun panelLoadsOptionsOnceAndCanRetry() {
        backend.optionsError = RuntimeException("offline")
        val vm = vm()
        vm.openPanel()
        assertTrue(vm.state.value.panelOpen)
        assertTrue(vm.state.value.optionsFailed)
        backend.optionsError = null
        vm.retryOptions()
        assertFalse(vm.state.value.optionsFailed)
        assertTrue(vm.state.value.options.majors.size > 100)
        vm.closePanel(); vm.openPanel()
        assertEquals(2, backend.optionLoads)
    }

    @Test fun preferencesNeverAppearInToString() {
        val s = MatchPreferences(setOf("music"), major = "nursing").toString()
        assertFalse(s.contains("music") || s.contains("nursing"))
    }
}
