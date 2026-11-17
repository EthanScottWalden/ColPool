package io.github.ethanscottwalden.colpool.ui.auth

import io.github.ethanscottwalden.colpool.ui.auth.SignUpFieldError.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignUpFormTest {
    private fun errors(first: String = "Jordan", last: String = "Lee", email: String = "jlee@calbaptist.edu", pw: String = "password1", consent: Boolean = true) =
        SignUpForm.validate(first, last, email, pw, consent)

    @Test fun validFormHasNoErrors() = assertTrue(errors().isEmpty())

    @Test fun emailMustBeCbuDomain() {
        assertEquals(setOf(Email), errors(email = "jlee@gmail.com"))
        assertEquals(setOf(Email), errors(email = "@calbaptist.edu"))
        assertEquals(setOf(Email), errors(email = "a@b@calbaptist.edu"))
        assertEquals(setOf(Email), errors(email = "jlee@sub.calbaptist.edu"))
        assertEquals(setOf(Email), errors(email = ""))
    }

    @Test fun emailIsTrimmedAndCaseInsensitive() {
        assertTrue(errors(email = "  JLee@CalBaptist.EDU ").isEmpty())
        assertEquals("jlee@calbaptist.edu", SignUpForm.normalizeEmail("  JLee@CalBaptist.EDU "))
    }

    @Test fun passwordNeedsEightCharactersAndIsNotTrimmed() {
        assertEquals(setOf(Password), errors(pw = "1234567"))
        assertTrue(errors(pw = "12345678").isEmpty())
        assertTrue(errors(pw = "       1").isEmpty()) // 8 chars counting spaces
    }

    @Test fun namesUpTo30CharactersAfterTrim() {
        assertTrue(errors(first = "a".repeat(30), last = "  " + "b".repeat(30) + "  ").isEmpty())
        assertEquals(setOf(FirstName), errors(first = "a".repeat(31)))
        assertEquals(setOf(LastName), errors(last = "   "))
        assertEquals(setOf(FirstName, LastName), errors(first = "", last = ""))
    }

    @Test fun consentIsRequired() {
        assertEquals(setOf(Consent), errors(consent = false))
        assertFalse(errors().contains(Consent))
    }
}
