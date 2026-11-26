package io.github.ethanscottwalden.colpool.data.session

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlin.coroutines.cancellation.CancellationException

class FirebaseAuthBackend(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) : AuthBackend {

    override suspend fun currentUser(): SessionUser? {
        val user = auth.currentUser ?: return null
        val claims = user.getIdToken(false).await().claims
        // Any value counts, matching Rules and functions (#18).
        return SessionUser(user.uid, isStaff = isStaffClaims(claims))
    }

    override suspend fun readPrivateDetails(uid: String): PrivateDetailsResult =
        try {
            val snap = firestore.collection("privateDetails").document(uid).get(Source.SERVER).await()
            privateDetailsResult(snap.exists() && snap.get("startingAddress") != null)
        } catch (e: FirebaseFirestoreException) {
            noConsentOrNull(e) ?: throw e
        }

    override suspend fun signIn(email: String, password: String) {
        try {
            auth.signInWithEmailAndPassword(email, password).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AuthFailure(signInKindFor(e), e)
        }
    }

    override suspend fun signUp(request: SignUpRequest) {
        try {
            val data = mapOf(
                "email" to request.email,
                "password" to request.password,
                "firstName" to request.firstName,
                "lastName" to request.lastName,
                "consent" to true,
            )
            functions.getHttpsCallable("signUp").call(data).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AuthFailure(signUpKind(e), e)
        }
    }

    override fun signOut() = auth.signOut()

    private fun signUpKind(e: Exception): AuthFailureKind {
        if (e is FirebaseNetworkException) return AuthFailureKind.Network
        if (e !is FirebaseFunctionsException) return AuthFailureKind.Unknown
        if (e.code == FirebaseFunctionsException.Code.DEADLINE_EXCEEDED) return AuthFailureKind.CouldNotConfirm
        if (e.code == FirebaseFunctionsException.Code.UNAVAILABLE) return AuthFailureKind.Network
        val reason = (e.details as? Map<*, *>)?.get("reason") as? String
        return signUpKindForReason(reason)
    }

    companion object {
        const val INSTITUTION_CLAIM = "institution"

        /**
         * Sign-in failure to user text. The real backend (email enumeration protection) reports a wrong email or
         * password as INVALID_LOGIN_CREDENTIALS, which the SDK raises as FirebaseAuthInvalidCredentialsException;
         * the emulator may still send user-not-found (FirebaseAuthInvalidUserException). Both read the same.
         */
        fun signInKindFor(e: Exception): AuthFailureKind = when (e) {
            is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException -> AuthFailureKind.InvalidCredentials
            is FirebaseNetworkException -> AuthFailureKind.Network
            else -> AuthFailureKind.Unknown
        }

        /** Staff = the claim is present with any value (matches Rules and functions, #18). */
        fun isStaffClaims(claims: Map<String, Any?>): Boolean = claims.containsKey(INSTITUTION_CLAIM)

        fun privateDetailsResult(hasStartingAddress: Boolean): PrivateDetailsResult =
            if (hasStartingAddress) PrivateDetailsResult.HasStartingAddress else PrivateDetailsResult.Missing

        /** PERMISSION_DENIED means no consent record; any other failure returns null (the caller rethrows, so routing shows the retry state). */
        fun noConsentOrNull(e: FirebaseFirestoreException): PrivateDetailsResult? =
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) PrivateDetailsResult.NoConsent else null

        fun signUpKindForReason(reason: String?): AuthFailureKind = when (reason) {
            "email-domain" -> AuthFailureKind.EmailDomain
            "password-too-short" -> AuthFailureKind.PasswordTooShort
            "first-name-invalid" -> AuthFailureKind.FirstNameInvalid
            "last-name-invalid" -> AuthFailureKind.LastNameInvalid
            "consent-required" -> AuthFailureKind.ConsentRequired
            "email-in-use" -> AuthFailureKind.EmailInUse
            "internal" -> AuthFailureKind.Internal
            else -> AuthFailureKind.Unknown
        }
    }
}
