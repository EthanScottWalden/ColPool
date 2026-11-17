package io.github.ethanscottwalden.colpool.ui.session

import io.github.ethanscottwalden.colpool.data.session.AuthBackend
import io.github.ethanscottwalden.colpool.data.session.AuthFailure
import io.github.ethanscottwalden.colpool.data.session.PrivateDetailsResult
import io.github.ethanscottwalden.colpool.data.session.SessionUser
import io.github.ethanscottwalden.colpool.data.session.SignUpRequest

/** Fake sources: set [user], then [details] or [detailsError]. Made-up uids only. */
class FakeAuthBackend : AuthBackend {
    var user: SessionUser? = null
    var details: PrivateDetailsResult = PrivateDetailsResult.Missing
    var detailsError: Exception? = null
    var tokenError: Exception? = null
    var signInError: AuthFailure? = null
    var signUpError: AuthFailure? = null
    var signOutCalls = 0
    var detailsReads = 0
    val signUps = mutableListOf<SignUpRequest>()
    val signIns = mutableListOf<Pair<String, String>>()

    override suspend fun currentUser(): SessionUser? {
        tokenError?.let { throw it }
        return user
    }

    override suspend fun readPrivateDetails(uid: String): PrivateDetailsResult {
        detailsReads++
        detailsError?.let { throw it }
        return details
    }

    override suspend fun signIn(email: String, password: String) {
        signInError?.let { throw it }
        signIns += email to password
    }

    override suspend fun signUp(request: SignUpRequest) {
        signUpError?.let { throw it }
        signUps += request
    }

    override fun signOut() {
        signOutCalls++
        user = null
    }
}
