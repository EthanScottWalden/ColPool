package io.github.ethanscottwalden.colpool.ui.search

import io.github.ethanscottwalden.colpool.data.search.HostMatchFields
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * What a rider would like in a host (US-10, RC-20): any number of interests and traits, at most one major and one year.
 * Option ids only. Never age or gender. Private to the device.
 */
data class MatchPreferences(
    val interests: Set<String> = emptySet(),
    val traits: Set<String> = emptySet(),
    val major: String? = null,
    val year: String? = null,
) {
    /** The number of chosen items: what the panel row shows and what the formula divides by. */
    val count: Int get() = interests.size + traits.size + (if (major != null) 1 else 0) + (if (year != null) 1 else 0)
    val isEmpty: Boolean get() = count == 0

    override fun toString(): String = "MatchPreferences(redacted)"
}

/** The match formula (INTENT §6.3, RC-21). Pure, so JVM tests can check it. Preferences never filter or reorder (RC-20). */
object MatchRules {
    /** Real year option ids in order; the step between two years is the distance between their places here. */
    val YEAR_ORDER = listOf("freshman", "sophomore", "junior", "senior", "graduate")
    private const val YEAR_STEP_LOSS = 0.34

    /** 1 for the same year, 0.66 one step away, 0.32 two, 0 from three. 0 when either year is missing or unknown. */
    fun yearScore(wanted: String, host: String?): Double {
        val a = YEAR_ORDER.indexOf(wanted)
        val b = host?.let { YEAR_ORDER.indexOf(it) } ?: -1
        if (a < 0 || b < 0) return 0.0
        return max(0.0, 1.0 - YEAR_STEP_LOSS * abs(a - b))
    }

    /**
     * Whole percent (half rounds up). Null when nothing is chosen, or when the host's profile could not be read
     * ([host] null): no percentage is shown. A readable profile with empty fields scores 0.
     */
    fun percent(prefs: MatchPreferences, host: HostMatchFields?): Int? {
        val items = prefs.count
        if (items == 0 || host == null) return null
        var total = 0.0
        total += prefs.interests.count { it in host.interests }
        total += prefs.traits.count { it in host.traits }
        if (prefs.major != null && prefs.major == host.major) total += 1.0
        if (prefs.year != null) total += yearScore(prefs.year, host.year)
        // The small epsilon keeps exact halves (such as 12.5) from falling below the line in floating point.
        return floor(total / items * 100.0 + 0.5 + 1e-9).toInt()
    }
}
