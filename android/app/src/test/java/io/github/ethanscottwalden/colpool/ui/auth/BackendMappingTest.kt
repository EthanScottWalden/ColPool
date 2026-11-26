package io.github.ethanscottwalden.colpool.ui.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestoreException
import io.github.ethanscottwalden.colpool.data.session.AuthFailureKind
import io.github.ethanscottwalden.colpool.data.session.FirebaseAuthBackend
import io.github.ethanscottwalden.colpool.data.session.PrivateDetailsResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pure routing helpers of FirebaseAuthBackend (Robolectric only because Firebase exception classes need Android). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackendMappingTest {
    @Test fun claimAndFailureMapping() {
        val b = FirebaseAuthBackend.Companion
        assertEquals(true, b.isStaffClaims(mapOf("institution" to true)))
        assertEquals(true, b.isStaffClaims(mapOf("institution" to false)))
        assertEquals(true, b.isStaffClaims(mapOf("institution" to "yes")))
        assertEquals(false, b.isStaffClaims(mapOf("other" to true)))
        assertEquals(false, b.isStaffClaims(emptyMap()))
        assertEquals(PrivateDetailsResult.HasStartingAddress, b.privateDetailsResult(true))
        assertEquals(PrivateDetailsResult.Missing, b.privateDetailsResult(false))
        fun ex(c: FirebaseFirestoreException.Code) = FirebaseFirestoreException("x", c)
        assertEquals(PrivateDetailsResult.NoConsent, b.noConsentOrNull(ex(FirebaseFirestoreException.Code.PERMISSION_DENIED)))
        assertNull(b.noConsentOrNull(ex(FirebaseFirestoreException.Code.UNAVAILABLE)))
        assertNull(b.noConsentOrNull(ex(FirebaseFirestoreException.Code.NOT_FOUND)))
    }

    @Test fun signInErrorsMapToWrongEmailOrPassword() {
        val b = FirebaseAuthBackend.Companion
        // Real project: INVALID_LOGIN_CREDENTIALS arrives as ERROR_INVALID_CREDENTIAL.
        assertEquals(
            AuthFailureKind.InvalidCredentials,
            b.signInKindFor(FirebaseAuthInvalidCredentialsException("ERROR_INVALID_CREDENTIAL", "The supplied auth credential is incorrect.")),
        )
        assertEquals(
            AuthFailureKind.InvalidCredentials,
            b.signInKindFor(FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "no user")),
        )
        assertEquals(AuthFailureKind.Network, b.signInKindFor(FirebaseNetworkException("offline")))
        assertEquals(AuthFailureKind.Unknown, b.signInKindFor(IllegalStateException("x")))
    }
}
