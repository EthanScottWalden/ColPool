package io.github.ethanscottwalden.colpool.ui.onboarding

import io.github.ethanscottwalden.colpool.data.model.ProfileOptions
import io.github.ethanscottwalden.colpool.data.session.OnboardingBackend
import io.github.ethanscottwalden.colpool.data.session.OnboardingSave
import io.github.ethanscottwalden.colpool.ui.profile.SeededOptions

/** Fake backend with made-up option lists. */
class FakeOnboardingBackend : OnboardingBackend {
    var optionsError: Exception? = null
    var saveError: Exception? = null
    val saves = mutableListOf<OnboardingSave>()

    override suspend fun loadOptions(): ProfileOptions {
        optionsError?.let { throw it }
        return TEST_OPTIONS
    }

    override suspend fun save(data: OnboardingSave) {
        saveError?.let { throw it }
        saves += data
    }

    companion object {
        /** The full seeded lists, as users see them. */
        val TEST_OPTIONS = SeededOptions.ALL
    }
}
