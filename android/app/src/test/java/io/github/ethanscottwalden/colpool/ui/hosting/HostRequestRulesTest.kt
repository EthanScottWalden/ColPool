package io.github.ethanscottwalden.colpool.ui.hosting

import com.google.firebase.Timestamp
import io.github.ethanscottwalden.colpool.data.requests.IncomingRequestParser
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday
import io.github.ethanscottwalden.colpool.ui.hosting.HostRequestsTestData.request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostRequestRulesTest {
    @Test fun parsesAFullDocument() {
        val r = HostRequestsTestData.parsed(HostRequestsTestData.doc(days = mapOf("thu" to mapOf("status" to "accepted"), "tue" to mapOf("status" to "pending"))))
        assertEquals("s1", r.seriesId)
        assertEquals("r1", r.riderId)
        assertEquals(1_764_000_000_000L, r.createdAtMillis)
        assertEquals(setOf("tue" to "pending", "thu" to "accepted"), r.days.map { it.weekdayId to it.status }.toSet())
    }

    @Test fun ignoresExtraFieldsAndKeepsUnknownStatus() {
        val d = HostRequestsTestData.doc(days = mapOf("tue" to mapOf("status" to "on-hold", "note" to "x"))) + ("surprise" to 5)
        assertEquals("on-hold", HostRequestsTestData.parsed(d).days.single().status)
    }

    @Test fun missingSeriesOrRiderIsSkipped() {
        assertNull(IncomingRequestParser.parse("x", HostRequestsTestData.doc() - "seriesId"))
        assertNull(IncomingRequestParser.parse("x", HostRequestsTestData.doc() - "riderId"))
        assertNull(IncomingRequestParser.parse("x", HostRequestsTestData.doc(riderId = " ")))
    }

    @Test fun oddDaysAreSkippedAndMissingCreatedSortsLast() {
        val r = HostRequestsTestData.parsed(HostRequestsTestData.doc(days = mapOf("tue" to "oops", "wed" to mapOf("status" to 3), "thu" to mapOf("status" to "pending")), created = "yesterday"))
        assertEquals(listOf("thu"), r.days.map { it.weekdayId })
        assertEquals(Long.MAX_VALUE, r.createdAtMillis)
        assertTrue(HostRequestsTestData.parsed(HostRequestsTestData.doc() - "days").days.isEmpty())
        assertEquals(1_000_000L, HostRequestsTestData.parsed(HostRequestsTestData.doc(created = Timestamp(1_000, 0))).createdAtMillis)
    }

    @Test fun statusValues() {
        assertEquals(DayStatus.Pending, HostRequestRules.status("pending"))
        assertEquals(DayStatus.Accepted, HostRequestRules.status("accepted"))
        assertEquals(DayStatus.Declined, HostRequestRules.status("declined"))
        assertEquals(DayStatus.Other("on-hold"), HostRequestRules.status("on-hold"))
        assertEquals(DayStatus.Other("?"), HostRequestRules.status("  "))
        assertEquals(24, (HostRequestRules.status("x".repeat(80)) as DayStatus.Other).raw.length)
    }

    @Test fun listsOnlyRequestsWithAPendingDayOldestFirst() {
        val rs = listOf(
            request("late", riderId = "c", created = 3_000, days = arrayOf("mon" to "pending")),
            request("done", riderId = "d", created = 500, days = arrayOf("tue" to "accepted", "thu" to "declined")),
            request("early", riderId = "a", created = 1_000, days = arrayOf("tue" to "accepted", "thu" to "pending")),
            request("other", seriesId = "s2", riderId = "e", created = 100, days = arrayOf("mon" to "pending")),
        )
        assertEquals(listOf("s1_a", "s1_c"), HostRequestRules.listed(rs, "s1").map { it.id })
        assertEquals(3, HostRequestRules.listed(rs).size)
    }

    @Test fun equalTimesOrderById() {
        val rs = listOf(request("z", riderId = "z", days = arrayOf("mon" to "pending")), request("a", riderId = "a", days = arrayOf("mon" to "pending")))
        assertEquals(listOf("s1_a", "s1_z"), HostRequestRules.listed(rs, "s1").map { it.id })
    }

    @Test fun badgeCountsPendingRequestsOnActiveSeriesOnly() {
        val rs = HostRequestsTestData.MIXED + listOf(
            request("n", seriesId = "gone", riderId = "n", days = arrayOf("mon" to "pending")),
            request("m", seriesId = "s2", riderId = "m", days = arrayOf("mon" to "declined")),
        )
        assertEquals(2, HostRequestRules.pendingCount(rs, setOf("s1", "s2")))
        assertEquals(0, HostRequestRules.pendingCount(rs, emptySet()))
    }

    @Test fun daysAreOrderedMondayFirstAndUnknownWeekdaysDropped() {
        val r = request("a", days = arrayOf("fri" to "pending", "sun" to "pending", "tue" to "accepted"))
        assertEquals(listOf(SeriesWeekday.Tue, SeriesWeekday.Fri), HostRequestRules.orderedDays(r).map { it.first })
    }

    @Test fun badgeIgnoresRequestsForSeriesNotInTheActiveList() {
        val rs = listOf(request("x", seriesId = "ended", riderId = "x", days = arrayOf("mon" to "pending")), request("y", seriesId = "other", riderId = "y", days = arrayOf("tue" to "pending")))
        assertEquals(0, HostRequestRules.pendingCount(rs, setOf("s1")))
        assertEquals(1, HostRequestRules.pendingCount(rs, setOf("s1", "other")))
    }
}
