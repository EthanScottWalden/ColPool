package io.github.ethanscottwalden.colpool.ui.notifications

import io.github.ethanscottwalden.colpool.data.notifications.AppNotification
import java.time.Instant
import java.time.ZoneId

/** Pure notification rules: time text, the badge and what to mark read. */
object NotificationRules {
    val PACIFIC: ZoneId = ZoneId.of("America/Los_Angeles")

    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /**
     * "Today, 9:05 AM", "Yesterday, 6:40 PM", "Oct 28, 2:10 PM", and "Oct 28, 2025, 2:10 PM" for another year.
     * Pacific time, 12-hour, built by hand so the result never depends on the device locale or JDK. Display only:
     * "today" comes from the device clock ([nowMillis]). Null when the time is unknown.
     */
    fun timeText(createdAtMillis: Long?, nowMillis: Long, zone: ZoneId = PACIFIC): String? {
        if (createdAtMillis == null) return null
        val at = Instant.ofEpochMilli(createdAtMillis).atZone(zone)
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val date = at.toLocalDate()
        val h12 = if (at.hour % 12 == 0) 12 else at.hour % 12
        val clock = "$h12:${at.minute.toString().padStart(2, '0')} ${if (at.hour < 12) "AM" else "PM"}"
        return when {
            date == today -> "Today, $clock"
            date == today.minusDays(1) -> "Yesterday, $clock"
            date.year == today.year -> "${MONTHS[date.monthValue - 1]} ${date.dayOfMonth}, $clock"
            else -> "${MONTHS[date.monthValue - 1]} ${date.dayOfMonth}, ${date.year}, $clock"
        }
    }

    /** Unread notifications not yet cleared by opening the tab. */
    fun badgeCount(items: List<AppNotification>, cleared: Set<String>): Int = items.count { !it.read && it.id !in cleared }

    /** The badge text: hidden at 0, "9+" above 9. */
    fun badgeText(count: Int): String? = when {
        count <= 0 -> null
        count > 9 -> "9+"
        else -> count.toString()
    }

    /** Ids to mark read: unread ones only, never twice while a write is out. */
    fun idsToMarkRead(items: List<AppNotification>, alreadySent: Set<String>): List<String> =
        items.filter { !it.read && it.id !in alreadySent }.map { it.id }

    /** Newest first; a missing time sorts last. */
    fun newestFirst(items: List<AppNotification>): List<AppNotification> = items.sortedByDescending { it.createdAtMillis ?: Long.MIN_VALUE }
}
