package io.github.ethanscottwalden.colpool.ui.series

import io.github.ethanscottwalden.colpool.data.series.ReviewDraft
import io.github.ethanscottwalden.colpool.data.series.ReviewDraftParser

/** A stored leg for tests: hop meters and seconds, and the times the backend would store for them (data-model 2.11). */
data class HopSpec(val meters: Double, val seconds: Double)

/** Made-up Riverside-area data shaped like a `seriesDrafts` document. */
object ReviewTestData {
    private fun address(text: String) = mapOf("text" to text, "placeId" to "p-$text", "lat" to 33.95, "lng" to -117.39)

    /** Input leg plus stored route. Times are computed by the stored rule: per-hop rounding, stop minutes added. */
    fun leg(departMin: Int, campusId: String, stops: List<Pair<String, Int>>, hops: List<HopSpec>): Pair<Map<String, Any>, Map<String, Any>> {
        require(hops.size == stops.size + 1)
        val times = mutableListOf<Map<String, Any>>()
        var clock = departMin
        times += mapOf("departMin" to clock)
        stops.forEachIndexed { i, (_, minutes) ->
            clock += ReviewFormat.hopMinutes(hops[i].seconds)
            val arrive = clock
            clock += minutes
            times += mapOf("arriveMin" to arrive, "departMin" to clock)
        }
        clock += ReviewFormat.hopMinutes(hops.last().seconds)
        times += mapOf("arriveMin" to clock)
        val input = mapOf(
            "departTime" to SeriesTime.to24h(departMin),
            "campusPointId" to campusId,
            "stops" to stops.map { (name, minutes) -> mapOf("address" to address(name), "minutes" to minutes) },
        )
        val route = mapOf(
            "legs" to hops.map { mapOf("meters" to it.meters, "durationSec" to it.seconds) },
            "totalMeters" to hops.sumOf { it.meters },
            "totalDurationSec" to hops.sumOf { it.seconds },
            "times" to times,
        )
        return input to route
    }

    val STARBUCKS = "Starbucks, 3750 University Ave, Riverside, CA"
    val CHEVRON = "Chevron, 1500 Magnolia Ave, Riverside, CA"
    const val PICKUP = "Norco Towne Center, Hamner Ave, Norco, CA"

    /** Wednesday has one stop each way; Monday none. Matches the prototype's shape, not its numbers. */
    fun doc(
        wed: Pair<Pair<Map<String, Any>, Map<String, Any>>, Pair<Map<String, Any>, Map<String, Any>>> = oneStopEachWay(),
        mon: Pair<Pair<Map<String, Any>, Map<String, Any>>, Pair<Map<String, Any>, Map<String, Any>>> = noStops(),
    ): Map<String, Any?> = mapOf(
        "hostId" to "host1",
        "status" to "reviewed",
        "inputHash" to "hash",
        "input" to mapOf(
            "pickupSpot" to address(PICKUP),
            "startDate" to "2026-12-02",
            "endDate" to "2027-01-29",
            "priceDollars" to 5,
            "vehicle" to "Blue 2017 Honda Fit",
            "maxRiders" to 2,
            "weekdays" to listOf("mon", "wed"),
            "days" to mapOf(
                "mon" to mapOf("toCampus" to mon.first.first, "fromCampus" to mon.second.first),
                "wed" to mapOf("toCampus" to wed.first.first, "fromCampus" to wed.second.first),
            ),
        ),
        "route" to mapOf(
            "mon" to mapOf("toCampus" to mon.first.second, "fromCampus" to mon.second.second),
            "wed" to mapOf("toCampus" to wed.first.second, "fromCampus" to wed.second.second),
        ),
    )

    fun oneStopEachWay() = leg(450, "yeager-center", listOf(STARBUCKS to 5), listOf(HopSpec(16093.44, 1830.0), HopSpec(15932.0, 1110.0))) to
        leg(930, "lancer-plaza", listOf(CHEVRON to 3), listOf(HopSpec(8000.0, 689.5), HopSpec(7200.0, 630.0)))

    fun noStops() = leg(450, "yeager-center", emptyList(), listOf(HopSpec(32000.0, 3000.0))) to
        leg(930, "lancer-plaza", emptyList(), listOf(HopSpec(16000.0, 1320.0)))

    fun draft(doc: Map<String, Any?> = doc()): ReviewDraft = requireNotNull(ReviewDraftParser.parse(doc))

    fun loaded(draft: ReviewDraft = draft(), active: SeriesWeekday = SeriesWeekday.Wed) = ReviewUiState(
        load = ReviewLoad.Loaded,
        draft = draft,
        campusNames = SeriesTestData.CAMPUS_POINTS.associate { it.id to it.name },
        activeDay = active,
    )
}
