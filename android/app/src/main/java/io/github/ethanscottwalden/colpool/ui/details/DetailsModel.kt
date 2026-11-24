package io.github.ethanscottwalden.colpool.ui.details

import androidx.annotation.StringRes
import io.github.ethanscottwalden.colpool.R
import io.github.ethanscottwalden.colpool.data.model.OptionItem
import io.github.ethanscottwalden.colpool.data.model.ProfileOptions
import io.github.ethanscottwalden.colpool.data.requests.DayFailureReason
import io.github.ethanscottwalden.colpool.data.requests.HostProfile
import io.github.ethanscottwalden.colpool.data.series.HostedSeries
import io.github.ethanscottwalden.colpool.ui.search.DayState
import io.github.ethanscottwalden.colpool.ui.search.SearchRules
import io.github.ethanscottwalden.colpool.ui.series.SeriesWeekday
import java.time.Instant

/** One optional host-card field that is filled in. Nothing is built for an empty one, so no "Not provided" ever shows. */
sealed interface HostField {
    data class Text(@StringRes val label: Int, val value: String) : HostField
    data class Chips(@StringRes val label: Int, val values: List<String>) : HostField
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

    /** The filled-in optional fields, in the profile screen's order. Ids without a seeded label are skipped. */
    fun hostFields(p: HostProfile, o: ProfileOptions): List<HostField> = buildList {
        label(o.years, p.year)?.let { add(HostField.Text(R.string.profile_year, it)) }
        label(o.majors, p.major)?.let { add(HostField.Text(R.string.profile_major, it)) }
        label(o.genders, p.gender)?.let { add(HostField.Text(R.string.profile_gender, it)) }
        p.age?.let { add(HostField.Text(R.string.profile_age, it.toString())) }
        p.interests.mapNotNull { label(o.interests, it) }.takeIf { it.isNotEmpty() }?.let { add(HostField.Chips(R.string.profile_interests, it)) }
        p.traits.mapNotNull { label(o.traits, it) }.takeIf { it.isNotEmpty() }?.let { add(HostField.Chips(R.string.profile_traits, it)) }
        p.aboutMe?.trim()?.ifEmpty { null }?.let { add(HostField.Text(R.string.profile_about_me, it)) }
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
