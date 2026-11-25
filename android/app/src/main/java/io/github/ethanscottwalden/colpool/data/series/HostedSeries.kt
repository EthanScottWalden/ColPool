package io.github.ethanscottwalden.colpool.data.series

import java.time.Instant

/**
 * A posted series as the host sees it (`series/{id}`, data-model 2.10). Routes are the stored ones (RC-12) and are never recalculated.
 * [lastDepartureAt] is kept as the stored timestamps; it is not shown.
 */
data class HostedSeries(
    val id: String,
    val pickupName: String,
    val startDate: String,
    val endDate: String,
    val priceDollars: Int,
    val maxRiders: Int,
    val vehicle: String,
    val weekdayIds: List<String>,
    val days: Map<String, DraftDay>,
    val openSeats: Map<String, Int>,
    val lastDepartureAt: Map<String, Instant>,
    /** The host's uid, for Search (a rider never sees it). */
    val hostId: String = "",
    /** The pickup spot's coordinates, for Search ranking only (RC-19). Null when the document has none. */
    val pickupLat: Double? = null,
    val pickupLng: Double? = null,
) {
    override fun toString(): String = "HostedSeries(redacted)"
}

/** What the live listener on the host's series gave. */
sealed interface HostedSeriesUpdate {
    data class Loaded(val series: List<HostedSeries>) : HostedSeriesUpdate
    data object Failed : HostedSeriesUpdate
}

/** Turns a `series` document into a [HostedSeries]. Returns null for a document that is incomplete or inconsistent; extra fields are ignored. */
object HostedSeriesParser {
    private val weekdayIds = listOf("mon", "tue", "wed", "thu", "fri")

    fun parse(id: String, doc: Map<String, Any?>): HostedSeries? {
        val ids = (doc["weekdays"] as? List<*>)?.filterIsInstance<String>() ?: return null
        if (ids.isEmpty() || ids.any { it !in weekdayIds }) return null
        val ordered = weekdayIds.filter { it in ids }
        val storedDays = doc["days"] as? Map<*, *> ?: return null
        val seats = doc["openSeats"] as? Map<*, *> ?: return null
        val maxRiders = ReviewDraftParser.int(doc["maxRiders"]) ?: return null
        if (maxRiders < 1) return null
        val days = LinkedHashMap<String, DraftDay>()
        val open = LinkedHashMap<String, Int>()
        for (w in ordered) {
            val d = storedDays[w] as? Map<*, *> ?: return null
            val to = leg(d["toCampus"]) ?: return null
            val from = leg(d["fromCampus"]) ?: return null
            days[w] = DraftDay(to, from)
            open[w] = (ReviewDraftParser.int(seats[w]) ?: return null).coerceIn(0, maxRiders)
        }
        val pickup = doc["pickupSpot"] as? Map<*, *>
        return HostedSeries(
            id = id,
            pickupName = (pickup?.get("text") as? String) ?: return null,
            startDate = doc["startDate"] as? String ?: return null,
            endDate = doc["endDate"] as? String ?: return null,
            priceDollars = ReviewDraftParser.int(doc["priceDollars"]) ?: return null,
            maxRiders = maxRiders,
            vehicle = doc["vehicle"] as? String ?: return null,
            weekdayIds = ordered,
            days = days,
            openSeats = open,
            lastDepartureAt = departures(doc["lastDepartureAt"]),
            hostId = doc["hostId"] as? String ?: "",
            pickupLat = (pickup["lat"] as? Number)?.toDouble(),
            pickupLng = (pickup["lng"] as? Number)?.toDouble(),
        )
    }

    /** A stored leg carries its route inside it: `{campusPointId, stops, route}`. */
    private fun leg(v: Any?): DraftLeg? {
        val m = v as? Map<*, *> ?: return null
        return ReviewDraftParser.leg(m, m["route"])
    }

    private fun departures(v: Any?): Map<String, Instant> {
        val m = v as? Map<*, *> ?: return emptyMap()
        return m.entries.mapNotNull { (k, t) ->
            val key = k as? String ?: return@mapNotNull null
            val ts = t as? com.google.firebase.Timestamp ?: return@mapNotNull null
            key to Instant.ofEpochSecond(ts.seconds, ts.nanoseconds.toLong())
        }.toMap()
    }
}
