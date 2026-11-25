package io.github.ethanscottwalden.colpool.ui.hosting

import android.content.res.Resources
import androidx.annotation.StringRes
import io.github.ethanscottwalden.colpool.R
import io.github.ethanscottwalden.colpool.data.requests.IncomingRequest
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday

/** One status a request day can have. [Other] keeps whatever the server sent. */
sealed interface DayStatus {
    data object Pending : DayStatus
    data object Accepted : DayStatus
    data object Declined : DayStatus
    data class Other(val raw: String) : DayStatus
}

/** Which requests the host sees and how they read (US-07, US-14). Pure, so JVM tests can check it. */
object HostRequestRules {
    const val PENDING = "pending"
    private const val MAX_RAW = 24

    fun status(raw: String): DayStatus = when (raw) {
        PENDING -> DayStatus.Pending
        "accepted" -> DayStatus.Accepted
        "declined" -> DayStatus.Declined
        else -> DayStatus.Other(raw.trim().take(MAX_RAW).ifEmpty { "?" })
    }

    fun hasPending(r: IncomingRequest): Boolean = r.days.any { it.status == PENDING }

    /** Requests with at least one pending day, oldest first (then id, so the order never flickers). */
    fun listed(requests: List<IncomingRequest>, seriesId: String? = null): List<IncomingRequest> =
        requests.filter { (seriesId == null || it.seriesId == seriesId) && hasPending(it) }
            .sortedWith(compareBy({ it.createdAtMillis }, { it.id }))

    /** Requests with a pending day across [activeSeriesIds] (the Hosting tab badge). */
    fun pendingCount(requests: List<IncomingRequest>, activeSeriesIds: Set<String>): Int =
        requests.count { it.seriesId in activeSeriesIds && hasPending(it) }

    /** A request's days in Mon..Fri order; days that are not a weekday of the app are skipped. */
    fun orderedDays(r: IncomingRequest): List<Pair<SeriesWeekday, DayStatus>> =
        r.days.mapNotNull { d -> SeriesWeekday.fromId(d.weekdayId)?.let { it to status(d.status) } }.sortedBy { it.first.ordinal }

    @StringRes
    private fun label(s: DayStatus): Int? = when (s) {
        DayStatus.Pending -> R.string.requests_status_pending
        DayStatus.Accepted -> R.string.requests_status_accepted
        DayStatus.Declined -> R.string.requests_status_declined
        is DayStatus.Other -> null
    }

    /** `Tue · pending`. */
    fun dayText(res: Resources, w: SeriesWeekday, s: DayStatus): String =
        res.getString(R.string.requests_day_status, res.getString(w.shortName), label(s)?.let { res.getString(it) } ?: (s as DayStatus.Other).raw)

    /** `Tue · pending, Thu · accepted`. */
    fun rowDays(res: Resources, r: IncomingRequest): String = orderedDays(r).joinToString(", ") { (w, s) -> dayText(res, w, s) }
}
