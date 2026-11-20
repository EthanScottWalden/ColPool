package io.github.ethanscottwalden.colpool.ui.profile

import io.github.ethanscottwalden.colpool.data.model.OwnProfile
import io.github.ethanscottwalden.colpool.data.model.ProfileOptions
import io.github.ethanscottwalden.colpool.data.places.SelectedAddress
import io.github.ethanscottwalden.colpool.data.session.ProfileBackend
import io.github.ethanscottwalden.colpool.data.session.ProfileChanges

/** Fake backend with made-up data. */
class FakeProfileBackend(var profile: OwnProfile = FULL) : ProfileBackend {
    var loadError: Exception? = null
    var optionsError: Exception? = null
    var saveError: Exception? = null
    val saves = mutableListOf<ProfileChanges>()

    override suspend fun loadProfile(): OwnProfile {
        loadError?.let { throw it }
        return profile
    }

    override suspend fun loadOptions(): ProfileOptions {
        optionsError?.let { throw it }
        return SeededOptions.ALL
    }

    override suspend fun save(changes: ProfileChanges) {
        saveError?.let { throw it }
        saves += changes
    }

    companion object {
        val ADDRESS = SelectedAddress("100 Example Street, Exampleville, CA 90000", "place-1", 12.5, -34.25)
        val FULL = OwnProfile(
            firstName = "Jordan", lastName = "Example", email = "jexample@calbaptist.edu", address = ADDRESS,
            age = 20, gender = "woman", year = "junior", major = "computer-science",
            interests = listOf("music", "coffee", "gaming"), traits = listOf("friendly", "music-friendly"),
            aboutMe = "Commuting in on weekday mornings. Usually have a podcast or playlist going.",
        )
        val MINIMAL = OwnProfile(firstName = "Sam", lastName = "Sample", email = "ssample@calbaptist.edu", address = ADDRESS)
    }
}
