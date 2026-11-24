package io.github.ethanscottwalden.colpool.ui.hosting

import android.content.res.Resources
import io.github.ethanscottwalden.colpool.R
import io.github.ethanscottwalden.colpool.data.series.HostedSeries
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** Which series the Hosting tab shows, and in what order. Display rules only: the backend decides when a series ends (RC-17). */
object HostingList {
    val PACIFIC: ZoneId = ZoneId.of("America/Los_Angeles")

    fun today(clock: Clock): LocalDate = LocalDate.now(clock.withZone(PACIFIC))

    /** Hides a series whose last date is before [today] (Pacific). It lingers until the backend marks it ended. */
    fun visible(series: List<HostedSeries>, today: LocalDate): List<HostedSeries> =
        series.filter { runCatching { !LocalDate.parse(it.endDate).isBefore(today) }.getOrDefault(false) }

    /** Soonest start date first, then first weekday, then id so the order never flickers. */
    fun ordered(series: List<HostedSeries>): List<HostedSeries> =
        series.sortedWith(compareBy({ it.startDate }, { firstWeekday(it) }, { it.id }))

    private fun firstWeekday(s: HostedSeries): Int = s.weekdayIds.firstNotNullOfOrNull { SeriesWeekday.fromId(it)?.ordinal } ?: Int.MAX_VALUE

    fun forDisplay(series: List<HostedSeries>, clock: Clock): List<HostedSeries> = ordered(visible(series, today(clock)))
}

/** User-facing text for Hosting (POLICY §11 terms). Pure, so JVM tests can check it. */
object HostingText {
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /** `Aug 24`, or `Aug 24, 2027` when the year is not [currentYear]. */
    fun date(iso: String, currentYear: Int): String {
        val d = LocalDate.parse(iso)
        val base = "${MONTHS[d.monthValue - 1]} ${d.dayOfMonth}"
        return if (d.year == currentYear) base else "$base, ${d.year}"
    }

    /** `Aug 24 – Dec 11`. */
    fun dateRange(start: String, end: String, currentYear: Int): String = "${date(start, currentYear)} – ${date(end, currentYear)}"

    /** `Tue, Thu`. */
    fun weekdays(res: Resources, ids: List<String>): String =
        ids.mapNotNull { SeriesWeekday.fromId(it) }.joinToString(", ") { res.getString(it.shortName) }

    /** `2 of 3 seats open` ("1 of 1 seat open"). */
    fun seatsOpen(res: Resources, open: Int, max: Int): String = res.getQuantityString(R.plurals.hosting_seats_open, max, open, max)

    /** `Tue 2 of 3 seats open · Thu 3 of 3 seats open`. */
    fun seatsLine(res: Resources, s: HostedSeries): String =
        s.weekdayIds.mapNotNull { id -> SeriesWeekday.fromId(id)?.let { w -> "${res.getString(w.shortName)} ${seatsOpen(res, s.openSeats[id] ?: 0, s.maxRiders)}" } }.joinToString(" · ")

    fun price(res: Resources, dollars: Int): String = res.getString(R.string.hosting_price_per_trip, dollars)

    internal fun yearOf(clock: Clock): Int = HostingList.today(clock).year

}
