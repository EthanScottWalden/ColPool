package io.github.ethanscottwalden.colpool.data.search

import com.google.firebase.firestore.FirebaseFirestore
import io.github.ethanscottwalden.colpool.data.series.HostedSeries
import io.github.ethanscottwalden.colpool.data.series.HostedSeriesParser
import io.github.ethanscottwalden.colpool.data.session.await
import kotlin.coroutines.cancellation.CancellationException

class FirebaseSearchBackend(
    private val firestore: FirebaseFirestore,
    private val uid: () -> String?,
) : SearchBackend {

    override fun currentUid(): String? = uid()

    override suspend fun searchSeries(weekdayIds: List<String>): List<HostedSeries> {
        if (weekdayIds.isEmpty()) return emptyList()
        val snap = firestore.collection("series")
            .whereEqualTo("status", "active")
            .whereArrayContainsAny("openWeekdays", weekdayIds)
            .get().await()
        return snap.documents.mapNotNull { HostedSeriesParser.parse(it.id, it.data ?: emptyMap()) }
    }

    override suspend fun loadStartingPoint(): GeoPoint? {
        val me = uid() ?: error("Not signed in")
        val doc = firestore.collection("privateDetails").document(me).get().await()
        return parseStartingPoint(doc.data)
    }

    override suspend fun loadRequestedSeriesIds(): Set<String> {
        val me = uid() ?: error("Not signed in")
        val snap = firestore.collection("requests").whereEqualTo("riderId", me).get().await()
        return snap.documents.mapNotNull { it.getString("seriesId") ?: it.id.substringBefore('_').ifEmpty { null } }.toSet()
    }

    override suspend fun loadHostFirstName(hostId: String): String? = try {
        firestore.collection("profiles").document(hostId).get().await().getString("firstName")?.trim()?.ifEmpty { null }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    companion object {
        /** `startingAddress.{lat,lng}` as a point, or null when either is missing or not a valid coordinate. */
        fun parseStartingPoint(doc: Map<String, Any?>?): GeoPoint? {
            val a = doc?.get("startingAddress") as? Map<*, *> ?: return null
            val lat = (a["lat"] as? Number)?.toDouble() ?: return null
            val lng = (a["lng"] as? Number)?.toDouble() ?: return null
            if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
            return GeoPoint(lat, lng)
        }
    }
}
