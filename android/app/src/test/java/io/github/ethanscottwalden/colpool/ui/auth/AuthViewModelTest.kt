package io.github.ethanscottwalden.colpool.ui.auth

import io.github.ethanscottwalden.colpool.data.session.AuthFailure
import io.github.ethanscottwalden.colpool.data.session.AuthFailureKind
import com.google.firebase.firestore.FirebaseFirestoreException
import io.github.ethanscottwalden.colpool.data.session.FirebaseAuthBackend
import io.github.ethanscottwalden.colpool.data.session.PrivateDetailsResult
import io.github.ethanscottwalden.colpool.data.session.SessionUser
import io.github.ethanscottwalden.colpool.ui.session.SessionState
import io.github.ethanscottwalden.colpool.ui.session.SessionViewModel
import io.github.ethanscottwalden.colpool.ui.session.SignedOutNotice
import io.github.ethanscottwalden.colpool.ui.session.FakeAuthBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val backend = FakeAuthBackend()
    private var routed = 0
    private fun vm() = AuthViewModel(backend) { routed++ }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun AuthViewModel.fillSignUp(consent: Boolean = true) {
        setMode(AuthMode.SignUp)
        setFirstName("Jordan"); setLastName("Lee"); setEmail(" JLee@calbaptist.edu "); setPassword("password1"); setConsent(consent)
    }

    @Test fun signInCallsBackendThenRoutes() {
        val vm = vm()
        vm.setEmail("jlee@calbaptist.edu"); vm.setPassword("pw")
        vm.submit()
        assertEquals(listOf("jlee@calbaptist.edu" to "pw"), backend.signIns)
        assertEquals(1, routed)
        assertFalse(vm.state.value.busy)
    }

    @Test fun signInFailureShowsErrorAndDoesNotRoute() {
        backend.signInError = AuthFailure(AuthFailureKind.InvalidCredentials)
        val vm = vm()
        vm.setEmail("a@calbaptist.edu"); vm.setPassword("pw")
        vm.submit()
        assertEquals(AuthFailureKind.InvalidCredentials, vm.state.value.formError)
        assertEquals(0, routed)
    }

    @Test fun blankSignInDoesNotCallBackend() {
        val vm = vm()
        vm.submit()
        assertEquals(AuthFailureKind.FieldsRequired, vm.state.value.formError)
        assertEquals(0, backend.signIns.size)
    }

    @Test fun signUpWithoutConsentIsBlockedAndShowsError() {
        val vm = vm()
        vm.fillSignUp(consent = false)
        vm.submit()
        assertEquals(setOf(SignUpFieldError.Consent), vm.state.value.fieldErrors)
        assertEquals(0, backend.signUps.size)
    }

    @Test fun signUpNormalizesThenSignsIn() {
        val vm = vm()
        vm.fillSignUp()
        vm.submit()
        assertEquals("jlee@calbaptist.edu", backend.signUps.single().email)
        assertEquals(1, backend.signIns.size)
        assertEquals(1, routed)
    }

    @Test fun signUpServerReasonIsShown() {
        backend.signUpError = AuthFailure(AuthFailureKind.EmailInUse)
        val vm = vm()
        vm.fillSignUp()
        vm.submit()
        assertEquals(AuthFailureKind.EmailInUse, vm.state.value.formError)
        assertEquals(0, routed)
    }

    @Test fun resetClearsForm() {
        val vm = vm()
        vm.fillSignUp()
        vm.reset()
        assertEquals(AuthUiState(), vm.state.value)
        assertNull(vm.state.value.formError)
    }

    @Test fun reasonsMapToKinds() {
        val m = FirebaseAuthBackend.Companion::signUpKindForReason
        assertEquals(AuthFailureKind.EmailDomain, m("email-domain"))
        assertEquals(AuthFailureKind.PasswordTooShort, m("password-too-short"))
        assertEquals(AuthFailureKind.FirstNameInvalid, m("first-name-invalid"))
        assertEquals(AuthFailureKind.LastNameInvalid, m("last-name-invalid"))
        assertEquals(AuthFailureKind.ConsentRequired, m("consent-required"))
        assertEquals(AuthFailureKind.EmailInUse, m("email-in-use"))
        assertEquals(AuthFailureKind.Internal, m("internal"))
        assertEquals(AuthFailureKind.Unknown, m(null))
    }

    @Test fun signUpThenSignInFailureSwitchesToSignInKeepingEmail() {
        backend.signInError = AuthFailure(AuthFailureKind.Unknown)
        val vm = vm()
        vm.fillSignUp()
        vm.submit()
        val s = vm.state.value
        assertEquals(1, backend.signUps.size)
        assertEquals(AuthMode.SignIn, s.mode)
        assertEquals("jlee@calbaptist.edu", s.email)
        assertEquals("", s.password)
        assertEquals(AuthFailureKind.AccountCreatedSignIn, s.formError)
        assertEquals(0, routed)
    }

    @Test fun signUpTimeoutSwitchesToSignIn() {
        backend.signUpError = AuthFailure(AuthFailureKind.CouldNotConfirm)
        val vm = vm()
        vm.fillSignUp()
        vm.submit()
        val s = vm.state.value
        assertEquals(AuthMode.SignIn, s.mode)
        assertEquals("jlee@calbaptist.edu", s.email)
        assertEquals(AuthFailureKind.CouldNotConfirm, s.formError)
        assertEquals(0, backend.signIns.size)
    }

    @Test fun signInThenNoConsentClearsPasswordKeepsEmail() {
        backend.userAfterSignIn = SessionUser("s1", isStaff = false)
        backend.details = PrivateDetailsResult.NoConsent
        val session = SessionViewModel(backend)
        val vm = AuthViewModel(backend) { session.resolve() }
        vm.setEmail("jlee@calbaptist.edu"); vm.setPassword("password1")
        vm.submit()
        assertEquals(SessionState.SignedOut(SignedOutNotice.NoConsent), session.state.value)
        val s = vm.state.value
        assertEquals("", s.password)
        assertEquals("jlee@calbaptist.edu", s.email)
        assertFalse(s.busy)
        assertNull(s.formError)
    }
}
