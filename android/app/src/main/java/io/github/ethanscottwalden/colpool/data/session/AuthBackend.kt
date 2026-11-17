package io.github.ethanscottwalden.colpool.data.session

/** The signed-in account as the routing decision sees it. */
data class SessionUser(val uid: String, val isStaff: Boolean)

/** Result of the student's own `privateDetails` read (ADR 0004). Other failures are thrown. */
enum class PrivateDetailsResult {
    /** No document, or no starting address in it: the student still has to onboard. */
    Missing,
    HasStartingAddress,
    /** PERMISSION_DENIED: Rules found no consent record (RC-06). */
    NoConsent,
}

/** Why sign-in or sign-up failed. Sign-up reasons mirror `functions/src/shared/auth/reasons.ts`. */
enum class AuthFailureKind {
    InvalidCredentials,
    Network,
    Unknown,
    EmailDomain,
    PasswordTooShort,
    FirstNameInvalid,
    LastNameInvalid,
    ConsentRequired,
    EmailInUse,
    Internal,
}

class AuthFailure(val kind: AuthFailureKind, cause: Throwable? = null) : Exception(kind.name, cause)

data class SignUpRequest(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
)

/** Everything the app needs from Firebase for sign-in and routing. Faked in JVM tests. */
interface AuthBackend {
    /** The signed-in user, or null when signed out. Throws if the ID token can't be read. */
    suspend fun currentUser(): SessionUser?

    /** A server read of the user's own `privateDetails` (never the cache). Throws on network or other errors. */
    suspend fun readPrivateDetails(uid: String): PrivateDetailsResult

    /** Throws [AuthFailure]. */
    suspend fun signIn(email: String, password: String)

    /** Calls the sign-up callable (consent is always true: the form requires it). Throws [AuthFailure]. */
    suspend fun signUp(request: SignUpRequest)

    fun signOut()
}
