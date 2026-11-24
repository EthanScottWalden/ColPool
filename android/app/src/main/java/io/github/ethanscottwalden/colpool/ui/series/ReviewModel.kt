package io.github.ethanscottwalden.colpool.ui.series

import io.github.ethanscottwalden.colpool.data.series.DraftLeg
import java.util.Locale

/** Formatting for Review. Times are 12-hour Pacific (RC-18); distances are miles with one decimal. */
object ReviewFormat {
    private const val METERS_PER_MILE = 1609.344
    private const val DAY_MINUTES = 24 * 60

    /** `7:30 AM`. A time on the next day (1440 or more) reads as its clock time plus [nextDay], e.g. `12:10 AM (next day)`. */
    fun time(minutes: Int, nextDay: String): String {
        val clock = SeriesTime.format12(minutes % DAY_MINUTES)
        return if (minutes >= DAY_MINUTES) "$clock $nextDay" else clock
    }

    /** `19.9`; the caller adds the unit. */
    fun miles(meters: Double): String = String.format(Locale.US, "%.1f", meters / METERS_PER_MILE)

    /** A hop's miles as whole tenths, rounded once: the value shown, so totals can sum what the rider sees. */
    fun milesTenths(meters: Double): Int = Math.round(meters / METERS_PER_MILE * 10.0).toInt()

    /** `19.9` from 199 tenths; the caller adds the unit. */
    fun tenths(tenths: Int): String = String.format(Locale.US, "%d.%d", tenths / 10, tenths % 10)

    /** One hop's minutes: `round(durationSec / 60)` per hop, a half minute rounds up (data-model 2.11). */
    fun hopMinutes(durationSec: Double): Int = Math.round(durationSec / 60.0).toInt()
}

enum class ReviewRole { Start, Stop, End }

/** One point on a timeline. [time] is the time shown beside it: leave for the start, arrive for a stop or the end. */
data class ReviewPoint(
    val role: ReviewRole,
    val name: String,
    val time: Int,
    /** Stops only. */
    val stopMinutes: Int = 0,
    val leaveMin: Int? = null,
)

/** The leg between a point and the next one. */
data class ReviewHopRow(val minutes: Int, val milesTenths: Int)

/**
 * One direction's card. [totalMinutes] is the sum of the displayed hop minutes plus the stop minutes, never a
 * recomputation from total seconds; [totalMilesTenths] is the sum of the displayed one-decimal hop miles (tenths), so the card total equals its hops.
 */
data class ReviewCard(
    val points: List<ReviewPoint>,
    val hops: List<ReviewHopRow>,
    val totalMinutes: Int,
    val totalMilesTenths: Int,
)

object ReviewCards {
    /**
     * Builds the card for one direction from the stored leg. [startName] and [endName] are the pickup spot and the
     * campus point (names, in the order the direction travels). Times come from the stored `times`, never recomputed.
     */
    fun build(leg: DraftLeg, startName: String, endName: String): ReviewCard {
        val hops = leg.hops.map { ReviewHopRow(ReviewFormat.hopMinutes(it.durationSec), ReviewFormat.milesTenths(it.meters)) }
        val last = leg.times.lastIndex
        val points = leg.times.mapIndexed { i, t ->
            when (i) {
                0 -> ReviewPoint(ReviewRole.Start, startName, t.departMin ?: 0)
                last -> ReviewPoint(ReviewRole.End, endName, t.arriveMin ?: 0)
                else -> ReviewPoint(ReviewRole.Stop, leg.stops[i - 1].name, t.arriveMin ?: 0, leg.stops[i - 1].minutes, t.departMin)
            }
        }
        return ReviewCard(points, hops, hops.sumOf { it.minutes } + leg.stops.sumOf { it.minutes }, hops.sumOf { it.milesTenths })
    }
}
