package io.github.ethanscottwalden.colpool.ui.series

import io.github.ethanscottwalden.colpool.data.series.SeriesError
import java.time.LocalDate

/**
 * Client-side pre-checks that mirror the obvious RC-30 errors, using the backend's codes and paths so the
 * same text and placement apply. They only save a round trip: the backend stays authoritative.
 * [today] is the Pacific calendar date.
 */
object SeriesChecks {
    fun run(s: CreateSeriesUiState, today: LocalDate): List<SeriesError> {
        val out = mutableListOf<SeriesError>()
        fun add(code: String, path: String, vararg params: Pair<String, Any?>) = out.add(SeriesError(code, path, mapOf(*params)))

        if (s.pickup == null) add("required", "pickupSpot")

        val start = s.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val end = s.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (s.startDate.isNullOrBlank()) add("required", "startDate") else if (start == null) add("invalidDate", "startDate")
        if (s.endDate.isNullOrBlank()) add("required", "endDate") else if (end == null) add("invalidDate", "endDate")
        if (start != null && start.isBefore(today)) add("startInPast", "startDate")
        if (start != null && end != null && end.isBefore(start)) add("endBeforeStart", "endDate")

        val priceText = s.price.trim()
        val price = priceText.toIntOrNull()
        when {
            priceText.isEmpty() -> add("required", "priceDollars")
            price == null -> add("notInteger", "priceDollars")
            price !in SeriesLimits.PRICE_MIN..SeriesLimits.PRICE_MAX ->
                add("outOfRange", "priceDollars", "min" to SeriesLimits.PRICE_MIN, "max" to SeriesLimits.PRICE_MAX)
        }
        if (s.riders !in SeriesLimits.RIDERS_MIN..SeriesLimits.RIDERS_MAX) {
            add("outOfRange", "maxRiders", "min" to SeriesLimits.RIDERS_MIN, "max" to SeriesLimits.RIDERS_MAX)
        }

        val vehicle = s.vehicle.trim()
        if (vehicle.isEmpty()) add("required", "vehicle")
        else if (vehicle.length > SeriesLimits.VEHICLE_MAX) add("tooLong", "vehicle", "limit" to SeriesLimits.VEHICLE_MAX)

        val weekdays = s.orderedWeekdays
        if (weekdays.isEmpty()) add("required", "weekdays")

        for (w in weekdays) {
            val day = s.day(w)
            for (dir in Direction.entries) {
                val leg = day.leg(dir)
                val base = "days.${w.id}.${dir.id}"
                if (leg.departMinutes == null) add("required", "$base.departTime")
                val cp = leg.campusPointId
                if (cp == null) add("required", "$base.campusPointId")
                else if (s.campusPoints.none { it.id == cp }) add("unknownCampusPoint", "$base.campusPointId")
                if (leg.stops.size > SeriesLimits.MAX_STOPS_PER_DIRECTION) {
                    add("tooManyStops", "$base.stops", "limit" to SeriesLimits.MAX_STOPS_PER_DIRECTION)
                }
                leg.stops.forEachIndexed { i, stop ->
                    if (stop.minutes !in SeriesLimits.STOP_MINUTES_MIN..SeriesLimits.STOP_MINUTES_MAX) {
                        add("outOfRange", "$base.stops.$i.minutes", "min" to SeriesLimits.STOP_MINUTES_MIN, "max" to SeriesLimits.STOP_MINUTES_MAX)
                    }
                }
            }
        }

        // RC-12: at least one chosen weekday must have a date in the range.
        if (start != null && end != null && !end.isBefore(start) && weekdays.isNotEmpty()) {
            if (weekdays.none { hasDate(it, start, end) }) add("noDateOnWeekdays", "")
        }
        return out
    }

    private fun hasDate(w: SeriesWeekday, start: LocalDate, end: LocalDate): Boolean {
        var d = start
        // A range of seven days always contains every weekday; stop early.
        for (i in 0..6) {
            if (d.isAfter(end)) return false
            if (d.dayOfWeek == w.dayOfWeek) return true
            d = d.plusDays(1)
        }
        return false
    }
}
