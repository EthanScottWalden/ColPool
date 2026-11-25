package io.github.ethanscottwalden.colpool.ui.notifications

import io.github.ethanscottwalden.colpool.ui.notifications.NotificationsTestData.note
import io.github.ethanscottwalden.colpool.ui.notifications.NotificationsTestData.pacific
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationRulesTest {
    private fun text(created: Long?, now: Long) = NotificationRules.timeText(created, now)

    @Test fun sameDayYesterdayAndOlder() {
        val now = pacific(2026, 11, 24, 12, 0)
        assertEquals("Today, 9:05 AM", text(pacific(2026, 11, 24, 9, 5), now))
        assertEquals("Yesterday, 6:40 PM", text(pacific(2026, 11, 23, 18, 40), now))
        assertEquals("Nov 22, 2:10 PM", text(pacific(2026, 11, 22, 14, 10), now))
        assertEquals("Oct 28, 2:10 PM", text(pacific(2026, 10, 28, 14, 10), now))
    }

    @Test fun twelveHourClockEdges() {
        val now = pacific(2026, 11, 24, 23, 0)
        assertEquals("Today, 12:00 AM", text(pacific(2026, 11, 24, 0, 0), now))
        assertEquals("Today, 12:05 PM", text(pacific(2026, 11, 24, 12, 5), now))
        assertEquals("Today, 11:59 PM", text(pacific(2026, 11, 24, 23, 59), now))
        assertEquals("Today, 1:07 AM", text(pacific(2026, 11, 24, 1, 7), now))
    }

    @Test fun todayChangesAtPacificMidnightNotUtc() {
        val created = pacific(2026, 11, 3, 23, 59, 59)
        // One second before Pacific midnight: still today. At midnight: yesterday.
        assertEquals("Today, 11:59 PM", text(created, pacific(2026, 11, 3, 23, 59, 59)))
        assertEquals("Yesterday, 11:59 PM", text(created, pacific(2026, 11, 4, 0, 0, 0)))
        // 5 PM Pacific is already the next day in UTC; it must still read as today.
        assertEquals("Today, 4:30 PM", text(pacific(2026, 11, 3, 16, 30), pacific(2026, 11, 3, 17, 0)))
    }

    @Test fun daylightSavingEndsOnNov1() {
        // 2026-11-01: 2:00 AM PDT falls back to 1:00 AM PST (a 25-hour day).
        val noon = pacific(2026, 11, 1, 12, 0)
        assertEquals("Yesterday, 11:30 PM", text(pacific(2026, 10, 31, 23, 30), noon))
        val firstOneThirty = ZonedDateTime.of(2026, 11, 1, 1, 30, 0, 0, NotificationsTestData.PACIFIC).withEarlierOffsetAtOverlap().toInstant().toEpochMilli()
        val secondOneThirty = ZonedDateTime.of(2026, 11, 1, 1, 30, 0, 0, NotificationsTestData.PACIFIC).withLaterOffsetAtOverlap().toInstant().toEpochMilli()
        assertEquals(60 * 60 * 1000L, secondOneThirty - firstOneThirty)
        assertEquals("Today, 1:30 AM", text(firstOneThirty, noon))
        assertEquals("Today, 1:30 AM", text(secondOneThirty, noon))
        // The next day, more than 24 hours after the 11:30 PM one is not required: yesterday is a calendar day.
        assertEquals("Yesterday, 1:30 AM", text(secondOneThirty, pacific(2026, 11, 2, 0, 10)))
        assertEquals("Yesterday, 11:30 PM", text(pacific(2026, 11, 1, 23, 30), pacific(2026, 11, 2, 0, 10)))
        // A 23-hour spring day too (2026-03-08).
        assertEquals("Yesterday, 11:00 PM", text(pacific(2026, 3, 7, 23, 0), pacific(2026, 3, 8, 12, 0)))
        assertEquals("Mar 7, 11:00 PM", text(pacific(2026, 3, 7, 23, 0), pacific(2026, 3, 9, 12, 0)))
    }

    @Test fun yearOnlyWhenNotTheCurrentYear() {
        assertEquals("Dec 31, 2025, 3:00 PM", text(pacific(2025, 12, 31, 15, 0), pacific(2026, 1, 2, 9, 0)))
        assertEquals("Yesterday, 3:00 PM", text(pacific(2025, 12, 31, 15, 0), pacific(2026, 1, 1, 9, 0)))
        assertEquals("Jan 5, 3:00 PM", text(pacific(2026, 1, 5, 15, 0), pacific(2026, 11, 24, 9, 0)))
    }

    @Test fun unknownTimeIsNull() = assertNull(text(null, NotificationsTestData.NOW))

    @Test fun badgeTextHidesAtZeroAndCapsAtNinePlus() {
        assertNull(NotificationRules.badgeText(0))
        assertEquals("1", NotificationRules.badgeText(1))
        assertEquals("9", NotificationRules.badgeText(9))
        assertEquals("9+", NotificationRules.badgeText(10))
        assertEquals("9+", NotificationRules.badgeText(250))
    }

    @Test fun badgeCountsUnreadNotYetCleared() {
        val items = listOf(note("a"), note("b"), note("c", read = true))
        assertEquals(2, NotificationRules.badgeCount(items, emptySet()))
        assertEquals(1, NotificationRules.badgeCount(items, setOf("a")))
        assertEquals(0, NotificationRules.badgeCount(items, setOf("a", "b")))
    }

    @Test fun markReadChoosesOnlyUnreadAndNotAlreadySent() {
        val items = listOf(note("a"), note("b"), note("c", read = true))
        assertEquals(listOf("a", "b"), NotificationRules.idsToMarkRead(items, emptySet()))
        assertEquals(listOf("b"), NotificationRules.idsToMarkRead(items, setOf("a")))
    }

    @Test fun newestFirstPutsMissingTimesLast() {
        val items = listOf(note("old", at = 1), note("none").copy(createdAtMillis = null), note("new", at = 9))
        assertEquals(listOf("new", "old", "none"), NotificationRules.newestFirst(items).map { it.id })
    }
}
