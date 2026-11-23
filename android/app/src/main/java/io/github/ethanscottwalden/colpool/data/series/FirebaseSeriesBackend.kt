package io.github.ethanscottwalden.colpool.data.series

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import io.github.ethanscottwalden.colpool.data.session.await
import kotlin.coroutines.cancellation.CancellationException

class FirebaseSeriesBackend(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) : SeriesBackend {

    override suspend fun loadCampusPoints(): List<CampusPoint> {
        val snap = firestore.collection("campusPoints").get().await()
        return parseCampusPoints(snap.documents.map { it.id to (it.data ?: emptyMap()) })
    }

    override suspend fun routePreview(series: Map<String, Any>, draftId: String?): RoutePreviewResult = try {
        val data = if (draftId == null) mapOf("series" to series) else mapOf("series" to series, "draftId" to draftId)
        val res = functions.getHttpsCallable("routePreview").call(data).await()
        parseSuccess(res.getData())
    } catch (e: CancellationException) {
        throw e
    } catch (e: FirebaseFunctionsException) {
        mapFailure(e.code.name, e.details)
    } catch (e: Exception) {
        RoutePreviewResult.Failed
    }

    companion object {
        /** Active points only, sorted by `order`. A point without a name is skipped. */
        fun parseCampusPoints(docs: List<Pair<String, Map<String, Any?>>>): List<CampusPoint> =
            docs.filter { (_, d) -> d["active"] == true }
                .mapNotNull { (id, d) ->
                    val name = d["name"] as? String ?: return@mapNotNull null
                    Triple((d["order"] as? Number)?.toDouble() ?: 0.0, id, name)
                }
                .sortedBy { it.first }
                .map { CampusPoint(it.second, it.third) }

        fun parseSuccess(data: Any?): RoutePreviewResult {
            val m = data as? Map<*, *> ?: return RoutePreviewResult.Failed
            val id = m["draftId"] as? String ?: return RoutePreviewResult.Failed
            return RoutePreviewResult.Success(id, m["inputHash"] as? String ?: "")
        }

        /** [code] is the `FirebaseFunctionsException.Code` name, e.g. `INVALID_ARGUMENT`. */
        fun mapFailure(code: String, details: Any?): RoutePreviewResult = when (code) {
            "INVALID_ARGUMENT" -> parseErrors(details).takeIf { it.isNotEmpty() }?.let { RoutePreviewResult.Invalid(it) } ?: RoutePreviewResult.Failed
            "UNAVAILABLE" -> RoutePreviewResult.Unavailable
            "NOT_FOUND" -> RoutePreviewResult.DraftRejected
            "FAILED_PRECONDITION" -> if ((details as? Map<*, *>)?.get("reason") == "draft-posted") RoutePreviewResult.DraftRejected else RoutePreviewResult.Failed
            else -> RoutePreviewResult.Failed
        }

        /** `details` is `{ errors: [{ code, path, params? }] }`; malformed entries are skipped. */
        fun parseErrors(details: Any?): List<SeriesError> {
            val list = (details as? Map<*, *>)?.get("errors") as? List<*> ?: return emptyList()
            return list.mapNotNull { e ->
                val m = e as? Map<*, *> ?: return@mapNotNull null
                val code = m["code"] as? String ?: return@mapNotNull null
                val params = (m["params"] as? Map<*, *>).orEmpty().entries.associate { it.key.toString() to it.value }
                SeriesError(code, m["path"] as? String ?: "", params)
            }
        }
    }
}
