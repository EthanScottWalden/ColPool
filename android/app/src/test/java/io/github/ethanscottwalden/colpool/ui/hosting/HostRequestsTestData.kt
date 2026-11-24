package io.github.ethanscottwalden.colpool.ui.hosting

import com.google.firebase.Timestamp
import io.github.ethanscottwalden.colpool.data.model.ProfileOptions
import io.github.ethanscottwalden.colpool.data.requests.HostProfile
import io.github.ethanscottwalden.colpool.data.requests.HostRequestsBackend
import io.github.ethanscottwalden.colpool.data.requests.IncomingRequest
import io.github.ethanscottwalden.colpool.data.requests.IncomingRequestParser
import io.github.ethanscottwalden.colpool.data.requests.RequestDay
import io.github.ethanscottwalden.colpool.data.requests.RequestsUpdate
import io.github.ethanscottwalden.colpool.ui.details.DetailsTestData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeHostRequestsBackend : HostRequestsBackend {
    /** One flow per listener kind; replay keeps the latest for a late collector. */
    val all = MutableSharedFlow<RequestsUpdate>(replay = 1)
    val bySeries = MutableSharedFlow<RequestsUpdate>(replay = 1)
    val observed = mutableListOf<String?>()
    val profiles = mutableMapOf<String, HostProfile?>()
    var options: ProfileOptions = DetailsTestData.OPTIONS
    var optionsError: Exception? = null

    override fun observeRequests(seriesId: String?): Flow<RequestsUpdate> {
        observed += seriesId
        return if (seriesId == null) all else bySeries
    }

    /** When set, profile reads wait for it (to test a read in flight). */
    var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun loadRequesterProfile(riderId: String): HostProfile? {
        gate?.await()
        return profiles[riderId]
    }
    override suspend fun loadOptions(): ProfileOptions = optionsError?.let { throw it } ?: options
}

/** Made-up riders and requests (no real students). */
object HostRequestsTestData {
    val MAYA = HostProfile("Maya", "Okafor", age = 21, year = "junior", major = "nursing", interests = listOf("music", "coffee"), aboutMe = "Early classes, quiet rides.")
    val JORDAN = HostProfile("Jordan", "Reyes")

    fun request(id: String, seriesId: String = "s1", riderId: String = "rider-$id", created: Long = 1_000, vararg days: Pair<String, String>) =
        IncomingRequest("${seriesId}_$riderId", seriesId, riderId, created, days.map { RequestDay(it.first, it.second) })

    fun doc(seriesId: String = "s1", riderId: String = "r1", days: Map<String, Any?> = mapOf("tue" to mapOf("status" to "pending")), created: Any? = Timestamp(1_764_000_000, 0)): Map<String, Any?> =
        mapOf("seriesId" to seriesId, "riderId" to riderId, "hostId" to "host1", "weekdays" to days.keys.toList(), "days" to days, "createdAt" to created)

    fun parsed(doc: Map<String, Any?>): IncomingRequest = requireNotNull(IncomingRequestParser.parse("${doc["seriesId"]}_${doc["riderId"]}", doc))

    /** Maya: Tue pending, Thu accepted. Jordan: Fri pending. */
    val MIXED = listOf(
        request("a", riderId = "maya", created = 1_000, days = arrayOf("tue" to "pending", "thu" to "accepted")),
        request("b", riderId = "jordan", created = 2_000, days = arrayOf("fri" to "pending")),
    )

    val NAMES: Map<String, HostProfile?> = mapOf("maya" to MAYA, "jordan" to JORDAN)
}
