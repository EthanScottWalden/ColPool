package io.github.ethanscottwalden.colpool.data.requests

import io.github.ethanscottwalden.colpool.data.model.ProfileOptions

/**
 * A host's public profile (`profiles/{hostId}`, readable for active hosts, ADR 0009). Never carries the email or the
 * starting address. Optional fields are null or empty when the host left them out.
 */
data class HostProfile(
    val firstName: String,
    val lastName: String,
    val age: Int? = null,
    val gender: String? = null,
    val year: String? = null,
    val major: String? = null,
    val interests: List<String> = emptyList(),
    val traits: List<String> = emptyList(),
    val aboutMe: String? = null,
) {
    override fun toString(): String = "HostProfile(redacted)"
}

/** Why one weekday of a request failed (ADR 0010). */
enum class DayFailureReason { Full, NoTripsLeft, Conflict }

data class DayFailure(val weekdayId: String, val reason: DayFailureReason)

/** What the `sendRequest` callable answered (ADR 0010). */
sealed interface SendRequestResult {
    data object Sent : SendRequestResult

    /** `series-id-invalid` or `weekdays-invalid`: the app sent something it should not have. */
    data object Invalid : SendRequestResult

    /** `not-found` or `series-not-active`. */
    data object NotAvailable : SendRequestResult

    data object OwnSeries : SendRequestResult
    data object AlreadyRequested : SendRequestResult

    /** `weekdays-unavailable`: the failing days with their reasons. Nothing was saved. */
    data class WeekdaysUnavailable(val failures: List<DayFailure>) : SendRequestResult

    /** `unavailable`, `internal`, network or anything unknown. */
    data object Failed : SendRequestResult
}

/** Firebase access for Series Details and the request flow. Faked in JVM tests. */
interface RequestBackend {
    /** The signed-in student's uid, or null. */
    fun currentUid(): String?

    /** The host's public profile, or null when it can't be read. Never throws except cancellation. */
    suspend fun loadHostProfile(hostId: String): HostProfile?

    /** The predefined lists that turn stored ids into labels. Throws on failure. */
    suspend fun loadOptions(): ProfileOptions

    /** Campus point id to name. Throws on failure. */
    suspend fun loadCampusNames(): Map<String, String>

    /** True when this rider already has a request for [seriesId] (`requests/{seriesId}_{uid}`). Never throws except cancellation (false when unreadable). */
    suspend fun hasRequested(seriesId: String): Boolean

    /** Calls `sendRequest` with the series id and the chosen weekday ids. Never throws except cancellation. */
    suspend fun sendRequest(seriesId: String, weekdayIds: List<String>): SendRequestResult
}
