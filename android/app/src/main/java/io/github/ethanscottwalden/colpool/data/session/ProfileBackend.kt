package io.github.ethanscottwalden.colpool.data.session

import io.github.ethanscottwalden.colpool.data.model.OwnProfile
import io.github.ethanscottwalden.colpool.data.model.ProfileOptions
import io.github.ethanscottwalden.colpool.data.places.SelectedAddress

/**
 * What Edit Profile changes, relative to the stored profile. [set] holds changed fields (only keys the Rules allow),
 * [clear] the optional fields to remove (the key is deleted, never written empty), [newAddress] a changed starting address.
 * Holds private data: never log it.
 */
data class ProfileChanges(
    val set: Map<String, Any> = emptyMap(),
    val clear: Set<String> = emptySet(),
    val newAddress: SelectedAddress? = null,
) {
    val isEmpty: Boolean get() = set.isEmpty() && clear.isEmpty() && newAddress == null

    override fun toString(): String = "ProfileChanges(redacted)"
}

/** Firebase access for the Profile tab and Edit Profile. Faked in JVM tests. */
interface ProfileBackend {
    /** Reads the signed-in student's own profile, private address and email. Throws on failure. */
    suspend fun loadProfile(): OwnProfile

    /** Reads `options/profile`. Throws on failure. */
    suspend fun loadOptions(): ProfileOptions

    /**
     * Writes [changes] in ONE batch: the profile update (with `updatedAt`) and, only when the address changed,
     * the private details. Throws on failure.
     */
    suspend fun save(changes: ProfileChanges)
}
