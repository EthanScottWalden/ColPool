package io.github.ethanscottwalden.colpool.ui.profile

import io.github.ethanscottwalden.colpool.data.session.FirebaseProfileBackend
import io.github.ethanscottwalden.colpool.data.session.ProfileChanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseProfileBackendTest {
    @Test fun parsesTheThreeDocuments() {
        val p = FirebaseProfileBackend.parseOwnProfile(
            mapOf("email" to "jexample@calbaptist.edu"),
            mapOf("firstName" to "Jordan", "lastName" to "Example", "age" to 20L, "interests" to listOf("music"), "aboutMe" to "hi"),
            mapOf("startingAddress" to mapOf("text" to "1 Test St", "placeId" to "p", "lat" to 1.5, "lng" to 2L)),
        )
        assertEquals("jexample@calbaptist.edu", p.email)
        assertEquals(20, p.age); assertEquals(listOf("music"), p.interests); assertEquals("hi", p.aboutMe)
        assertEquals(2.0, p.address.lng, 0.0)
        assertTrue(p.traits.isEmpty())
    }

    @Test fun updateWritesOnlyRulesKeysWithDeletesAndServerTime() {
        val f = FirebaseProfileBackend.profileUpdateFields(ProfileChanges(set = mapOf("firstName" to "A"), clear = setOf("age", "interests")))
        assertEquals(setOf("firstName", "age", "interests", "updatedAt"), f.keys)
        // A removed field is a delete marker, never null, an empty list or a blank string.
        assertTrue(f["age"] != null && f["age"] !is String && f["age"] !is List<*>)
    }

    @Test fun privateDetailsKeysMatchTheRules() {
        val p = FirebaseProfileBackend.privateDetailsFields(FakeProfileBackend.ADDRESS)
        assertEquals(setOf("startingAddress", "updatedAt"), p.keys)
        assertEquals(setOf("text", "placeId", "lat", "lng"), (p["startingAddress"] as Map<*, *>).keys)
    }
}
