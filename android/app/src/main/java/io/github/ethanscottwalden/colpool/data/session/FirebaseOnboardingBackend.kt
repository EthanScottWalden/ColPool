package io.github.ethanscottwalden.colpool.data.session

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import io.github.ethanscottwalden.colpool.data.model.OptionItem
import io.github.ethanscottwalden.colpool.data.model.ProfileOptions

class FirebaseOnboardingBackend(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : OnboardingBackend {

    override suspend fun loadOptions(): ProfileOptions {
        val snap = firestore.collection("options").document("profile").get().await()
        return parseOptions(snap.data ?: error("options/profile is missing"))
    }

    override suspend fun save(data: OnboardingSave) {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        val batch = firestore.batch()
        batch.update(firestore.collection("profiles").document(uid), profileFields(data))
        batch.set(firestore.collection("privateDetails").document(uid), privateDetailsFields(data))
        batch.commit().await()
    }

    companion object {
        /** Profile update: only the fields the student set, plus the server time Rules require. */
        fun profileFields(d: OnboardingSave): Map<String, Any> = buildMap {
            d.age?.let { put("age", it) }
            d.gender?.let { put("gender", it) }
            d.year?.let { put("year", it) }
            d.major?.let { put("major", it) }
            if (d.interests.isNotEmpty()) put("interests", d.interests)
            if (d.traits.isNotEmpty()) put("traits", d.traits)
            d.aboutMe?.let { put("aboutMe", it) }
            put("updatedAt", FieldValue.serverTimestamp())
        }

        fun privateDetailsFields(d: OnboardingSave): Map<String, Any> = mapOf(
            "startingAddress" to mapOf(
                "text" to d.address.text,
                "placeId" to d.address.placeId,
                "lat" to d.address.lat,
                "lng" to d.address.lng,
            ),
            "updatedAt" to FieldValue.serverTimestamp(),
        )

        /** Parses the seeded doc: each list is `[{id, label, order}]`; entries are returned sorted by `order`. */
        fun parseOptions(doc: Map<String, Any?>): ProfileOptions = ProfileOptions(
            genders = list(doc["genders"]),
            years = list(doc["years"]),
            majors = list(doc["majors"]),
            interests = list(doc["interests"]),
            traits = list(doc["traits"]),
        )

        private fun list(raw: Any?): List<OptionItem> =
            (raw as? List<*>).orEmpty()
                .mapNotNull { it as? Map<*, *> }
                .mapNotNull { m ->
                    val id = m["id"] as? String ?: return@mapNotNull null
                    val label = m["label"] as? String ?: return@mapNotNull null
                    Triple((m["order"] as? Number)?.toDouble() ?: 0.0, id, label)
                }
                .sortedBy { it.first }
                .map { OptionItem(it.second, it.third) }
    }
}
