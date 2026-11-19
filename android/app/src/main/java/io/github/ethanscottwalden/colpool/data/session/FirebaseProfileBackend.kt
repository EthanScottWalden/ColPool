package io.github.ethanscottwalden.colpool.data.session

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import io.github.ethanscottwalden.colpool.data.model.OwnProfile
import io.github.ethanscottwalden.colpool.data.model.ProfileOptions
import io.github.ethanscottwalden.colpool.data.places.SelectedAddress

class FirebaseProfileBackend(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ProfileBackend {

    private fun uid() = auth.currentUser?.uid ?: error("Not signed in")

    override suspend fun loadProfile(): OwnProfile {
        val uid = uid()
        val user = firestore.collection("users").document(uid).get().await()
        val profile = firestore.collection("profiles").document(uid).get().await()
        val details = firestore.collection("privateDetails").document(uid).get().await()
        return parseOwnProfile(
            user.data ?: error("users doc is missing"),
            profile.data ?: error("profiles doc is missing"),
            details.data ?: error("privateDetails doc is missing"),
        )
    }

    override suspend fun loadOptions(): ProfileOptions {
        val snap = firestore.collection("options").document("profile").get().await()
        return FirebaseOnboardingBackend.parseOptions(snap.data ?: error("options/profile is missing"))
    }

    override suspend fun save(changes: ProfileChanges) {
        if (changes.isEmpty) return
        val uid = uid()
        val batch = firestore.batch()
        if (changes.set.isNotEmpty() || changes.clear.isNotEmpty()) {
            batch.update(firestore.collection("profiles").document(uid), profileUpdateFields(changes))
        }
        changes.newAddress?.let {
            batch.set(firestore.collection("privateDetails").document(uid), privateDetailsFields(it))
        }
        batch.commit().await()
    }

    companion object {
        /** Changed fields, `FieldValue.delete()` for removed optional fields, and the server time Rules require. */
        fun profileUpdateFields(c: ProfileChanges): Map<String, Any> = buildMap {
            putAll(c.set)
            c.clear.forEach { put(it, FieldValue.delete()) }
            put("updatedAt", FieldValue.serverTimestamp())
        }

        fun privateDetailsFields(a: SelectedAddress): Map<String, Any> = mapOf(
            "startingAddress" to mapOf("text" to a.text, "placeId" to a.placeId, "lat" to a.lat, "lng" to a.lng),
            "updatedAt" to FieldValue.serverTimestamp(),
        )

        fun parseOwnProfile(user: Map<String, Any?>, profile: Map<String, Any?>, details: Map<String, Any?>): OwnProfile {
            val addr = details["startingAddress"] as? Map<*, *> ?: error("startingAddress is missing")
            return OwnProfile(
                firstName = profile["firstName"] as? String ?: error("firstName is missing"),
                lastName = profile["lastName"] as? String ?: error("lastName is missing"),
                email = user["email"] as? String ?: error("email is missing"),
                address = SelectedAddress(
                    text = addr["text"] as? String ?: error("address text is missing"),
                    placeId = addr["placeId"] as? String ?: error("address placeId is missing"),
                    lat = (addr["lat"] as? Number)?.toDouble() ?: error("address lat is missing"),
                    lng = (addr["lng"] as? Number)?.toDouble() ?: error("address lng is missing"),
                ),
                age = (profile["age"] as? Number)?.toInt(),
                gender = profile["gender"] as? String,
                year = profile["year"] as? String,
                major = profile["major"] as? String,
                interests = (profile["interests"] as? List<*>).orEmpty().filterIsInstance<String>(),
                traits = (profile["traits"] as? List<*>).orEmpty().filterIsInstance<String>(),
                aboutMe = profile["aboutMe"] as? String,
            )
        }
    }
}
