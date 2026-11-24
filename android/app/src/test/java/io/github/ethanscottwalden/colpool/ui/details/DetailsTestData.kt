package io.github.ethanscottwalden.colpool.ui.details

import io.github.ethanscottwalden.colpool.data.model.OptionItem
import io.github.ethanscottwalden.colpool.data.model.ProfileOptions
import io.github.ethanscottwalden.colpool.data.requests.HostProfile
import io.github.ethanscottwalden.colpool.data.requests.RequestBackend
import io.github.ethanscottwalden.colpool.data.requests.SendRequestResult
import io.github.ethanscottwalden.colpool.data.series.HostedSeries
import io.github.ethanscottwalden.colpool.ui.search.SearchTestData
import java.time.Instant

class FakeRequestBackend : RequestBackend {
    var uid: String? = "me"
    var profile: HostProfile? = DetailsTestData.AVA
    var options: ProfileOptions = DetailsTestData.OPTIONS
    var requested = false
    var result: SendRequestResult = SendRequestResult.Sent
    /** When set, [sendRequest] waits for it (to check the sending state). */
    var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    val sends = mutableListOf<Pair<String, List<String>>>()

    override fun currentUid() = uid
    override suspend fun loadHostProfile(hostId: String) = profile
    override suspend fun loadOptions() = options
    override suspend fun loadCampusNames() = mapOf("yeager-center" to "Yeager Center", "lancer-plaza" to "Lancer Plaza")
    override suspend fun hasRequested(seriesId: String) = requested
    override suspend fun sendRequest(seriesId: String, weekdayIds: List<String>): SendRequestResult {
        sends += seriesId to weekdayIds
        gate?.await()
        return result
    }
}

/** Made-up people and Riverside-area series for Series Details tests. */
object DetailsTestData {
    val OPTIONS = ProfileOptions(
        genders = listOf(OptionItem("woman", "Woman"), OptionItem("man", "Man")),
        years = listOf(OptionItem("junior", "Junior")),
        majors = listOf(OptionItem("nursing", "Nursing")),
        interests = listOf(OptionItem("music", "Music"), OptionItem("coffee", "Coffee"), OptionItem("hiking", "Hiking")),
        traits = listOf(OptionItem("chatty", "Chatty"), OptionItem("music-friendly", "Music-friendly")),
    )

    val AVA = HostProfile(
        "Ava", "Thompson", age = 20, gender = "woman", year = "junior", major = "nursing",
        interests = listOf("music", "coffee", "hiking"), traits = listOf("chatty", "music-friendly"),
        aboutMe = "Clinicals keep me busy, so I love a fun drive in.",
    )
    val MINIMAL = HostProfile("Riley", "Chen")

    /** Mon, Wed, Fri: Monday 2 open, Wednesday full, Friday open but its last trip has passed. */
    fun mixed(): HostedSeries = SearchTestData.series(
        "mixed",
        SearchTestData.doc(
            "hostA", "Canyon Crest Towne Centre, Riverside, CA", 33.9733, -117.3396, listOf("mon", "wed", "fri"),
            open = mapOf("mon" to 2, "wed" to 0, "fri" to 1),
            lastDeparture = mapOf("mon" to SearchTestData.FAR_FUTURE, "wed" to SearchTestData.FAR_FUTURE, "fri" to Instant.parse("2026-10-30T20:00:00Z")),
        ),
    )

    /** Mon, Wed, Fri all open. */
    fun open(): HostedSeries = SearchTestData.near()
}
