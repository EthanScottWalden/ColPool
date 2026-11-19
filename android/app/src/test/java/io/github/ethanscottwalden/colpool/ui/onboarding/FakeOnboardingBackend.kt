package io.github.ethanscottwalden.colpool.ui.onboarding

import io.github.ethanscottwalden.colpool.data.model.OptionItem
import io.github.ethanscottwalden.colpool.data.model.ProfileOptions
import io.github.ethanscottwalden.colpool.data.session.OnboardingBackend
import io.github.ethanscottwalden.colpool.data.session.OnboardingSave

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
        val TEST_OPTIONS = ProfileOptions(
            genders = listOf(OptionItem("woman", "Woman"), OptionItem("man", "Man"), OptionItem("prefer-not-to-say", "Prefer not to say")),
            years = listOf(OptionItem("freshman", "Freshman"), OptionItem("sophomore", "Sophomore"), OptionItem("junior", "Junior")),
            majors = listOf(OptionItem("biology", "Biology"), OptionItem("computer-science", "Computer Science")),
            interests = listOf(OptionItem("music", "Music"), OptionItem("coffee", "Coffee"), OptionItem("hiking", "Hiking")),
            traits = listOf(OptionItem("chatty", "Chatty"), OptionItem("quiet", "Quiet")),
        )
    }
}
