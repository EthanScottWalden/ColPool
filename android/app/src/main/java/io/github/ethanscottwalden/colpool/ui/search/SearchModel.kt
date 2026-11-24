package io.github.ethanscottwalden.colpool.ui.search

import android.content.res.Resources
import io.github.ethanscottwalden.colpool.R
import io.github.ethanscottwalden.colpool.data.search.GeoPoint
import io.github.ethanscottwalden.colpool.data.series.HostedSeries
import io.github.ethanscottwalden.colpool.ui.series.ReviewFormat
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday
import java.time.Instant
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** How one weekday of a series looks to this rider. */
enum class DayState { Open, Full, NoTripsLeft, NotNeeded }

/** One series that qualifies, with what its card shows. [distanceTenths] is null when the rider has no starting coordinates. */
data class SearchResult(
    val series: HostedSeries,
    val distanceTenths: Int?,
    /** One entry per weekday of the series, Monday first. */
    val days: List<Pair<SeriesWeekday, DayState>>,
    /** The first picked weekday that is open: its times are shown. */
    val firstOpen: SeriesWeekday,
) {
    override fun toString(): String = "SearchResult(redacted)"
}

/** The times of one weekday, minutes after local midnight. */
data class DayTimes(val leave: Int, val atCampus: Int, val leaveCampus: Int)

/** Search rules (US-09, RC-19, RC-33). Pure, so JVM tests can check them. The backend enforces trips left on the server clock. */
object SearchRules {
    private const val EARTH_RADIUS_MILES = 3958.7613

    /** Straight-line (great-circle) miles between two points. */
    fun haversineMiles(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
        val p1 = Math.toRadians(aLat)
        val p2 = Math.toRadians(bLat)
        val dLat = p2 - p1
        val dLng = Math.toRadians(bLng - aLng)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(p1) * cos(p2) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * EARTH_RADIUS_MILES * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Miles in whole tenths, rounded once: the value shown and the value ranked. */
    fun tenths(miles: Double): Int = (miles * 10.0).roundToInt()

    /** A trip is left on [weekdayId] when its last departure is after [now]. An absent key means none (data-model 2.10). */
    fun hasTripsLeft(s: HostedSeries, weekdayId: String, now: Instant): Boolean = s.lastDepartureAt[weekdayId]?.isAfter(now) == true

    /** No trips left is checked before seats: a day with no trips left is over whatever its seats say. */
    fun dayState(s: HostedSeries, w: SeriesWeekday, picked: Set<SeriesWeekday>, now: Instant): DayState = when {
        w !in picked -> DayState.NotNeeded
        !hasTripsLeft(s, w.id, now) -> DayState.NoTripsLeft
        (s.openSeats[w.id] ?: 0) <= 0 -> DayState.Full
        else -> DayState.Open
    }

    /** Qualifies when at least one picked weekday has an open seat and trips left. */
    fun qualifies(s: HostedSeries, picked: Set<SeriesWeekday>, now: Instant): Boolean =
        SeriesWeekday.entries.any { it.id in s.weekdayIds && dayState(s, it, picked, now) == DayState.Open }

    /**
     * The qualifying series, nearest first when [start] is known (ties: start date, then id). Without [start] there is no
     * ranking: start date, then id. The rider's own series and the ones already requested are left out.
     */
    fun rank(
        series: List<HostedSeries>,
        picked: Set<SeriesWeekday>,
        now: Instant,
        me: String?,
        requested: Set<String>,
        start: GeoPoint?,
    ): List<SearchResult> {
        val results = series.asSequence()
            .filter { it.hostId != me && it.id !in requested && qualifies(it, picked, now) }
            .map { s ->
                val days = SeriesWeekday.entries.filter { it.id in s.weekdayIds }.map { it to dayState(s, it, picked, now) }
                val d = if (start != null && s.pickupLat != null && s.pickupLng != null) tenths(haversineMiles(start.lat, start.lng, s.pickupLat, s.pickupLng)) else null
                SearchResult(s, d, days, days.first { it.second == DayState.Open }.first)
            }
        return results.sortedWith(compareBy<SearchResult>({ it.distanceTenths ?: Int.MAX_VALUE }, { it.series.startDate }, { it.series.id })).toList()
    }

    /** From the stored route: leave the pickup spot, arrive at campus, leave campus. Null when the stored legs are unusable. */
    fun times(s: HostedSeries, w: SeriesWeekday): DayTimes? {
        val day = s.days[w.id] ?: return null
        val leave = day.toCampus.times.firstOrNull()?.departMin ?: return null
        val arrive = day.toCampus.times.lastOrNull()?.arriveMin ?: return null
        val back = day.fromCampus.times.firstOrNull()?.departMin ?: return null
        return DayTimes(leave, arrive, back)
    }
}

/** User-facing text for Search (POLICY §11 terms). Wording is provisional. */
object SearchText {
    fun distance(res: Resources, tenths: Int): String = res.getString(R.string.search_distance, ReviewFormat.tenths(tenths))

    fun count(res: Resources, n: Int, hasDistances: Boolean): String =
        res.getQuantityString(if (hasDistances) R.plurals.search_count_near else R.plurals.search_count, n, n)

    fun dayState(res: Resources, s: DayState): String = res.getString(
        when (s) {
            DayState.Open -> R.string.search_day_open
            DayState.Full -> R.string.search_day_full
            DayState.NoTripsLeft -> R.string.search_day_no_trips
            DayState.NotNeeded -> R.string.search_day_not_needed
        },
    )

    /** `Monday: leaves 7:30 AM, at campus ~7:47 AM, leaves campus 3:15 PM`. */
    fun times(res: Resources, w: SeriesWeekday, t: DayTimes): String {
        val next = res.getString(R.string.review_next_day)
        return res.getString(
            R.string.search_times, res.getString(w.fullName),
            ReviewFormat.time(t.leave, next), ReviewFormat.time(t.atCampus, next), ReviewFormat.time(t.leaveCampus, next),
        )
    }

    fun cash(res: Resources, dollars: Int): String = res.getString(R.string.search_price, dollars)

    fun host(res: Resources, firstName: String?): String =
        if (firstName == null) res.getString(R.string.search_host_unknown) else res.getString(R.string.search_hosted_by, firstName)
}
