package io.github.ethanscottwalden.colpool.ui.onboarding

import io.github.ethanscottwalden.colpool.data.places.SelectedAddress
import io.github.ethanscottwalden.colpool.ui.profile.AgeParse
import io.github.ethanscottwalden.colpool.ui.profile.OptionsStatus
import io.github.ethanscottwalden.colpool.ui.profile.ProfileFieldRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val backend = FakeOnboardingBackend()
    private var completed = 0
    private val address = SelectedAddress("100 Example Street, Exampleville, CA 90000", "place-1", 12.5, -34.25)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = OnboardingViewModel(backend) { completed++ }.also { it.loadOptions() }

    @Test fun cannotFinishWithoutAddress() {
        val vm = vm()
        assertFalse(vm.state.value.canFinish)
        vm.finish()
        assertTrue(backend.saves.isEmpty())
        assertTrue(vm.state.value.showErrors)
        assertEquals(0, completed)
    }

    @Test fun addressAloneIsEnoughAndOptionalFieldsAreOmitted() {
        val vm = vm()
        vm.setAddress(address)
        assertTrue(vm.state.value.canFinish)
        vm.setAboutMe("   ")
        vm.finish()
        val saved = backend.saves.single()
        assertEquals(address, saved.address)
        assertNull(saved.age); assertNull(saved.gender); assertNull(saved.year); assertNull(saved.major)
        assertTrue(saved.interests.isEmpty()); assertTrue(saved.traits.isEmpty())
        assertNull(saved.aboutMe)
        assertEquals(1, completed)
    }

    @Test fun savesIdsNotLabelsInListOrder() {
        val vm = vm()
        vm.setAddress(address)
        vm.setAge("21")
        vm.setGender("woman"); vm.setYear("junior"); vm.setMajor("biology")
        vm.toggleInterest("hiking"); vm.toggleInterest("music")
        vm.toggleTrait("quiet")
        vm.setAboutMe("  Hi there  ")
        vm.finish()
        val s = backend.saves.single()
        assertEquals(21, s.age)
        assertEquals("woman", s.gender); assertEquals("junior", s.year); assertEquals("biology", s.major)
        assertEquals(listOf("music", "hiking"), s.interests)
        assertEquals(listOf("quiet"), s.traits)
        assertEquals("Hi there", s.aboutMe)
    }

    @Test fun ageRules() {
        assertEquals(AgeParse.Blank, ProfileFieldRules.parseAge(""))
        assertEquals(AgeParse.Valid(18), ProfileFieldRules.parseAge("18"))
        assertEquals(AgeParse.Valid(120), ProfileFieldRules.parseAge("120"))
        for (bad in listOf("17", "0", "18.5", "-20", "twenty", "1e2", "99999999999")) {
            assertEquals(bad, AgeParse.Invalid, ProfileFieldRules.parseAge(bad))
        }
    }

    @Test fun invalidAgeBlocksFinish() {
        val vm = vm()
        vm.setAddress(address)
        vm.setAge("17")
        assertTrue(vm.state.value.ageInvalid)
        assertFalse(vm.state.value.canFinish)
        vm.finish()
        assertTrue(backend.saves.isEmpty())
        vm.setAge("18")
        assertTrue(vm.state.value.canFinish)
    }

    @Test fun valuesNotInSeededListsAreIgnored() {
        val vm = vm()
        vm.setGender("Woman") // a label, not an id
        vm.setYear("senior-ish")
        vm.setMajor("astrology")
        vm.toggleInterest("politics")
        vm.toggleTrait("loud")
        val s = vm.state.value
        assertNull(s.gender); assertNull(s.year); assertNull(s.major)
        assertTrue(s.interests.isEmpty()); assertTrue(s.traits.isEmpty())
    }

    @Test fun singleChoiceCanBeCleared() {
        val vm = vm()
        vm.setGender("man")
        vm.setGender(null)
        assertNull(vm.state.value.gender)
        vm.toggleInterest("music"); vm.toggleInterest("music")
        assertTrue(vm.state.value.interests.isEmpty())
    }

    @Test fun aboutMeIsCappedAt300() {
        val vm = vm()
        vm.setAboutMe("a".repeat(300))
        assertEquals(300, vm.state.value.aboutMe.length)
        vm.setAboutMe("a".repeat(301))
        assertEquals(300, vm.state.value.aboutMe.length)
    }

    @Test fun saveFailureIsRetryableAndDoesNotComplete() {
        val vm = vm()
        vm.setAddress(address)
        backend.saveError = RuntimeException("network")
        vm.finish()
        assertTrue(vm.state.value.saveFailed)
        assertFalse(vm.state.value.saving)
        assertEquals(0, completed)
        backend.saveError = null
        vm.finish()
        assertFalse(vm.state.value.saveFailed)
        assertEquals(1, backend.saves.size)
        assertEquals(1, completed)
    }

    @Test fun optionsFailureStillAllowsFinishAndCanRetry() {
        backend.optionsError = RuntimeException("offline")
        val vm = vm()
        assertEquals(OptionsStatus.Failed, vm.state.value.optionsStatus)
        vm.setAddress(address)
        assertTrue(vm.state.value.canFinish)
        backend.optionsError = null
        vm.loadOptions()
        assertEquals(OptionsStatus.Loaded, vm.state.value.optionsStatus)
        assertEquals(FakeOnboardingBackend.TEST_OPTIONS, vm.state.value.options)
    }

    @Test fun resetClearsPrivateData() {
        val vm = vm()
        vm.setAddress(address)
        vm.setAboutMe("hi")
        vm.reset()
        assertEquals(OnboardingUiState(), vm.state.value)
    }

    @Test fun stateNeverPrintsTheAddress() {
        val vm = vm()
        vm.setAddress(address)
        assertFalse(vm.state.value.toString().contains("Example"))
    }
}
