package io.github.ethanscottwalden.colpool.ui.profile

import io.github.ethanscottwalden.colpool.data.model.OwnProfile
import io.github.ethanscottwalden.colpool.data.places.SelectedAddress
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
class EditProfileViewModelTest {
    private val backend = FakeProfileBackend()
    private val saved = mutableListOf<OwnProfile>()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(profile: OwnProfile = FakeProfileBackend.FULL) =
        EditProfileViewModel(backend) { saved += it }.also { it.start(profile, SeededOptions.ALL) }

    @Test fun startsFilledAndUnchanged() {
        val s = vm().state.value
        assertTrue(s.ready)
        assertEquals("Jordan", s.firstName); assertEquals("20", s.age)
        assertEquals(setOf("music", "coffee", "gaming"), s.interests)
        assertFalse(s.invalid)
        assertFalse(s.canSave)
        assertTrue(s.changes!!.isEmpty)
    }

    @Test fun saveDoesNothingWhenUnchanged() {
        val vm = vm()
        vm.save()
        assertTrue(backend.saves.isEmpty())
    }

    @Test fun nameChangeIsTrimmedAndSavedAlone() {
        val vm = vm()
        vm.setFirstName("  Casey ")
        assertTrue(vm.state.value.canSave)
        vm.save()
        val c = backend.saves.single()
        assertEquals(mapOf<String, Any>("firstName" to "Casey"), c.set)
        assertTrue(c.clear.isEmpty()); assertNull(c.newAddress)
        assertEquals("Casey", saved.single().firstName)
        assertFalse(vm.state.value.ready) // form closed
    }

    @Test fun blankNamesCannotBeSaved() {
        for (bad in listOf("", "   ", " ")) {
            val vm = vm()
            vm.setFirstName(bad)
            assertTrue(bad, vm.state.value.firstNameInvalid)
            assertFalse(vm.state.value.canSave)
            vm.save()
            vm.setFirstName("Jordan"); vm.setLastName(bad)
            assertTrue(vm.state.value.lastNameInvalid)
            vm.save()
        }
        assertTrue(backend.saves.isEmpty())
    }

    @Test fun nameIsCappedAtThirtyCharacters() {
        val vm = vm()
        vm.setFirstName("a".repeat(31))
        assertEquals(30, vm.state.value.firstName.length)
    }

    @Test fun removedOptionalFieldsBecomeClearsNeverEmptyValues() {
        val vm = vm()
        vm.setAge(""); vm.setGender(null); vm.setYear(null); vm.setMajor(null)
        vm.toggleInterest("music"); vm.toggleInterest("coffee"); vm.toggleInterest("gaming")
        vm.toggleTrait("friendly"); vm.toggleTrait("music-friendly")
        vm.setAboutMe("   ")
        vm.save()
        val c = backend.saves.single()
        assertTrue(c.set.isEmpty())
        assertEquals(setOf("age", "gender", "year", "major", "interests", "traits", "aboutMe"), c.clear)
        val s = saved.single()
        assertNull(s.age); assertNull(s.gender); assertNull(s.aboutMe)
        assertTrue(s.interests.isEmpty())
    }

    @Test fun addingAndChangingOptionalFields() {
        val vm = vm(FakeProfileBackend.MINIMAL)
        vm.setAge("21"); vm.setGender("man"); vm.setMajor("biology")
        vm.toggleInterest("hiking"); vm.toggleInterest("music")
        vm.setAboutMe(" hi ")
        vm.save()
        val c = backend.saves.single()
        assertEquals(
            mapOf<String, Any>("age" to 21, "gender" to "man", "major" to "biology", "interests" to listOf("music", "hiking"), "aboutMe" to "hi"),
            c.set,
        )
        assertTrue(c.clear.isEmpty())
    }

    @Test fun removingOnlyOneInterestRewritesTheList() {
        val vm = vm()
        vm.toggleInterest("coffee")
        vm.save()
        assertEquals(mapOf<String, Any>("interests" to listOf("music", "gaming")), backend.saves.single().set)
    }

    @Test fun addressChangeWritesPrivateDetailsOnly() {
        val vm = vm()
        val next = SelectedAddress("200 Sample Road, Exampleville, CA 90001", "place-2", 1.0, 2.0)
        vm.setAddress(next)
        vm.save()
        val c = backend.saves.single()
        assertEquals(next, c.newAddress)
        assertTrue(c.set.isEmpty() && c.clear.isEmpty())
        assertEquals(next, saved.single().address)
    }

    @Test fun unchangedAddressIsNotRewritten() {
        val vm = vm()
        vm.setAddress(FakeProfileBackend.ADDRESS.copy())
        vm.setAboutMe("changed")
        vm.save()
        assertNull(backend.saves.single().newAddress)
    }

    @Test fun invalidAgeBlocksSave() {
        val vm = vm()
        vm.setAge("17")
        assertTrue(vm.state.value.ageInvalid)
        assertFalse(vm.state.value.canSave)
        vm.save()
        assertTrue(backend.saves.isEmpty())
        vm.setAge("18")
        assertTrue(vm.state.value.canSave)
    }

    @Test fun valuesNotInSeededListsAreIgnored() {
        val vm = vm()
        vm.setGender("Woman"); vm.setMajor("astrology"); vm.toggleTrait("loud")
        val s = vm.state.value
        assertNull(s.gender); assertNull(s.major)
        assertEquals(setOf("friendly", "music-friendly"), s.traits)
    }

    @Test fun aboutMeIsCappedAt300() {
        val vm = vm()
        vm.setAboutMe("a".repeat(300)); vm.setAboutMe("a".repeat(301))
        assertEquals(300, vm.state.value.aboutMe.length)
    }

    @Test fun saveFailureIsRetryableAndKeepsTheForm() {
        val vm = vm()
        vm.setFirstName("Casey")
        backend.saveError = RuntimeException("network")
        vm.save()
        assertTrue(vm.state.value.saveFailed)
        assertFalse(vm.state.value.saving)
        assertTrue(vm.state.value.ready)
        assertTrue(saved.isEmpty())
        backend.saveError = null
        vm.save()
        assertEquals(1, backend.saves.size)
        assertEquals(1, saved.size)
    }

    @Test fun aSaveFinishingAfterResetIsIgnored() {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val slow = object : io.github.ethanscottwalden.colpool.data.session.ProfileBackend by backend {
            override suspend fun save(changes: io.github.ethanscottwalden.colpool.data.session.ProfileChanges) { gate.await(); backend.save(changes) }
        }
        val vm = EditProfileViewModel(slow) { saved += it }.also { it.start(FakeProfileBackend.FULL, SeededOptions.ALL) }
        vm.setFirstName("Casey")
        vm.save()
        assertTrue(vm.state.value.saving)
        vm.reset() // e.g. log out
        gate.complete(Unit)
        assertTrue(saved.isEmpty())
        assertEquals(EditProfileUiState(), vm.state.value)
    }

    @Test fun resetClearsPrivateData() {
        val vm = vm()
        vm.reset()
        assertEquals(EditProfileUiState(), vm.state.value)
    }

    @Test fun stateNeverPrintsTheAddress() {
        val vm = vm()
        assertFalse(vm.state.value.toString().contains("Example"))
    }
}
