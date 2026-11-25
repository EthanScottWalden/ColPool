package io.github.ethanscottwalden.colpool.data.requests

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import io.github.ethanscottwalden.colpool.data.model.ProfileOptions
import io.github.ethanscottwalden.colpool.data.series.FirebaseSeriesBackend
import io.github.ethanscottwalden.colpool.data.session.FirebaseOnboardingBackend
import io.github.ethanscottwalden.colpool.data.session.await
import kotlin.coroutines.cancellation.CancellationException

class FirebaseRequestBackend(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val uid: () -> String?,
) : RequestBackend {

    override fun currentUid(): String? = uid()

    override suspend fun loadHostProfile(hostId: String): HostProfile? = try {
        parseHostProfile(firestore.collection("profiles").document(hostId).get().await().data)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    override suspend fun loadOptions(): ProfileOptions {
        val snap = firestore.collection("options").document("profile").get().await()
        return FirebaseOnboardingBackend.parseOptions(snap.data ?: error("options/profile is missing"))
    }

    override suspend fun loadCampusNames(): Map<String, String> {
        val snap = firestore.collection("campusPoints").get().await()
        return FirebaseSeriesBackend.parseCampusPoints(snap.documents.map { it.id to (it.data ?: emptyMap()) }).associate { it.id to it.name }
    }

    override suspend fun hasRequested(seriesId: String): Boolean = try {
        val me = uid() ?: return false
        firestore.collection("requests").document("${seriesId}_$me").get().await().exists()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    override suspend fun sendRequest(seriesId: String, weekdayIds: List<String>): SendRequestResult = try {
        val res = functions.getHttpsCallable("sendRequest").call(mapOf("seriesId" to seriesId, "weekdays" to weekdayIds)).await()
        if ((res.getData() as? Map<*, *>)?.get("requestId") is String) SendRequestResult.Sent else SendRequestResult.Failed
    } catch (e: CancellationException) {
        throw e
    } catch (e: FirebaseFunctionsException) {
        mapFailure(e.code.name, e.details)
    } catch (e: Exception) {
        SendRequestResult.Failed
    }

    companion object {
        /** A profile document as a [HostProfile], or null when the names are missing. Only public fields are read. */
        fun parseHostProfile(doc: Map<String, Any?>?): HostProfile? {
            if (doc == null) return null
            val first = (doc["firstName"] as? String)?.trim()?.ifEmpty { null } ?: return null
            val last = (doc["lastName"] as? String)?.trim()?.ifEmpty { null } ?: return null
            return HostProfile(
                firstName = first,
                lastName = last,
                age = (doc["age"] as? Number)?.toInt(),
                gender = doc["gender"] as? String,
                year = doc["year"] as? String,
                major = doc["major"] as? String,
                interests = (doc["interests"] as? List<*>).orEmpty().filterIsInstance<String>(),
                traits = (doc["traits"] as? List<*>).orEmpty().filterIsInstance<String>(),
                aboutMe = doc["aboutMe"] as? String,
            )
        }

        /** [code] is the `FirebaseFunctionsException.Code` name; the reason is `details.reason` (ADR 0010). */
        fun mapFailure(code: String, details: Any?): SendRequestResult {
            val d = details as? Map<*, *>
            return when (code) {
                "INVALID_ARGUMENT" -> if (d?.get("reason") in listOf("series-id-invalid", "weekdays-invalid")) SendRequestResult.Invalid else SendRequestResult.Failed
                "NOT_FOUND" -> SendRequestResult.NotAvailable
                "ALREADY_EXISTS" -> if (d?.get("reason") == "already-requested") SendRequestResult.AlreadyRequested else SendRequestResult.Failed
                "FAILED_PRECONDITION" -> when (d?.get("reason")) {
                    "own-series" -> SendRequestResult.OwnSeries
                    "series-not-active" -> SendRequestResult.NotAvailable
                    "weekdays-unavailable" -> parseFailures(d["weekdays"]).takeIf { it.isNotEmpty() }?.let { SendRequestResult.WeekdaysUnavailable(it) } ?: SendRequestResult.Failed
                    else -> SendRequestResult.Failed
                }
                else -> SendRequestResult.Failed
            }
        }

        /** `[{weekday, reason}]`; entries that are malformed or have an unknown reason are skipped. */
        fun parseFailures(v: Any?): List<DayFailure> = (v as? List<*>).orEmpty().mapNotNull { e ->
            val m = e as? Map<*, *> ?: return@mapNotNull null
            val wd = m["weekday"] as? String ?: return@mapNotNull null
            val reason = when (m["reason"]) {
                "full" -> DayFailureReason.Full
                "no-trips-left" -> DayFailureReason.NoTripsLeft
                "conflict" -> DayFailureReason.Conflict
                else -> return@mapNotNull null
            }
            DayFailure(wd, reason)
        }
    }
}
