package io.github.ethanscottwalden.colpool.ui.notifications

import io.github.ethanscottwalden.colpool.data.notifications.AppNotification
import io.github.ethanscottwalden.colpool.data.notifications.NotificationsBackend
import io.github.ethanscottwalden.colpool.data.notifications.NotificationsUpdate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

class FakeNotificationsBackend : NotificationsBackend {
    /** No replay: a listener only sees what is emitted while it listens, like a fresh snapshot listener would after its first result. */
    val updates = MutableSharedFlow<NotificationsUpdate>(extraBufferCapacity = 16)
    var listeners = 0
    var observeCalls = 0
    val markedRead = mutableListOf<List<String>>()
    var markError: Exception? = null

    override fun observe(): Flow<NotificationsUpdate> {
        observeCalls++
        return updates.onStart { listeners++ }.onCompletion { listeners-- }
    }

    override suspend fun markRead(ids: List<String>) {
        markError?.let { throw it }
        markedRead += ids
    }

    fun load(vararg n: AppNotification) = updates.tryEmit(NotificationsUpdate.Loaded(n.toList()))
    fun fail() = updates.tryEmit(NotificationsUpdate.Failed)
}

/** Made-up notifications. Text is written the way the server renders it (ADR 0005), with invented names. */
object NotificationsTestData {
    val PACIFIC: ZoneId = NotificationRules.PACIFIC

    /** Epoch millis of a Pacific wall-clock time. */
    fun pacific(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0): Long = ZonedDateTime.of(y, mo, d, h, mi, s, 0, PACIFIC).toInstant().toEpochMilli()

    fun note(
        id: String,
        at: Long = 1_000,
        read: Boolean = false,
        type: String = "request_received",
        title: String = "New ride request",
        body: String = "Eli Morgan wants to join your series on Thursday.",
        line: String = "Your series from Mission Grove Plaza, Ralphs lot",
    ) = AppNotification(id, type, title, body, line, at, read)

    val MIXED = listOf(
        note("a", pacific(2026, 11, 24, 9, 5), title = "New ride request", body = "Eli Morgan wants to join your series on Thursday."),
        note("b", pacific(2026, 11, 23, 18, 40), title = "New ride request", body = "Liam Carter wants to join your series on Tuesday and Thursday."),
        note("c", pacific(2026, 10, 28, 14, 10), read = true, type = "day_declined", title = "Day declined", body = "Marcus Lee declined your request for Thursday.", line = "Marcus Lee’s series from Moreno Valley Mall, north lot"),
        note("d", pacific(2026, 10, 9, 18, 0), read = true, type = "series_ended", title = "Series ended", body = "Isaac Moreno’s series reached its end date on Oct 9.", line = "Isaac Moreno’s series from Gas station, Arlington Ave & Van Buren Blvd"),
        note("e", pacific(2026, 8, 31, 19, 12), read = true, type = "day_accepted", title = "Day accepted", body = "Priya Nair accepted your request for Wednesday.", line = "Priya Nair’s series from Riverside Plaza, Target lot"),
    )
    val NOW = pacific(2026, 11, 24, 12, 0)
}
