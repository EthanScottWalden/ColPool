package io.github.ethanscottwalden.colpool.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ethanscottwalden.colpool.data.notifications.AppNotification
import io.github.ethanscottwalden.colpool.data.notifications.NotificationsBackend
import io.github.ethanscottwalden.colpool.data.notifications.NotificationsUpdate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsUiState(
    /** Newest first. */
    val items: List<AppNotification> = emptyList(),
    val loaded: Boolean = false,
    val failed: Boolean = false,
    /** Unread count for the tab badge (cleared the moment the tab opens). */
    val unreadCount: Int = 0,
    /** Ids shown with a "New" tag: still unread, or seen as new during the current visit to the tab. */
    val newIds: Set<String> = emptySet(),
    /** The notification the banner shows now, if any. */
    val banner: AppNotification? = null,
    val tabOpen: Boolean = false,
) {
    override fun toString(): String = "NotificationsUiState(redacted)"
}

/**
 * The student's notifications (US-15, US-16): one live listener, the history, the tab badge and the banner queue.
 * The client's only write is `read: true` on unread records (ADR 0005); it never sets unread and never deletes.
 */
class NotificationsViewModel(private val backend: NotificationsBackend) : ViewModel() {
    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    private var job: Job? = null
    private val writeJobs = mutableSetOf<Job>()

    // Ids seen so far. Null until the first snapshot: what is there at sign-in is not "new".
    private var known: Set<String>? = null
    private val queue = ArrayDeque<AppNotification>()
    // Ids already sent to be marked read (the badge treats them as read).
    private val cleared = mutableSetOf<String>()
    private val failedWrites = mutableSetOf<String>()
    private var visitNew = setOf<String>()

    /** Starts the listener (once). */
    fun start() {
        if (job?.isActive == true) return
        known = null
        job = viewModelScope.launch {
            try {
                backend.observe().collect { u ->
                    when (u) {
                        is NotificationsUpdate.Loaded -> onLoaded(u.notifications, u.fromCache)
                        NotificationsUpdate.Failed -> _state.update { it.copy(loaded = true, failed = true) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loaded = true, failed = true) }
            }
        }
    }

    /** Restarts the listener after a failure. What arrived meanwhile is not shown in the banner. */
    fun retry() {
        job?.cancel()
        job = null
        _state.update { it.copy(failed = false, loaded = false) }
        start()
    }

    private fun onLoaded(list: List<AppNotification>, fromCache: Boolean) {
        val items = NotificationRules.newestFirst(list)
        val previous = known
        // What existed is the first snapshot from the server; a cache-sourced one can be empty or old.
        if (!fromCache || previous != null) known = items.map { it.id }.toSet()
        val arrived = if (previous == null) emptyList() else items.filter { it.id !in previous && !it.read }
        val s = _state.value
        if (s.tabOpen) {
            // Seen on load or arrival (also after a retry): no banner, the tag stays for this visit, and it is marked read.
            visitNew = visitNew + items.filter { !it.read }.map { it.id }
        } else if (arrived.isNotEmpty()) {
            // Newest first; a later arrival goes in front of those still waiting.
            val merged = arrived + queue.toList()
            queue.clear()
            queue.addAll(merged)
        }
        _state.update { cur ->
            val banner = cur.banner ?: if (cur.tabOpen) null else queue.removeFirstOrNull()
            publish(cur.copy(items = items, loaded = true, failed = false, banner = banner))
        }
        if (s.tabOpen) markUnreadRead()
    }

    private fun publish(s: NotificationsUiState): NotificationsUiState = s.copy(
        unreadCount = NotificationRules.badgeCount(s.items, cleared),
        newIds = s.items.filter { !it.read || it.id in visitNew }.map { it.id }.toSet(),
    )

    /** The banner closed (its timer, its close button, or a tap). The next waiting one shows. */
    fun dismissBanner() = _state.update { cur -> publish(cur.copy(banner = if (cur.tabOpen) null else queue.removeFirstOrNull())) }

    /** The Notifications tab became visible: clear the badge and the banners, mark unread records read. */
    fun openTab() {
        if (_state.value.tabOpen) return
        queue.clear()
        visitNew = _state.value.items.filter { !it.read }.map { it.id }.toSet()
        _state.update { publish(it.copy(tabOpen = true, banner = null)) }
        markUnreadRead()
    }

    /** The tab was left: "New" tags go, and a failed write shows its unread count again. */
    fun closeTab() {
        if (!_state.value.tabOpen) return
        visitNew = emptySet()
        cleared.removeAll(failedWrites)
        failedWrites.clear()
        _state.update { publish(it.copy(tabOpen = false)) }
    }

    private fun markUnreadRead() {
        val ids = NotificationRules.idsToMarkRead(_state.value.items, cleared)
        if (ids.isEmpty()) return
        cleared += ids
        _state.update { publish(it) }
        val w = viewModelScope.launch {
            try {
                backend.markRead(ids)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Best effort: the next time the tab opens, these are sent again.
                failedWrites += ids
                if (!_state.value.tabOpen) {
                    cleared.removeAll(ids.toSet())
                    failedWrites.removeAll(ids.toSet())
                    _state.update { publish(it) }
                }
            }
        }
        writeJobs += w
        w.invokeOnCompletion { writeJobs.remove(w) }
    }

    /** Stops listening and forgets everything (logging out, or leaving the student shell). */
    fun reset() {
        job?.cancel()
        job = null
        writeJobs.toList().forEach { it.cancel() }
        writeJobs.clear()
        known = null
        queue.clear()
        cleared.clear()
        failedWrites.clear()
        visitNew = emptySet()
        _state.value = NotificationsUiState()
    }
}
