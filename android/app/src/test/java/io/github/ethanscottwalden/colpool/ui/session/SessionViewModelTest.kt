package io.github.ethanscottwalden.colpool.ui.session

import io.github.ethanscottwalden.colpool.data.session.PrivateDetailsResult
import io.github.ethanscottwalden.colpool.data.session.SessionUser
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {
    private val backend = FakeAuthBackend()
    private val student = SessionUser("student-1", isStaff = false)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = SessionViewModel(backend)

    @Test fun signedOutGoesToSignIn() {
        assertEquals(SessionState.SignedOut(), vm().state.value)
    }

    @Test fun staffClaimGoesToStaffWithoutReadingPrivateDetails() {
        backend.user = SessionUser("staff-1", isStaff = true)
        assertEquals(SessionState.Staff, vm().state.value)
        assertEquals(0, backend.detailsReads)
    }

    @Test fun noConsentSignsOutWithNotice() {
        backend.user = student
        backend.details = PrivateDetailsResult.NoConsent
        assertEquals(SessionState.SignedOut(SignedOutNotice.NoConsent), vm().state.value)
        assertEquals(1, backend.signOutCalls)
    }

    @Test fun missingDocGoesToOnboarding() {
        backend.user = student
        backend.details = PrivateDetailsResult.Missing
        assertEquals(SessionState.Onboarding, vm().state.value)
    }

    @Test fun startingAddressGoesToSearch() {
        backend.user = student
        backend.details = PrivateDetailsResult.HasStartingAddress
        assertEquals(SessionState.Search, vm().state.value)
    }

    @Test fun networkErrorGoesToRetryAndNeverSignsOut() {
        backend.user = student
        backend.detailsError = IOException("offline")
        val vm = vm()
        assertEquals(SessionState.Error, vm.state.value)
        assertEquals(0, backend.signOutCalls)
    }

    @Test fun tokenErrorGoesToRetry() {
        backend.user = student
        backend.tokenError = IOException("offline")
        assertEquals(SessionState.Error, vm().state.value)
    }

    @Test fun retryRecoversAfterNetworkReturns() = runTest {
        backend.user = student
        backend.detailsError = IOException("offline")
        val vm = vm()
        backend.detailsError = null
        backend.details = PrivateDetailsResult.HasStartingAddress
        vm.retry()
        assertEquals(SessionState.Search, vm.state.value)
    }

    @Test fun logOutGoesToSignIn() {
        backend.user = student
        backend.details = PrivateDetailsResult.HasStartingAddress
        val vm = vm()
        vm.logOut()
        assertEquals(SessionState.SignedOut(), vm.state.value)
        assertEquals(1, backend.signOutCalls)
    }
}
