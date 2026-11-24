package io.github.ethanscottwalden.colpool.ui.search

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.ethanscottwalden.colpool.data.search.FirebaseSearchBackend
import io.github.ethanscottwalden.colpool.data.search.GeoPoint
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday.Fri
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday.Mon
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday.Thu
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday.Tue
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday.Wed
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class SearchRulesTest {
    private val res = ApplicationProvider.getApplicationContext<Application>().resources
    private val now = SearchTestData.NOW

    // ---- distance ----

    @Test fun haversineKnownPairs() {
        assertEquals(0.0, SearchRules.haversineMiles(33.95, -117.39, 33.95, -117.39), 1e-9)
        // One degree of longitude at the equator: 2 * pi * R / 360.
        assertEquals(69.09, SearchRules.haversineMiles(0.0, 0.0, 0.0, 1.0), 0.01)
        assertEquals(69.09, SearchRules.haversineMiles(0.0, 0.0, 1.0, 0.0), 0.01)
        // LAX to JFK is about 2,475 miles by great circle.
        assertEquals(2475.0, SearchRules.haversineMiles(33.9416, -118.4085, 40.6413, -73.7781), 6.0)
        // Symmetric.
        assertEquals(SearchRules.haversineMiles(33.9, -117.3, 34.0, -117.5), SearchRules.haversineMiles(34.0, -117.5, 33.9, -117.3), 1e-9)
    }

    @Test fun roundsToOneDecimal() {
        assertEquals(41, SearchRules.tenths(4.1))
        assertEquals(41, SearchRules.tenths(4.14))
        assertEquals(42, SearchRules.tenths(4.15))
        assertEquals(0, SearchRules.tenths(0.04))
        assertEquals(691, SearchRules.tenths(SearchRules.haversineMiles(0.0, 0.0, 0.0, 1.0)))
    }

    // ---- trips left, in a daylight-saving week (clocks went back Sun Nov 1, 2026) ----

    @Test fun tripsLeftComparesTheInstant() {
        // Monday's last departure 7:30 AM PST = 15:30Z; now is 7:00 AM PST.
        val s = SearchTestData.series("s", SearchTestData.doc("h", "p", 33.9, -117.3, listOf("mon"), lastDeparture = mapOf("mon" to Instant.parse("2026-11-02T15:30:00Z"))))
        assertTrue(SearchRules.hasTripsLeft(s, "mon", now))
        assertTrue(SearchRules.hasTripsLeft(s, "mon", Instant.parse("2026-11-02T15:29:59Z")))
        assertFalse(SearchRules.hasTripsLeft(s, "mon", Instant.parse("2026-11-02T15:30:00Z")))
        assertFalse(SearchRules.hasTripsLeft(s, "mon", Instant.parse("2026-11-02T16:00:00Z")))
        // An absent key means none.
        assertFalse(SearchRules.hasTripsLeft(s, "tue", now))
    }

    @Test fun lastDepartureAcrossTheChangeStillCounts() {
        // Friday Oct 30 (PDT) 7:30 AM = 14:30Z, the Friday before the change; Sunday Nov 1 1:30 AM exists twice.
        val s = SearchTestData.series("s", SearchTestData.doc("h", "p", 33.9, -117.3, listOf("fri"), lastDeparture = mapOf("fri" to Instant.parse("2026-10-30T14:30:00Z"))))
        assertFalse(SearchRules.hasTripsLeft(s, "fri", Instant.parse("2026-10-30T14:30:00Z")))
        assertTrue(SearchRules.hasTripsLeft(s, "fri", Instant.parse("2026-10-30T14:29:00Z")))
        assertTrue(SearchRules.hasTripsLeft(s, "fri", Instant.parse("2026-10-30T08:00:00Z")))
    }

    // ---- qualification per weekday: seat x trips left x picked ----

    private fun one(open: Int, tripsLeft: Boolean) = SearchTestData.series(
        "s", SearchTestData.doc("h", "p", 33.9, -117.3, listOf("mon", "wed"), open = mapOf("mon" to open, "wed" to 2), lastDeparture = mapOf(
            "mon" to if (tripsLeft) SearchTestData.FAR_FUTURE else Instant.parse("2026-11-01T20:00:00Z"),
            "wed" to if (tripsLeft) SearchTestData.FAR_FUTURE else Instant.parse("2026-11-01T20:00:00Z"),
        )),
    )

    @Test fun dayStateTable() {
        val picked = setOf(Mon)
        assertEquals(DayState.Open, SearchRules.dayState(one(2, true), Mon, picked, now))
        assertEquals(DayState.Full, SearchRules.dayState(one(0, true), Mon, picked, now))
        assertEquals(DayState.NoTripsLeft, SearchRules.dayState(one(2, false), Mon, picked, now))
        assertEquals(DayState.NoTripsLeft, SearchRules.dayState(one(0, false), Mon, picked, now))
        assertEquals(DayState.NotNeeded, SearchRules.dayState(one(2, true), Wed, picked, now))
    }

    @Test fun qualifiesOnlyWithAPickedDayThatIsOpenWithTripsLeft() {
        assertTrue(SearchRules.qualifies(one(2, true), setOf(Mon), now))
        assertFalse(SearchRules.qualifies(one(0, true), setOf(Mon), now))
        assertFalse(SearchRules.qualifies(one(2, false), setOf(Mon), now))
        // Wednesday open, Monday full: qualifies when Wednesday is picked, not when only Monday is.
        assertTrue(SearchRules.qualifies(one(0, true), setOf(Mon, Wed), now))
        assertFalse(SearchRules.qualifies(one(0, true), setOf(Mon, Tue), now))
        // Not a day of the series.
        assertFalse(SearchRules.qualifies(one(2, true), setOf(Fri), now))
    }

    @Test fun aSeriesWhoseOnlyOpenDayHasNoTripsLeftIsExcluded() {
        val s = SearchTestData.series("s", SearchTestData.doc("h", "p", 33.9, -117.3, listOf("tue", "thu"), open = mapOf("tue" to 1, "thu" to 0), lastDeparture = mapOf("tue" to Instant.parse("2026-10-27T20:00:00Z"), "thu" to SearchTestData.FAR_FUTURE)))
        assertTrue(SearchRules.rank(listOf(s), setOf(Tue, Thu), now, "me", emptySet(), SearchTestData.RIDER).isEmpty())
    }

    // ---- ranking and exclusion ----

    private val all get() = listOf(SearchTestData.far(), SearchTestData.mid(), SearchTestData.near(), SearchTestData.last())

    @Test fun nearestFirst() {
        val r = SearchRules.rank(all, setOf(Mon, Wed, Tue, Thu), now, "me", emptySet(), SearchTestData.RIDER)
        // far's Tuesday has no trips left but Thursday is open, so it qualifies.
        assertEquals(listOf("near", "last", "mid", "far"), r.map { it.series.id })
        assertTrue(r.zipWithNext().all { (a, b) -> a.distanceTenths!! <= b.distanceTenths!! })
    }

    @Test fun distanceIsStraightLineFromTheRidersStartToThePickupSpot() {
        val r = SearchRules.rank(listOf(SearchTestData.near()), setOf(Mon), now, "me", emptySet(), SearchTestData.RIDER).single()
        val miles = SearchRules.haversineMiles(33.9380, -117.3560, 33.9190, -117.3700)
        assertEquals(SearchRules.tenths(miles), r.distanceTenths)
    }

    @Test fun tiesBreakByStartDateThenId() {
        fun at(id: String, start: String) = SearchTestData.series(id, SearchTestData.doc("h$id", "p", 33.9190, -117.3700, listOf("mon"), start = start))
        val r = SearchRules.rank(listOf(at("b", "2026-12-07"), at("c", "2026-11-30"), at("a", "2026-12-07")), setOf(Mon), now, "me", emptySet(), SearchTestData.RIDER)
        assertEquals(listOf("c", "a", "b"), r.map { it.series.id })
    }

    @Test fun ownAndAlreadyRequestedSeriesAreLeftOut() {
        val mine = SearchTestData.series("mine", SearchTestData.doc("me", "p", 33.9190, -117.3700, listOf("mon")))
        val r = SearchRules.rank(listOf(mine, SearchTestData.near(), SearchTestData.last()), setOf(Mon), now, "me", setOf("near"), SearchTestData.RIDER)
        assertEquals(listOf("last"), r.map { it.series.id })
    }

    @Test fun withoutStartingCoordinatesThereIsNoRankingOrDistance() {
        val r = SearchRules.rank(all, setOf(Mon, Wed, Tue, Thu), now, "me", emptySet(), null)
        assertTrue(r.all { it.distanceTenths == null })
        assertEquals(listOf("far", "mid", "near", "last"), r.map { it.series.id })
    }

    @Test fun chipsCoverTheSeriesDaysAndFirstOpenPickedDayLeads() {
        val r = SearchRules.rank(listOf(SearchTestData.mid()), setOf(Mon, Tue), now, "me", emptySet(), SearchTestData.RIDER).single()
        assertEquals(listOf(Mon to DayState.Full, Tue to DayState.Open, Thu to DayState.NotNeeded), r.days)
        assertEquals(Tue, r.firstOpen)
    }

    // ---- card text ----

    @Test fun storedTimes() {
        val t = SearchRules.times(SearchTestData.near(), Mon)!!
        assertEquals(450, t.leave)
        assertEquals(450 + 27, t.atCampus)
        assertEquals(930, t.leaveCampus)
        assertNull(SearchRules.times(SearchTestData.near(), Tue))
    }

    @Test fun cardText() {
        assertEquals("4.1 mi away (straight line)", SearchText.distance(res, 41))
        assertEquals("0.4 mi away (straight line)", SearchText.distance(res, 4))
        assertEquals("Monday: leaves 7:30 AM, at campus ~7:57 AM, leaves campus 3:30 PM", SearchText.times(res, Mon, SearchRules.times(SearchTestData.near(), Mon)!!))
        assertEquals("$4 cash per round trip", SearchText.cash(res, 4))
        assertEquals("Hosted by Ava", SearchText.host(res, "Ava"))
        assertEquals("Host’s name unavailable", SearchText.host(res, null))
        assertEquals("1 series near you", SearchText.count(res, 1, true))
        assertEquals("8 series near you", SearchText.count(res, 8, true))
        assertEquals("2 series with an open seat", SearchText.count(res, 2, false))
        assertEquals(listOf("open", "full", "no trips left", "not needed"), DayState.entries.map { SearchText.dayState(res, it) })
    }

    // ---- parsing ----

    @Test fun parserKeepsHostAndPickupCoordinates() {
        val s = SearchTestData.near()
        assertEquals("hostA", s.hostId)
        assertEquals(33.9190, s.pickupLat!!, 1e-9)
        val none = SearchTestData.series("n", SearchTestData.doc("h", "p", null, null))
        assertNull(none.pickupLat)
        // A series without coordinates still lists, just without a distance.
        assertNull(SearchRules.rank(listOf(none), setOf(Mon), now, "me", emptySet(), SearchTestData.RIDER).single().distanceTenths)
    }

    @Test fun startingPointParsing() {
        assertEquals(GeoPoint(33.9, -117.3), FirebaseSearchBackend.parseStartingPoint(mapOf("startingAddress" to mapOf("text" to "x", "lat" to 33.9, "lng" to -117.3))))
        assertNull(FirebaseSearchBackend.parseStartingPoint(null))
        assertNull(FirebaseSearchBackend.parseStartingPoint(mapOf("startingAddress" to mapOf("text" to "x"))))
        assertNull(FirebaseSearchBackend.parseStartingPoint(mapOf("startingAddress" to mapOf("lat" to 133.9, "lng" to -117.3))))
    }

    @Test fun picksAreMondayFirst() {
        assertEquals(listOf("mon", "tue", "wed", "thu", "fri"), SeriesWeekday.entries.map { it.id })
    }
}
