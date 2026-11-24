package io.github.ethanscottwalden.colpool.ui.hosting

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class HostingTextTest {
    private val res = ApplicationProvider.getApplicationContext<Application>().resources

    private fun clockAt(iso: String) = Clock.fixed(Instant.parse(iso), ZoneOffset.UTC)
    private fun series(id: String, start: String, end: String, weekdays: List<String> = listOf("tue", "thu")) =
        HostingTestData.series(id, HostingTestData.doc(weekdays = weekdays, start = start, end = end))

    // ---- seats and dates ----

    @Test fun seatsText() {
        assertEquals("2 of 3 seats open", HostingText.seatsOpen(res, 2, 3))
        assertEquals("0 of 3 seats open", HostingText.seatsOpen(res, 0, 3))
        assertEquals("1 of 1 seat open", HostingText.seatsOpen(res, 1, 1))
        assertEquals("Tue 2 of 3 seats open · Thu 3 of 3 seats open", HostingText.seatsLine(res, HostingTestData.series("s1")))
    }

    @Test fun weekdayListAndPrice() {
        assertEquals("Tue, Thu", HostingText.weekdays(res, listOf("tue", "thu")))
        assertEquals("$4 per round trip", HostingText.price(res, 4))
    }

    @Test fun dateRangeOmitsTheCurrentYear() {
        assertEquals("Aug 24 – Dec 11", HostingText.dateRange("2026-08-24", "2026-12-11", 2026))
        assertEquals("Dec 15 – Jan 29, 2027", HostingText.dateRange("2026-12-15", "2027-01-29", 2026))
        assertEquals("Aug 24, 2027 – Dec 11, 2027", HostingText.dateRange("2027-08-24", "2027-12-11", 2026))
    }

    // ---- the past-end-date filter, in Pacific ----

    @Test fun endedSeriesAreHiddenAndTodayStillShows() {
        val list = listOf(series("past", "2026-08-01", "2026-11-23"), series("today", "2026-08-01", "2026-11-24"), series("future", "2026-08-01", "2026-12-11"))
        val shown = HostingList.visible(list, HostingList.today(clockAt("2026-11-24T20:00:00Z")))
        assertEquals(listOf("today", "future"), shown.map { it.id })
    }

    @Test fun pacificDateAcrossTheFallBackDaylightSaving() {
        // DST ends Sunday 2026-11-01 at 2:00 AM PDT. 06:30Z on Nov 2 is 10:30 PM PST on Nov 1, still Nov 1 in Pacific.
        val clock = clockAt("2026-11-02T06:30:00Z")
        assertEquals("2026-11-01", HostingList.today(clock).toString())
        assertEquals(listOf("lastDay"), HostingList.forDisplay(listOf(series("lastDay", "2026-08-24", "2026-11-01"), series("gone", "2026-08-24", "2026-10-31")), clock).map { it.id })
    }

    @Test fun pacificDateAcrossTheSpringForwardDaylightSaving() {
        // DST starts Sunday 2026-03-08. 07:59Z on Mar 8 is 11:59 PM PST on Mar 7; 08:00Z is 1:00 AM PST... still Mar 8 at 10:00Z (3:00 AM PDT).
        assertEquals("2026-03-07", HostingList.today(clockAt("2026-03-08T07:59:00Z")).toString())
        assertEquals("2026-03-08", HostingList.today(clockAt("2026-03-08T10:00:00Z")).toString())
        assertEquals(listOf("sat"), HostingList.forDisplay(listOf(series("sat", "2026-01-12", "2026-03-07")), clockAt("2026-03-08T07:59:00Z")).map { it.id })
        assertEquals(emptyList<String>(), HostingList.forDisplay(listOf(series("sat", "2026-01-12", "2026-03-07")), clockAt("2026-03-08T10:00:00Z")).map { it.id })
    }

    // ---- ordering ----

    @Test fun soonestStartFirstThenFirstWeekday() {
        val list = listOf(
            series("late", "2026-12-08", "2027-01-28"),
            series("thuFirst", "2026-12-01", "2027-01-28", listOf("thu")),
            series("monFirst", "2026-12-01", "2027-01-28", listOf("mon", "wed")),
            series("early", "2026-11-30", "2027-01-28", listOf("fri")),
        )
        assertEquals(listOf("early", "monFirst", "thuFirst", "late"), HostingList.ordered(list).map { it.id })
    }
}
