package io.github.ethanscottwalden.colpool.data.series

/** One hop of a stored route (`RouteResult.legs[]`, ADR 0008). */
data class RouteHop(val meters: Double, val durationSec: Double)

/** Times at one point of a stored route, minutes after local midnight; may exceed 1439. */
data class RouteTime(val arriveMin: Int?, val departMin: Int?)

/** A stop the host entered. The address text is the host's own public place. */
data class DraftStop(val name: String, val minutes: Int)

/** One direction on one weekday: what the host entered plus the route the backend stored for it. */
data class DraftLeg(
    val campusPointId: String,
    val stops: List<DraftStop>,
    val hops: List<RouteHop>,
    val times: List<RouteTime>,
)

data class DraftDay(val toCampus: DraftLeg, val fromCampus: DraftLeg)

/**
 * A saved review draft (`seriesDrafts/{id}`, data-model 2.11) as the Review screen shows it. [input] is the stored
 * `SeriesInput` map, sent back unchanged to `postSeries` so the body hashes to the reviewed `inputHash`.
 */
data class ReviewDraft(
    val pickupName: String,
    val startDate: String,
    val endDate: String,
    val priceDollars: Int,
    val maxRiders: Int,
    val vehicle: String,
    val weekdayIds: List<String>,
    val days: Map<String, DraftDay>,
    val input: Map<String, Any>,
) {
    override fun toString(): String = "ReviewDraft(redacted)"
}

/** What reading the draft gave. */
sealed interface DraftLoadResult {
    data class Loaded(val draft: ReviewDraft) : DraftLoadResult
    data object NotFound : DraftLoadResult
    data object Failed : DraftLoadResult
}

/** What the `postSeries` callable answered (ADR 0009). */
sealed interface PostSeriesResult {
    data class Posted(val seriesId: String, val alreadyPosted: Boolean) : PostSeriesResult

    /** `failed-precondition` with reason `draft-changed`. */
    data object DraftChanged : PostSeriesResult

    /** `invalid-argument` `series-invalid` with ADR 0007 errors. */
    data class Invalid(val errors: List<SeriesError>) : PostSeriesResult

    /** `not-found` or `invalid-argument` `draft-id-invalid`. */
    data object NotFound : PostSeriesResult

    /** `unavailable`, `internal`, network or anything unknown. */
    data object Failed : PostSeriesResult
}

/** Turns a Firestore draft document into a [ReviewDraft]. Returns null when it is not a complete, consistent draft. */
object ReviewDraftParser {
    private val weekdayIds = listOf("mon", "tue", "wed", "thu", "fri")

    @Suppress("UNCHECKED_CAST")
    fun parse(doc: Map<String, Any?>): ReviewDraft? {
        val input = doc["input"] as? Map<String, Any> ?: return null
        val route = doc["route"] as? Map<*, *> ?: return null
        val ids = (input["weekdays"] as? List<*>)?.filterIsInstance<String>() ?: return null
        if (ids.isEmpty() || ids.any { it !in weekdayIds }) return null
        val inputDays = input["days"] as? Map<*, *> ?: return null
        val days = LinkedHashMap<String, DraftDay>()
        for (id in weekdayIds.filter { it in ids }) {
            val d = inputDays[id] as? Map<*, *> ?: return null
            val r = route[id] as? Map<*, *> ?: return null
            val to = leg(d["toCampus"], r["toCampus"]) ?: return null
            val from = leg(d["fromCampus"], r["fromCampus"]) ?: return null
            days[id] = DraftDay(to, from)
        }
        return ReviewDraft(
            pickupName = ((input["pickupSpot"] as? Map<*, *>)?.get("text") as? String) ?: return null,
            startDate = input["startDate"] as? String ?: return null,
            endDate = input["endDate"] as? String ?: return null,
            priceDollars = int(input["priceDollars"]) ?: return null,
            maxRiders = int(input["maxRiders"]) ?: return null,
            vehicle = input["vehicle"] as? String ?: return null,
            weekdayIds = weekdayIds.filter { it in ids },
            days = days,
            input = input,
        )
    }

    private fun int(v: Any?): Int? = (v as? Number)?.toInt()

    private fun leg(input: Any?, route: Any?): DraftLeg? {
        val i = input as? Map<*, *> ?: return null
        val r = route as? Map<*, *> ?: return null
        val campus = i["campusPointId"] as? String ?: return null
        val stops = (i["stops"] as? List<*> ?: return null).map { s ->
            val m = s as? Map<*, *> ?: return null
            DraftStop(((m["address"] as? Map<*, *>)?.get("text") as? String) ?: return null, int(m["minutes"]) ?: return null)
        }
        val hops = (r["legs"] as? List<*> ?: return null).map { h ->
            val m = h as? Map<*, *> ?: return null
            RouteHop((m["meters"] as? Number)?.toDouble() ?: return null, (m["durationSec"] as? Number)?.toDouble() ?: return null)
        }
        val times = (r["times"] as? List<*> ?: return null).map { t ->
            val m = t as? Map<*, *> ?: return null
            RouteTime(int(m["arriveMin"]), int(m["departMin"]))
        }
        // One hop between each pair of points: pickup/campus, each stop, campus/pickup.
        if (hops.size != stops.size + 1 || times.size != stops.size + 2) return null
        if (times.first().departMin == null || times.last().arriveMin == null) return null
        if (times.subList(1, times.size - 1).any { it.arriveMin == null || it.departMin == null }) return null
        return DraftLeg(campus, stops, hops, times)
    }
}
