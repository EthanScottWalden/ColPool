package io.github.ethanscottwalden.colpool.data.notifications

import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow

/**
 * One server-rendered notification (`users/{uid}/notifications/{id}`, ADR 0005).
 * The text is shown exactly as the server wrote it; the app never builds text from ids.
 * [type] is kept for tests and future use; an unknown type is fine. Contents never reach logs.
 */
data class AppNotification(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val seriesLine: String,
    /** Milliseconds since the epoch, or null when `createdAt` is missing. */
    val createdAtMillis: Long?,
    val read: Boolean,
) {
    override fun toString(): String = "AppNotification(redacted)"
}

sealed interface NotificationsUpdate {
    /** [fromCache]: the snapshot came from the local cache, not the server; it is not yet a reliable "what already existed". */
    data class Loaded(val notifications: List<AppNotification>, val fromCache: Boolean = false) : NotificationsUpdate
    data object Failed : NotificationsUpdate
}

/** Firebase access for the notifications UI. Faked in JVM tests. The client's only write is [markRead]. */
interface NotificationsBackend {
    /** Live listener on the signed-in student's notifications, newest first, at most [NOTIFICATIONS_LIMIT]. Failures arrive as [NotificationsUpdate.Failed]. */
    fun observe(): Flow<NotificationsUpdate>

    /** Sets `read` to true, and nothing else, on these notifications. Throws on failure; a failed call is retried on the next open. */
    suspend fun markRead(ids: List<String>)
}

const val NOTIFICATIONS_LIMIT = 100

object NotificationParser {
    /** A notification, or null when it has neither a title nor a body. Missing or extra fields are tolerated; a missing `read` counts as read (no badge for a malformed record). */
    fun parse(id: String, doc: Map<String, Any?>): AppNotification? {
        val title = (doc["title"] as? String)?.trim().orEmpty()
        val body = (doc["body"] as? String)?.trim().orEmpty()
        if (title.isEmpty() && body.isEmpty()) return null
        return AppNotification(
            id = id,
            type = (doc["type"] as? String).orEmpty(),
            title = title,
            body = body,
            seriesLine = (doc["seriesLine"] as? String)?.trim().orEmpty(),
            createdAtMillis = (doc["createdAt"] as? Timestamp)?.toDate()?.time,
            read = (doc["read"] as? Boolean) ?: true,
        )
    }
}
