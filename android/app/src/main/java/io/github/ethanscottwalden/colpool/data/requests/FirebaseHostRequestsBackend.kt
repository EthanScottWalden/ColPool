package io.github.ethanscottwalden.colpool.data.requests

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import io.github.ethanscottwalden.colpool.data.model.ProfileOptions
import io.github.ethanscottwalden.colpool.data.session.FirebaseOnboardingBackend
import io.github.ethanscottwalden.colpool.data.session.await
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirebaseHostRequestsBackend(
    private val firestore: FirebaseFirestore,
    private val uid: () -> String?,
) : HostRequestsBackend {

    override fun observeRequests(seriesId: String?): Flow<RequestsUpdate> = callbackFlow {
        val me = uid()
        if (me == null) {
            trySend(RequestsUpdate.Failed)
            close()
            return@callbackFlow
        }
        // The rule needs hostId in the query; hostId + seriesId + createdAt is the #35 index.
        var q: Query = firestore.collection("requests").whereEqualTo("hostId", me)
        if (seriesId != null) q = q.whereEqualTo("seriesId", seriesId).orderBy("createdAt", Query.Direction.ASCENDING)
        val reg = q.addSnapshotListener { snap, err ->
            if (err != null || snap == null) {
                trySend(RequestsUpdate.Failed)
            } else {
                trySend(RequestsUpdate.Loaded(snap.documents.mapNotNull { IncomingRequestParser.parse(it.id, it.data ?: emptyMap()) }))
            }
        }
        awaitClose { reg.remove() }
    }

    override suspend fun loadRequesterProfile(riderId: String): HostProfile? = try {
        FirebaseRequestBackend.parseHostProfile(firestore.collection("profiles").document(riderId).get().await().data)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    override suspend fun loadOptions(): ProfileOptions {
        val snap = firestore.collection("options").document("profile").get().await()
        return FirebaseOnboardingBackend.parseOptions(snap.data ?: error("options/profile is missing"))
    }
}
