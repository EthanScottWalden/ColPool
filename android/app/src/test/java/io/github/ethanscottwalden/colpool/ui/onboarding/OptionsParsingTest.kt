package io.github.ethanscottwalden.colpool.ui.onboarding

import io.github.ethanscottwalden.colpool.data.model.OptionItem
import io.github.ethanscottwalden.colpool.data.places.SelectedAddress
import io.github.ethanscottwalden.colpool.data.session.FirebaseOnboardingBackend
import io.github.ethanscottwalden.colpool.data.session.OnboardingSave
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OptionsParsingTest {
    @Test fun parsesAndSortsByOrder() {
        val doc = mapOf<String, Any?>(
            "genders" to listOf(
                mapOf("id" to "man", "label" to "Man", "order" to 2L),
                mapOf("id" to "woman", "label" to "Woman", "order" to 1L),
                mapOf("label" to "no id", "order" to 3L),
            ),
            "genderIds" to listOf("woman", "man"),
        )
        val o = FirebaseOnboardingBackend.parseOptions(doc)
        assertEquals(listOf(OptionItem("woman", "Woman"), OptionItem("man", "Man")), o.genders)
        assertTrue(o.majors.isEmpty())
    }

    @Test fun writtenFieldsMatchTheRulesKeys() {
        val save = OnboardingSave(SelectedAddress("1 Test St", "p", 1.0, 2.0), age = 20, interests = listOf("music"))
        val f = FirebaseOnboardingBackend.profileFields(save)
        assertEquals(setOf("age", "interests", "updatedAt"), f.keys)
        val p = FirebaseOnboardingBackend.privateDetailsFields(save)
        assertEquals(setOf("startingAddress", "updatedAt"), p.keys)
        assertEquals(setOf("text", "placeId", "lat", "lng"), (p["startingAddress"] as Map<*, *>).keys)
    }
}
