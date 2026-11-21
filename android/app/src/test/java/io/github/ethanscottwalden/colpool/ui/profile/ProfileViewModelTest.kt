package io.github.ethanscottwalden.colpool.ui.profile

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
class ProfileViewModelTest {
    private val backend = FakeProfileBackend()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun loadsOwnProfileAndOptions() {
        val vm = ProfileViewModel(backend).also { it.load() }
        assertEquals(OptionsStatus.Loaded, vm.state.value.status)
        assertEquals(FakeProfileBackend.FULL, vm.state.value.profile)
        assertEquals(SeededOptions.ALL, vm.state.value.options)
    }

    @Test fun loadFailureCanRetry() {
        backend.loadError = RuntimeException("offline")
        val vm = ProfileViewModel(backend).also { it.load() }
        assertEquals(OptionsStatus.Failed, vm.state.value.status)
        assertNull(vm.state.value.profile)
        backend.loadError = null
        vm.load()
        assertEquals(OptionsStatus.Loaded, vm.state.value.status)
    }

    @Test fun optionsFailureIsALoadFailure() {
        backend.optionsError = RuntimeException("offline")
        val vm = ProfileViewModel(backend).also { it.load() }
        assertEquals(OptionsStatus.Failed, vm.state.value.status)
    }

    @Test fun appliedEditsShowAtOnce() {
        val vm = ProfileViewModel(backend).also { it.load() }
        vm.applySaved(FakeProfileBackend.FULL.copy(firstName = "Casey"))
        assertEquals("Casey", vm.state.value.profile?.firstName)
        assertEquals(OptionsStatus.Loaded, vm.state.value.status)
    }

    @Test fun resetForgetsPrivateData() {
        val vm = ProfileViewModel(backend).also { it.load() }
        vm.reset()
        assertEquals(ProfileUiState(), vm.state.value)
    }

    @Test fun stateNeverPrintsAddressOrEmail() {
        val vm = ProfileViewModel(backend).also { it.load() }
        val text = vm.state.value.toString() + vm.state.value.profile.toString()
        assertFalse(text.contains("Example Street")); assertFalse(text.contains("calbaptist"))
    }
}
