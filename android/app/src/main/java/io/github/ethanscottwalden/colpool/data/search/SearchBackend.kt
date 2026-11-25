package io.github.ethanscottwalden.colpool.data.search

import io.github.ethanscottwalden.colpool.data.model.ProfileOptions
import io.github.ethanscottwalden.colpool.data.series.HostedSeries

/** A point on the map. The rider's one is private: never log it. */
data class GeoPoint(val lat: Double, val lng: Double) {
    override fun toString(): String = "GeoPoint(redacted)"
}

/**
 * What a Search card needs from an active host's profile: the first name and the fields the match formula reads (RC-21).
 * Age, gender, about me and the last name are never read here.
 */
data class HostMatchFields(
    val firstName: String?,
    val year: String? = null,
    val major: String? = null,
    val interests: List<String> = emptyList(),
    val traits: List<String> = emptyList(),
) {
    override fun toString(): String = "HostMatchFields(redacted)"
}

/** Firebase reads for Search (US-09). Read-only, and never a Maps call (RC-28). Faked in JVM tests. */
interface SearchBackend {
    /** The signed-in student's uid, or null. */
    fun currentUid(): String?

    /** Active series with an open seat on at least one of [weekdayIds] (one query). Documents that do not parse are skipped. Throws on failure. */
    suspend fun searchSeries(weekdayIds: List<String>): List<HostedSeries>

    /** The rider's own starting-address coordinates (`privateDetails/{uid}`), or null when missing. Throws on failure. */
    suspend fun loadStartingPoint(): GeoPoint?

    /** Ids of series the rider has already requested (`requests` where `riderId == uid`). Throws on failure. */
    suspend fun loadRequestedSeriesIds(): Set<String>

    /** One read of the host's profile (`profiles/{hostId}`, readable for active hosts): first name and match fields, or null if it can't be read. Never throws except cancellation. */
    suspend fun loadHostFields(hostId: String): HostMatchFields?

    /** The predefined profile lists (`options/profile`) for the preference panel. Throws on failure. */
    suspend fun loadOptions(): ProfileOptions
}
