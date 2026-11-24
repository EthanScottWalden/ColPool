package io.github.ethanscottwalden.colpool.ui.series

import androidx.annotation.StringRes
import io.github.ethanscottwalden.colpool.R
import io.github.ethanscottwalden.colpool.data.series.SeriesError

/** A reference to another string resource, used as a format argument (for example a weekday's full name). */
data class StringRef(@StringRes val id: Int)

/** Display text as a string resource plus format arguments (numbers, or [StringRef]s resolved by the screen). */
data class UiText(@StringRes val res: Int, val args: List<Any> = emptyList())

/** Maps a backend or client error to its text (strings.xml). Anything unknown becomes "Something went wrong". */
object SeriesErrorText {
    private val legPath = Regex("""^days\.(mon|tue|wed|thu|fri)\.(toCampus|fromCampus)$""")

    private fun int(e: SeriesError, key: String, default: Int): Int = (e.params[key] as? Number)?.toInt() ?: default

    private fun weekday(e: SeriesError): Any =
        SeriesWeekday.fromId(e.params["weekday"] as? String)?.let { StringRef(it.fullName) } ?: (e.params["weekday"] as? String ?: "")

    fun of(e: SeriesError): UiText = when (e.code) {
        "required" -> UiText(if (e.path == "weekdays") R.string.series_err_weekdays_required else R.string.series_err_required)
        "notInteger" -> UiText(R.string.series_err_not_integer)
        "outOfRange" -> when {
            e.path == "priceDollars" -> UiText(R.string.series_err_price_range, listOf(int(e, "min", SeriesLimits.PRICE_MIN), int(e, "max", SeriesLimits.PRICE_MAX)))
            e.path == "maxRiders" -> UiText(R.string.series_err_riders_range, listOf(int(e, "min", SeriesLimits.RIDERS_MIN), int(e, "max", SeriesLimits.RIDERS_MAX)))
            e.path.endsWith(".minutes") -> UiText(R.string.series_err_stop_minutes_range, listOf(int(e, "min", SeriesLimits.STOP_MINUTES_MIN), int(e, "max", SeriesLimits.STOP_MINUTES_MAX)))
            else -> UiText(R.string.series_err_unknown)
        }
        "tooLong" -> UiText(R.string.series_err_too_long, listOf(int(e, "limit", SeriesLimits.VEHICLE_MAX)))
        "invalidAddress" -> UiText(R.string.series_err_invalid_address)
        "invalidDate" -> UiText(R.string.series_err_invalid_date)
        "invalidTime" -> UiText(R.string.series_err_invalid_time)
        "unknownCampusPoint" -> UiText(R.string.series_err_unknown_campus_point)
        // The app's cap (10) is lower than the backend's until #143; the text states what the app allows.
        "tooManyStops" -> UiText(R.string.series_err_too_many_stops, listOf(SeriesLimits.MAX_STOPS_PER_DIRECTION))
        "duplicateWeekday" -> UiText(R.string.series_err_duplicate_weekday, listOf(weekday(e)))
        "startInPast" -> UiText(R.string.series_err_start_in_past)
        "endBeforeStart" -> UiText(R.string.series_err_end_before_start)
        "noDateOnWeekdays" -> UiText(R.string.series_err_no_date_on_weekdays)
        "campusDepartureBeforeArrival" -> UiText(R.string.series_err_campus_departure_before_arrival, listOf(weekday(e)))
        "hostingLimitReached" -> UiText(R.string.series_err_hosting_limit, listOf(int(e, "limit", SeriesLimits.MAX_ACTIVE_HOSTED)))
        "hostingWeekdayOverlap" -> UiText(R.string.series_err_hosting_overlap, listOf(weekday(e)))
        "riderScheduleConflict" -> UiText(R.string.series_err_rider_conflict, listOf(weekday(e)))
        "routeNotFound" -> {
            val m = legPath.matchEntire(e.path)
            if (m == null) UiText(R.string.series_err_route_not_found_address)
            else {
                val dir = if (m.groupValues[2] == "toCampus") R.string.series_direction_to_campus_phrase else R.string.series_direction_from_campus_phrase
                UiText(R.string.series_err_route_not_found_leg, listOf(StringRef(SeriesWeekday.fromId(m.groupValues[1])!!.fullName), StringRef(dir)))
            }
        }
        "unavailable" -> UiText(R.string.series_err_unavailable)
        // Client-side: Review found the series changed after it was reviewed (postSeries `draft-changed`).
        "draftChanged" -> UiText(R.string.series_err_draft_changed)
        else -> UiText(R.string.series_err_unknown)
    }

    private val placed = Regex(
        """^(pickupSpot|startDate|endDate|priceDollars|maxRiders|vehicle|weekdays|""" +
            """days\.(mon|tue|wed|thu|fri)(\.(toCampus|fromCampus)(\.(departTime|campusPointId|stops(\.\d+\.(address|minutes))?))?)?)$""",
    )

    /** True when the form has a place to show an error at [path]. Errors without one go in the summary box. */
    fun hasInlineSpot(path: String): Boolean = placed.matches(path)

    /** The weekday a path belongs to (`days.tue...`), if any. */
    fun weekdayOf(path: String): SeriesWeekday? = Regex("""^days\.(mon|tue|wed|thu|fri)""").find(path)?.let { SeriesWeekday.fromId(it.groupValues[1]) }
}
