package io.github.ethanscottwalden.colpool.ui.search

import com.google.firebase.Timestamp
import io.github.ethanscottwalden.colpool.data.model.ProfileOptions
import io.github.ethanscottwalden.colpool.data.search.GeoPoint
import io.github.ethanscottwalden.colpool.data.search.HostMatchFields
import io.github.ethanscottwalden.colpool.data.search.SearchBackend
import io.github.ethanscottwalden.colpool.data.series.HostedSeries
import io.github.ethanscottwalden.colpool.data.series.HostedSeriesParser
import io.github.ethanscottwalden.colpool.ui.series.HopSpec
import io.github.ethanscottwalden.colpool.ui.series.ReviewTestData
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class FakeSearchBackend : SearchBackend {
    var uid: String? = "me"
    var series: List<HostedSeries> = emptyList()
    var start: GeoPoint? = SearchTestData.RIDER
    var requested: Set<String> = emptySet()
    var names: Map<String, String?> = emptyMap()
    /** Match fields per host (the first name still comes from [names]). */
    var matchFields: Map<String, HostMatchFields> = emptyMap()
    var options: ProfileOptions = io.github.ethanscottwalden.colpool.ui.profile.SeededOptions.ALL
    var optionsError: Exception? = null
    var optionLoads = 0
    var searchError: Exception? = null
    val searches = mutableListOf<List<String>>()
    var requestLists = 0
    val nameReads = mutableListOf<String>()

    override fun currentUid() = uid
    override suspend fun searchSeries(weekdayIds: List<String>): List<HostedSeries> {
        searches += weekdayIds
        searchError?.let { throw it }
        // Like the real query: active series with an open seat on a picked weekday.
        return series.filter { s -> s.openSeats.any { (w, n) -> w in weekdayIds && n > 0 } }
    }
    override suspend fun loadStartingPoint() = start
    override suspend fun loadRequestedSeriesIds(): Set<String> { requestLists++; return requested }
    override suspend fun loadHostFields(hostId: String): HostMatchFields? {
        nameReads += hostId
        val f = matchFields[hostId]
        val name = if (hostId in names) names[hostId] else f?.firstName
        return if (f == null && name == null) null else (f ?: HostMatchFields(name)).copy(firstName = name)
    }
    override suspend fun loadOptions(): ProfileOptions { optionLoads++; optionsError?.let { throw it }; return options }
}

/** Made-up Riverside-area data shaped like `series` documents. The clock is the Monday after the fall-back change (Nov 1, 2026). */
object SearchTestData {
    /** Mon Nov 2, 2026, 7:00 AM PST. */
    val NOW: Instant = Instant.parse("2026-11-02T15:00:00Z")
    val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
    val RIDER = GeoPoint(33.9380, -117.3560)
    val FAR_FUTURE = Instant.parse("2027-01-28T20:00:00Z")

    private fun stored(p: Pair<Map<String, Any>, Map<String, Any>>) = p.first + ("route" to p.second)

    fun doc(
        host: String,
        pickup: String,
        lat: Double?,
        lng: Double?,
        weekdays: List<String> = listOf("mon", "wed", "fri"),
        open: Map<String, Int> = weekdays.associateWith { 2 },
        lastDeparture: Map<String, Instant> = weekdays.associateWith { FAR_FUTURE },
        price: Int = 4,
        leaveMin: Int = 450,
        driveSec: Double = 1620.0,
        backMin: Int = 930,
        start: String = "2026-11-30",
        end: String = "2027-01-28",
    ): Map<String, Any?> {
        val to = stored(ReviewTestData.leg(leaveMin, "yeager-center", emptyList(), listOf(HopSpec(14000.0, driveSec))))
        val from = stored(ReviewTestData.leg(backMin, "lancer-plaza", emptyList(), listOf(HopSpec(14000.0, driveSec))))
        return mapOf(
            "hostId" to host, "status" to "active", "inputHash" to "h",
            "pickupSpot" to buildMap { put("text", pickup); put("placeId", "p"); if (lat != null) put("lat", lat); if (lng != null) put("lng", lng) },
            "startDate" to start, "endDate" to end, "priceDollars" to price, "vehicle" to "Blue 2017 Honda Fit", "maxRiders" to 3,
            "weekdays" to weekdays,
            "days" to weekdays.associateWith { mapOf("toCampus" to to, "fromCampus" to from) },
            "openSeats" to open,
            "openWeekdays" to open.filterValues { it > 0 }.keys.toList(),
            "lastDepartureAt" to lastDeparture.mapValues { Timestamp(it.value.epochSecond, 0) },
        )
    }

    fun series(id: String, doc: Map<String, Any?>): HostedSeries = requireNotNull(HostedSeriesParser.parse(id, doc))

    // Pickup points near Riverside, nearest to farthest from RIDER.
    fun near() = series("near", doc("hostA", "Kaiser Permanente Riverside, Magnolia Ave", 33.9190, -117.3700, listOf("mon", "wed", "fri"), price = 4))
    fun mid() = series("mid", doc("hostB", "Starbucks, 3750 University Ave, Riverside, CA", 33.9745, -117.3280, listOf("mon", "tue", "thu"), open = mapOf("mon" to 0, "tue" to 1, "thu" to 2), price = 3, leaveMin = 425, backMin = 810))
    fun far() = series("far", doc("hostC", "Norco Towne Center, Hamner Ave, Norco, CA", 33.9360, -117.5560, listOf("tue", "thu"), open = mapOf("tue" to 1, "thu" to 1), lastDeparture = mapOf("tue" to Instant.parse("2026-10-27T20:00:00Z"), "thu" to FAR_FUTURE), price = 5, leaveMin = 480, backMin = 1020))
    fun last() = series("last", doc("hostD", "Canyon Crest Towne Centre, Riverside, CA", 33.9733, -117.3396, listOf("mon", "wed"), price = 6, leaveMin = 465, backMin = 885, start = "2027-01-11", end = "2027-04-29"))
}
