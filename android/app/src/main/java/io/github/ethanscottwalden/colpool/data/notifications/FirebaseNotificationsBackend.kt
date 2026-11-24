package io.github.ethanscottwalden.colpool.data.notifications

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import io.github.ethanscottwalden.colpool.data.session.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirebaseNotificationsBackend(
    private val firestore: FirebaseFirestore,
    private val uid: () -> String?,
) : NotificationsBackend {

    private fun collection() = uid()?.let { firestore.collection("users").document(it).collection("notifications") }

    override fun observe(): Flow<NotificationsUpdate> = callbackFlow {
        val col = collection()
        if (col == null) {
            trySend(NotificationsUpdate.Failed)
            close()
            return@callbackFlow
        }
        // No composite index (ADR 0005): history is one orderBy on createdAt.
        val reg = col.orderBy("createdAt", Query.Direction.DESCENDING).limit(NOTIFICATIONS_LIMIT.toLong()).addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
            if (err != null || snap == null) {
                trySend(NotificationsUpdate.Failed)
            } else {
                trySend(NotificationsUpdate.Loaded(snap.documents.mapNotNull { NotificationParser.parse(it.id, it.data ?: emptyMap()) }, snap.metadata.isFromCache))
            }
        }
        awaitClose { reg.remove() }
    }

    override suspend fun markRead(ids: List<String>) {
        val col = collection() ?: error("not signed in")
        // A batch holds 500 writes; one update per document, touching only `read` (the rule allows nothing else).
        ids.distinct().chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.update(col.document(it), READ_ONLY_UPDATE) }
            batch.commit().await()
        }
    }

    companion object {
        /** The only field the client ever writes on a notification. */
        val READ_ONLY_UPDATE: Map<String, Any> = mapOf("read" to true)
    }
}
