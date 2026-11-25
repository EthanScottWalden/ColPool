package io.github.ethanscottwalden.colpool.ui.details

import androidx.annotation.StringRes
import io.github.ethanscottwalden.colpool.R
import io.github.ethanscottwalden.colpool.data.model.OptionItem
import io.github.ethanscottwalden.colpool.data.model.ProfileOptions
import io.github.ethanscottwalden.colpool.data.requests.DayFailureReason
import io.github.ethanscottwalden.colpool.data.requests.HostProfile
import io.github.ethanscottwalden.colpool.data.series.HostedSeries
import io.github.ethanscottwalden.colpool.ui.search.DayState
import io.github.ethanscottwalden.colpool.ui.search.MatchPreferences
import io.github.ethanscottwalden.colpool.ui.search.SearchRules
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday
import java.time.Instant

/** One optional host-card field that is filled in. Nothing is built for an empty one, so no "Not provided" ever shows. */
sealed interface HostField {
    /** [matched]: this value is what the rider asked for in Search (#37). */
    data class Text(@StringRes val label: Int, val value: String, val matched: Boolean = false) : HostField
    /** [matched]: the values (labels) that are among the rider's preferences. */
    data class Chips(@StringRes val label: Int, val values: List<String>, val matched: Set<String> = emptySet()) : HostField
}

/** Series Details rules (US-11, RC-32, RC-33). Pure, so JVM tests can check them. */
object DetailsRules {
    /** How each running weekday stands now: [DayState.Open], [DayState.Full] or [DayState.NoTripsLeft] (trips left is checked first, as in Search). */
    fun dayStates(s: HostedSeries, now: Instant): Map<SeriesWeekday, DayState> {
        val all = SeriesWeekday.entries.toSet()
        return SeriesWeekday.entries.filter { it.id in s.weekdayIds }.associateWith { SearchRules.dayState(s, it, all, now) }
    }

    /** A day can be picked when it is open and the server has not already refused it. RC-32 conflicts are only known from the server. */
    fun selectable(states: Map<SeriesWeekday, DayState>, refused: Set<SeriesWeekday>): Set<SeriesWeekday> =
        states.filter { (w, st) -> st == DayState.Open && w !in refused }.keys

    /**
     * The filled-in optional fields, in the profile screen's order. Ids without a seeded label are skipped.
     * With [prefs], matched interests, traits, a matching major and an exactly matching year are marked (partial year credit is not).
     */
    fun hostFields(p: HostProfile, o: ProfileOptions, prefs: MatchPreferences = MatchPreferences()): List<HostField> = buildList {
        label(o.years, p.year)?.let { add(HostField.Text(R.string.profile_year, it, matched = p.year == prefs.year)) }
        label(o.majors, p.major)?.let { add(HostField.Text(R.string.profile_major, it, matched = p.major == prefs.major)) }
        label(o.genders, p.gender)?.let { add(HostField.Text(R.string.profile_gender, it)) }
        p.age?.let { add(HostField.Text(R.string.profile_age, it.toString())) }
        chips(R.string.profile_interests, p.interests, o.interests, prefs.interests)?.let { add(it) }
        chips(R.string.profile_traits, p.traits, o.traits, prefs.traits)?.let { add(it) }
        p.aboutMe?.trim()?.ifEmpty { null }?.let { add(HostField.Text(R.string.profile_about_me, it)) }
    }

    private fun chips(@StringRes title: Int, ids: List<String>, list: List<OptionItem>, wanted: Set<String>): HostField.Chips? {
        val shown = ids.mapNotNull { id -> label(list, id)?.let { id to it } }
        if (shown.isEmpty()) return null
        return HostField.Chips(title, shown.map { it.second }, shown.filter { it.first in wanted }.map { it.second }.toSet())
    }

    private fun label(list: List<OptionItem>, id: String?): String? = id?.let { x -> list.firstOrNull { it.id == x }?.label }

    @StringRes
    fun failureText(r: DayFailureReason): Int = when (r) {
        DayFailureReason.Full -> R.string.details_reason_full
        DayFailureReason.NoTripsLeft -> R.string.details_reason_no_trips
        DayFailureReason.Conflict -> R.string.details_reason_conflict
    }
}

/** Where this rider stands with the series. The picker shows only for [Requestable]. */
enum class Standing { Requestable, Own, AlreadyRequested, Gone }

/** An error under the picker that is not about one day. */
enum class SendError { Invalid, Retry, OwnSeries, AlreadyRequested, Gone, SomeDays }
