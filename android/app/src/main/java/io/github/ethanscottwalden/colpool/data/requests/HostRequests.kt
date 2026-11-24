package io.github.ethanscottwalden.colpool.data.requests

import com.google.firebase.Timestamp
import io.github.ethanscottwalden.colpool.data.model.ProfileOptions
import kotlinx.coroutines.flow.Flow

/** One requested weekday and where it stands. [status] is `pending`, `accepted` or `declined`; anything else is kept as sent. */
data class RequestDay(val weekdayId: String, val status: String)

/**
 * One rider's request to one series (`requests/{seriesId}_{riderUid}`, ADR 0010), as the host reads it.
 * [id] is the document id; neither it nor [riderId] is ever shown.
 */
data class IncomingRequest(
    val id: String,
    val seriesId: String,
    val riderId: String,
    /** Milliseconds since the epoch, or [Long.MAX_VALUE] when `createdAt` is missing (sorts last). */
    val createdAtMillis: Long,
    val days: List<RequestDay>,
) {
    override fun toString(): String = "IncomingRequest(redacted)"
}

sealed interface RequestsUpdate {
    data class Loaded(val requests: List<IncomingRequest>) : RequestsUpdate
    data object Failed : RequestsUpdate
}

/** Firebase reads for the host's request screens. Faked in JVM tests. The client never writes a request. */
interface HostRequestsBackend {
    /** Live listener on `requests` where `hostId == uid`, and `seriesId == [seriesId]` when given, oldest first. Failures arrive as [RequestsUpdate.Failed]. */
    fun observeRequests(seriesId: String?): Flow<RequestsUpdate>

    /** The requester's public profile, or null when it can't be read (access ended). Never throws except cancellation. */
    suspend fun loadRequesterProfile(riderId: String): HostProfile?

    /** The predefined lists that turn stored ids into labels. Throws on failure. */
    suspend fun loadOptions(): ProfileOptions
}

object IncomingRequestParser {
    /** A request document, or null when it has no series id or rider id. Missing or odd days are skipped. */
    fun parse(id: String, doc: Map<String, Any?>): IncomingRequest? {
        val seriesId = (doc["seriesId"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val riderId = (doc["riderId"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val days = (doc["days"] as? Map<*, *>).orEmpty().mapNotNull { (k, v) ->
            val wd = k as? String ?: return@mapNotNull null
            val status = (v as? Map<*, *>)?.get("status") as? String ?: return@mapNotNull null
            RequestDay(wd, status)
        }
        val created = (doc["createdAt"] as? Timestamp)?.toDate()?.time ?: Long.MAX_VALUE
        return IncomingRequest(id, seriesId, riderId, created, days)
    }
}
