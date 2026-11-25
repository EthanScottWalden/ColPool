package io.github.ethanscottwalden.colpool.ui.series

import io.github.ethanscottwalden.colpool.data.places.SelectedAddress
import io.github.ethanscottwalden.colpool.data.series.CampusPoint
import io.github.ethanscottwalden.colpool.data.series.DraftLoadResult
import io.github.ethanscottwalden.colpool.data.series.PostSeriesResult
import io.github.ethanscottwalden.colpool.data.series.RoutePreviewResult
import io.github.ethanscottwalden.colpool.data.series.SeriesBackend
import io.github.ethanscottwalden.colpool.ui.profile.OptionsStatus
import java.time.LocalDate

/** Fake backend. */
class FakeSeriesBackend : SeriesBackend {
    var campusError: Exception? = null
    var result: RoutePreviewResult = RoutePreviewResult.Success("draft1234567890123456", "hash")
    val calls = mutableListOf<Map<String, Any>>()
    val draftIdsSent = mutableListOf<String?>()
    /** Answers used first, in order; then [result]. */
    val queue = ArrayDeque<RoutePreviewResult>()

    override suspend fun loadCampusPoints(): List<CampusPoint> {
        campusError?.let { throw it }
        return SeriesTestData.CAMPUS_POINTS
    }

    var draftResult: DraftLoadResult = DraftLoadResult.Loaded(ReviewTestData.draft())
    var postResult: PostSeriesResult = PostSeriesResult.Posted("draft1234567890123456", false)
    val draftLoads = mutableListOf<String>()
    val posts = mutableListOf<Pair<String, Map<String, Any>>>()
    /** When set, [postSeries] suspends until completed (to test repeat taps). */
    var postGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    /** Updates the fake listener emits; replay keeps the latest for a late collector. */
    val hosted = kotlinx.coroutines.flow.MutableSharedFlow<io.github.ethanscottwalden.colpool.data.series.HostedSeriesUpdate>(replay = 1)

    override fun observeHostedSeries() = hosted

    override suspend fun loadDraft(draftId: String): DraftLoadResult {
        draftLoads += draftId
        return draftResult
    }

    override suspend fun postSeries(draftId: String, series: Map<String, Any>): PostSeriesResult {
        posts += draftId to series
        postGate?.await()
        return postResult
    }

    override suspend fun routePreview(series: Map<String, Any>, draftId: String?): RoutePreviewResult {
        calls += series
        draftIdsSent += draftId
        return queue.removeFirstOrNull() ?: result
    }
}

/** Made-up but realistic data: Riverside-area public places and the seeded campus point names. */
object SeriesTestData {
    /** Names and order from functions/src/shared/schema/campusPoints.ts. */
    val CAMPUS_POINTS = listOf(
        CampusPoint("yeager-center", "Yeager Center"),
        CampusPoint("music-building", "Music Building"),
        CampusPoint("health-science", "Health Science"),
        CampusPoint("lancer-plaza", "Lancer Plaza"),
        CampusPoint("events-center", "Events Center"),
    )

    /** Monday, a fixed Pacific "today". */
    val TODAY: LocalDate = LocalDate.of(2026, 11, 23)

    val NORCO = SelectedAddress("Norco Towne Center, Hamner Ave, Norco, CA", "place-norco", 33.9311, -117.5481)
    val STARBUCKS = SelectedAddress("Starbucks, 3750 University Ave, Riverside, CA", "place-starbucks", 33.9806, -117.3755)
    val GAS_STATION = SelectedAddress("Chevron, 1500 Magnolia Ave, Riverside, CA", "place-chevron", 33.9533, -117.3961)

    fun stop(a: SelectedAddress = STARBUCKS, minutes: Int = 5) = StopForm(a, minutes)

    /** Everything filled, Monday and Wednesday selected, Wednesday active with a stop. */
    fun filled(): CreateSeriesUiState {
        val mon = DayForm(
            toCampus = LegForm(7 * 60 + 30, "yeager-center"),
            fromCampus = LegForm(15 * 60 + 30, "lancer-plaza"),
        )
        val wed = DayForm(
            toCampus = LegForm(7 * 60 + 30, "yeager-center", listOf(stop())),
            fromCampus = LegForm(15 * 60 + 30, "lancer-plaza"),
        )
        return CreateSeriesUiState(
            campusStatus = OptionsStatus.Loaded,
            campusPoints = CAMPUS_POINTS,
            pickup = NORCO,
            startDate = "2026-11-30",
            endDate = "2026-12-11",
            price = "5",
            riders = 2,
            vehicle = "Blue 2017 Honda Fit",
            weekdays = setOf(SeriesWeekday.Mon, SeriesWeekday.Wed),
            days = mapOf(SeriesWeekday.Mon to mon, SeriesWeekday.Wed to wed),
            activeDay = SeriesWeekday.Wed,
        )
    }
}
