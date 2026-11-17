package io.github.ethanscottwalden.colpool.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorDetailTest {
    @Test fun usesSimpleClassNameAndMessage() {
        assertEquals("IllegalStateException: boom", ErrorDetail.format(IllegalStateException("boom")))
    }

    @Test fun noMessageGivesJustTheClassName() {
        assertEquals("IllegalStateException", ErrorDetail.format(IllegalStateException()))
    }

    @Test fun redactsEmailsAndSecrets() {
        val d = ErrorDetail.format(
            RuntimeException("The user jordan.lee@calbaptist.edu failed with pw demo-pass-123 (other@x.org)"),
            secrets = listOf("jordan.lee@calbaptist.edu", "demo-pass-123"),
        )
        assertFalse(d, d.contains("@calbaptist"))
        assertFalse(d, d.contains("other@x.org"))
        assertFalse(d, d.contains("demo-pass-123"))
        assertTrue(d, d.startsWith("RuntimeException: "))
    }

    @Test fun redactsSecretsCaseInsensitivelyAndIgnoresBlankSecrets() {
        assertEquals("RuntimeException: bad [redacted]", ErrorDetail.format(RuntimeException("bad HUNTER2"), listOf("hunter2", "")))
    }

    @Test fun shortensLongMessages() {
        val d = ErrorDetail.format(RuntimeException("word ".repeat(100)))
        assertTrue(d.length < 240)
        assertTrue(d.endsWith("..."))
    }

    @Test fun releaseShowsNothing() {
        assertNull(ErrorDetail.forDisplay(RuntimeException("boom"), enabled = false))
        assertEquals("RuntimeException: boom", ErrorDetail.forDisplay(RuntimeException("boom"), enabled = true))
    }

    @Test fun masksJwts() {
        val d = ErrorDetail.format(RuntimeException("bad token eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjMifQ.c2lnbmF0dXJl end"))
        assertEquals("RuntimeException: bad token [token] end", d)
    }

    @Test fun masksLongTokenRunsButKeepsShortWords() {
        val d = ErrorDetail.format(RuntimeException("key AIzaSyA1b2C3d4E5f6G7h8I9j0K_lmnop used, code auth/network-request-failed"))
        assertEquals("RuntimeException: key [token] used, code auth/network-request-failed", d)
    }

    @Test fun keepsLongErrorCodesAndClassNames() {
        val msg = "INVALID_LOGIN_CREDENTIALS account-exists-with-different-credential FirebaseAuthInvalidCredentialsException"
        assertEquals("RuntimeException: $msg", ErrorDetail.format(RuntimeException(msg)))
    }
}
