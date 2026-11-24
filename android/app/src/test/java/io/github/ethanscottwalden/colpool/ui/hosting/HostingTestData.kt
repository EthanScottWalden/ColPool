package io.github.ethanscottwalden.colpool.ui.hosting

import com.google.firebase.Timestamp
import io.github.ethanscottwalden.colpool.data.series.HostedSeries
import io.github.ethanscottwalden.colpool.data.series.HostedSeriesParser
import io.github.ethanscottwalden.colpool.ui.series.HopSpec
import io.github.ethanscottwalden.colpool.ui.series.ReviewTestData
import io.github.ethanscottwalden.colpool.ui.series.SeriesTestData

/** Made-up Riverside-area data shaped like a `series` document (data-model 2.10). */
object HostingTestData {
    private fun storedLeg(pair: Pair<Map<String, Any>, Map<String, Any>>): Map<String, Any> = pair.first + ("route" to pair.second)

    private fun withStop() = storedLeg(ReviewTestData.leg(450, "yeager-center", listOf(ReviewTestData.STARBUCKS to 5), listOf(HopSpec(16093.44, 1830.0), HopSpec(15932.0, 1110.0)))) to
        storedLeg(ReviewTestData.leg(930, "lancer-plaza", listOf(ReviewTestData.CHEVRON to 3), listOf(HopSpec(8000.0, 689.5), HopSpec(7200.0, 630.0))))

    private fun noStop() = storedLeg(ReviewTestData.leg(450, "yeager-center", emptyList(), listOf(HopSpec(32000.0, 3000.0)))) to
        storedLeg(ReviewTestData.leg(930, "lancer-plaza", emptyList(), listOf(HopSpec(16000.0, 1320.0))))

    fun doc(
        weekdays: List<String> = listOf("tue", "thu"),
        start: String = "2026-12-01",
        end: String = "2027-01-28",
        pickup: String = ReviewTestData.PICKUP,
        price: Int = 4,
        max: Int = 3,
        open: Map<String, Int> = weekdays.associateWith { if (it == "tue") 2 else max },
        vehicle: String = "Blue 2017 Honda Fit",
    ): Map<String, Any?> = mapOf(
        "hostId" to "host1",
        "status" to "active",
        "inputHash" to "hash",
        "postedAt" to Timestamp(1_764_000_000, 0),
        "pickupSpot" to mapOf("text" to pickup, "placeId" to "p", "lat" to 33.95, "lng" to -117.39),
        "startDate" to start,
        "endDate" to end,
        "priceDollars" to price,
        "vehicle" to vehicle,
        "maxRiders" to max,
        "weekdays" to weekdays,
        "days" to weekdays.associateWith { w -> val (to, from) = if (w == "tue") withStop() else noStop(); mapOf("toCampus" to to, "fromCampus" to from) },
        "openSeats" to open,
        "openWeekdays" to open.filterValues { it > 0 }.keys.toList(),
        "lastDepartureAt" to weekdays.associateWith { Timestamp(1_769_000_000, 0) },
    )

    fun series(id: String, doc: Map<String, Any?> = doc()): HostedSeries = requireNotNull(HostedSeriesParser.parse(id, doc))

    val CAMPUS = SeriesTestData.CAMPUS_POINTS.associate { it.id to it.name }

    fun state(
        series: List<HostedSeries> = listOf(series("s1")),
        load: HostingLoad = HostingLoad.Loaded,
        selectedId: String? = null,
        activeDay: io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday? = null,
    ) = HostingUiState(load = load, series = series, campusNames = CAMPUS, selectedId = selectedId, activeDay = activeDay, currentYear = 2026)
}
