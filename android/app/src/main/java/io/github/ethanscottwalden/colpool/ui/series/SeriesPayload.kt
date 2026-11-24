package io.github.ethanscottwalden.colpool.ui.series

import io.github.ethanscottwalden.colpool.data.places.SelectedAddress

/** Builds the `SeriesInput` map the `routePreview` callable takes (functions/src/shared/schema/series.ts). */
object SeriesPayload {
    fun address(a: SelectedAddress): Map<String, Any> = mapOf("text" to a.text, "placeId" to a.placeId, "lat" to a.lat, "lng" to a.lng)

    private fun leg(l: LegForm): Map<String, Any> = mapOf(
        "departTime" to SeriesTime.to24h(l.departMinutes ?: 0),
        "campusPointId" to (l.campusPointId ?: ""),
        "stops" to l.stops.map { mapOf("address" to address(it.address), "minutes" to it.minutes) },
    )

    /** Call only after [SeriesChecks] passes: the form is complete then. */
    fun build(s: CreateSeriesUiState): Map<String, Any> {
        val weekdays = s.orderedWeekdays
        return mapOf(
            "pickupSpot" to address(requireNotNull(s.pickup)),
            "startDate" to requireNotNull(s.startDate),
            "endDate" to requireNotNull(s.endDate),
            "priceDollars" to (s.price.trim().toIntOrNull() ?: 0),
            "vehicle" to s.vehicle.trim(),
            "maxRiders" to s.riders,
            "weekdays" to weekdays.map { it.id },
            // Keys are exactly the chosen weekdays (the backend rejects any other key).
            "days" to weekdays.associate { w ->
                val d = s.day(w)
                w.id to mapOf("toCampus" to leg(d.toCampus), "fromCampus" to leg(d.fromCampus))
            },
        )
    }
}
