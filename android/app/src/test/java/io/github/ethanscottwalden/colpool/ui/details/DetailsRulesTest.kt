package io.github.ethanscottwalden.colpool.ui.details

import io.github.ethanscottwalden.colpool.R
import io.github.ethanscottwalden.colpool.data.requests.HostProfile
import io.github.ethanscottwalden.colpool.ui.search.DayState
import io.github.ethanscottwalden.colpool.ui.search.SearchTestData
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday.Fri
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday.Mon
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday.Wed
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailsRulesTest {
    private val now = SearchTestData.NOW

    @Test fun dayStatesAreSeatsTimesTripsLeftOnRunningDaysOnly() {
        val states = DetailsRules.dayStates(DetailsTestData.mixed(), now)
        assertEquals(mapOf(Mon to DayState.Open, Wed to DayState.Full, Fri to DayState.NoTripsLeft), states)
    }

    @Test fun onlyOpenDaysAreSelectable() {
        val states = DetailsRules.dayStates(DetailsTestData.mixed(), now)
        assertEquals(setOf(Mon), DetailsRules.selectable(states, emptySet()))
        // A day the server refused is not selectable again.
        assertEquals(emptySet<Any>(), DetailsRules.selectable(states, setOf(Mon)))
    }

    @Test fun noTripsLeftBeatsFullWhenBoth() {
        val s = SearchTestData.series("x", SearchTestData.doc("h", "P", 33.9, -117.3, listOf("mon"), open = mapOf("mon" to 0), lastDeparture = mapOf("mon" to Instant.parse("2026-10-01T00:00:00Z"))))
        assertEquals(DayState.NoTripsLeft, DetailsRules.dayStates(s, now)[Mon])
    }

    /** Pacific clocks fall back on Sunday Nov 1, 2026 (a 25-hour day). A trip is left only when its last departure is strictly after now. */
    @Test fun tripsLeftAroundTheFallBackChange() {
        val change = Instant.parse("2026-11-01T09:30:00Z") // 1:30 AM PDT, the first 1:30 AM
        fun state(lastDeparture: Instant, clock: Instant) = DetailsRules.dayStates(
            SearchTestData.series("d", SearchTestData.doc("h", "P", 33.9, -117.3, listOf("mon"), lastDeparture = mapOf("mon" to lastDeparture))), clock,
        )[Mon]
        // 1:30 AM PST (an hour later in real time) is after the first 1:30 AM.
        assertEquals(DayState.Open, state(Instant.parse("2026-11-01T09:30:01Z"), change))
        assertEquals(DayState.NoTripsLeft, state(change, change))
        assertEquals(DayState.NoTripsLeft, state(Instant.parse("2026-11-01T08:59:59Z"), change))
        // Monday after the change, 7:00 AM PST.
        assertEquals(DayState.Open, state(Instant.parse("2026-11-02T15:00:01Z"), now))
        assertEquals(DayState.NoTripsLeft, state(Instant.parse("2026-11-02T15:00:00Z"), now))
    }

    @Test fun hostFieldsShowOnlyFilledInFieldsInOrder() {
        val f = DetailsRules.hostFields(DetailsTestData.AVA, DetailsTestData.OPTIONS)
        assertEquals(
            listOf(R.string.profile_year, R.string.profile_major, R.string.profile_gender, R.string.profile_age, R.string.profile_interests, R.string.profile_traits, R.string.profile_about_me),
            f.map { if (it is HostField.Text) it.label else (it as HostField.Chips).label },
        )
        assertEquals(HostField.Chips(R.string.profile_interests, listOf("Music", "Coffee", "Hiking")), f[4])
    }

    @Test fun emptyOptionalFieldsAreHidden() {
        assertTrue(DetailsRules.hostFields(DetailsTestData.MINIMAL, DetailsTestData.OPTIONS).isEmpty())
        val blank = HostProfile("Riley", "Chen", aboutMe = "   ", interests = emptyList(), year = "unknown-id")
        assertTrue(DetailsRules.hostFields(blank, DetailsTestData.OPTIONS).isEmpty())
        val some = DetailsRules.hostFields(HostProfile("Riley", "Chen", major = "nursing"), DetailsTestData.OPTIONS)
        assertEquals(listOf<HostField>(HostField.Text(R.string.profile_major, "Nursing")), some)
    }
}
