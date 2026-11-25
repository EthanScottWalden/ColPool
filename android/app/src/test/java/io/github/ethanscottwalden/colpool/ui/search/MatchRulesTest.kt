package io.github.ethanscottwalden.colpool.ui.search

import io.github.ethanscottwalden.colpool.data.search.HostMatchFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The match formula (INTENT 6.3, RC-21) on made-up hosts. */
class MatchRulesTest {
    private val host = HostMatchFields("Ava", year = "junior", major = "nursing", interests = listOf("music", "coffee"), traits = listOf("chatty"))

    @Test fun noPreferencesMeansNoPercentageAtAll() {
        assertNull(MatchRules.percent(MatchPreferences(), host))
        assertNull(MatchRules.percent(MatchPreferences(), null))
        assertEquals(0, MatchPreferences().count)
    }

    @Test fun everythingMatchingIs100() {
        val p = MatchPreferences(setOf("music", "coffee"), setOf("chatty"), "nursing", "junior")
        assertEquals(5, p.count)
        assertEquals(100, MatchRules.percent(p, host))
    }

    @Test fun nothingMatchingIs0NotNull() {
        val p = MatchPreferences(setOf("gaming"), setOf("quiet"), "biology", "graduate")
        assertEquals(0, MatchRules.percent(p.copy(year = "graduate"), host.copy(year = "freshman")))
    }

    @Test fun yearCreditFallsByTheStep() {
        fun pct(y: String) = MatchRules.percent(MatchPreferences(year = y), host)
        assertEquals(100, pct("junior"))
        assertEquals(66, pct("sophomore"))
        assertEquals(66, pct("senior"))
        assertEquals(32, pct("freshman"))
        assertEquals(32, pct("graduate"))
        // Three steps and more floor at 0.
        assertEquals(0, MatchRules.percent(MatchPreferences(year = "graduate"), host.copy(year = "freshman")))
        assertEquals(0, MatchRules.percent(MatchPreferences(year = "freshman"), host.copy(year = "senior")))
        assertEquals(0, MatchRules.percent(MatchPreferences(year = "freshman"), host.copy(year = "graduate")))
    }

    @Test fun hostMissingFieldsScoresZeroForThem() {
        val bare = HostMatchFields("Riley")
        val p = MatchPreferences(setOf("music"), setOf("chatty"), "nursing", "junior")
        assertEquals(0, MatchRules.percent(p, bare))
        // An unreadable profile (null) is not a score of 0: no percentage.
        assertNull(MatchRules.percent(p, null))
        // A host that fills only some: 2 of 4 items.
        assertEquals(50, MatchRules.percent(p, HostMatchFields("X", interests = listOf("music"), traits = listOf("chatty"))))
    }

    @Test fun roundsHalfUp() {
        // 2 of 3 -> 66.67 -> 67
        assertEquals(67, MatchRules.percent(MatchPreferences(setOf("music", "coffee", "gaming")), host))
        // 1 of 8 -> 12.5 -> 13
        val eight = MatchPreferences(setOf("music", "a", "b", "c", "d", "e", "f", "g"))
        assertEquals(13, MatchRules.percent(eight, host))
        // 1 of 3 -> 33.33 -> 33
        assertEquals(33, MatchRules.percent(MatchPreferences(setOf("music", "x", "y")), host))
    }

    @Test fun partialYearCombinesWithTheOtherItems() {
        // music (1) + year one step off (0.66) over 2 items = 83%.
        assertEquals(83, MatchRules.percent(MatchPreferences(setOf("music"), year = "senior"), host))
    }

    @Test fun unknownYearIdsScoreZero() {
        assertEquals(0, MatchRules.percent(MatchPreferences(year = "alumnus"), host))
        assertEquals(0, MatchRules.percent(MatchPreferences(year = "junior"), host.copy(year = "alumnus")))
    }

    @Test fun yearOrderMatchesTheSeededOptions() {
        assertEquals(io.github.ethanscottwalden.colpool.ui.profile.SeededOptions.ALL.years.map { it.id }, MatchRules.YEAR_ORDER)
    }
}
