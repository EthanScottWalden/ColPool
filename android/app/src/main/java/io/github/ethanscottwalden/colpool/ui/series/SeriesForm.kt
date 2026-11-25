package io.github.ethanscottwalden.colpool.ui.series

import androidx.annotation.StringRes
import io.github.ethanscottwalden.colpool.R
import io.github.ethanscottwalden.colpool.data.places.SelectedAddress
import io.github.ethanscottwalden.colpool.data.series.CampusPoint
import io.github.ethanscottwalden.colpool.data.series.SeriesError
import io.github.ethanscottwalden.colpool.ui.profile.OptionsStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** RC-30 limits for series input (`SERIES_LIMITS` in functions/src/shared/schema/limits.ts). */
object SeriesLimits {
    const val PRICE_MIN = 1
    const val PRICE_MAX = 99
    const val RIDERS_MIN = 1
    const val RIDERS_MAX = 6
    const val VEHICLE_MAX = 60
    const val STOP_MINUTES_MIN = 0
    const val STOP_MINUTES_MAX = 120

    /**
     * Owner decision 2026-11-23: 10 stops per direction. The backend matches since contract-change Issue #143
     * (`SERIES_LIMITS.maxStopsPerDirection`, now 10); keep the two equal.
     */
    const val MAX_STOPS_PER_DIRECTION = 10

    /** Active hosted series per student (RC-31). Used only when the backend omits the limit. */
    const val MAX_ACTIVE_HOSTED = 5
}

/** Monday to Friday only (owner decision 2026-11-23). Declared Monday-first, the order the backend expects. */
enum class SeriesWeekday(
    val id: String,
    @StringRes val shortName: Int,
    @StringRes val fullName: Int,
    val dayOfWeek: DayOfWeek,
) {
    Mon("mon", R.string.weekday_mon_short, R.string.weekday_mon_full, DayOfWeek.MONDAY),
    Tue("tue", R.string.weekday_tue_short, R.string.weekday_tue_full, DayOfWeek.TUESDAY),
    Wed("wed", R.string.weekday_wed_short, R.string.weekday_wed_full, DayOfWeek.WEDNESDAY),
    Thu("thu", R.string.weekday_thu_short, R.string.weekday_thu_full, DayOfWeek.THURSDAY),
    Fri("fri", R.string.weekday_fri_short, R.string.weekday_fri_full, DayOfWeek.FRIDAY);

    companion object {
        fun fromId(id: String?): SeriesWeekday? = entries.firstOrNull { it.id == id }
    }
}

enum class Direction(val id: String) {
    ToCampus("toCampus"),
    FromCampus("fromCampus");

    companion object {
        fun fromId(id: String?): Direction? = entries.firstOrNull { it.id == id }
    }
}

data class StopForm(val address: SelectedAddress, val minutes: Int) {
    override fun toString(): String = "StopForm(redacted)"
}

/** One direction on one weekday. [departMinutes] is minutes after local midnight (Pacific). */
data class LegForm(
    val departMinutes: Int? = null,
    val campusPointId: String? = null,
    val stops: List<StopForm> = emptyList(),
) {
    val complete: Boolean get() = departMinutes != null && campusPointId != null
}

data class DayForm(val toCampus: LegForm = LegForm(), val fromCampus: LegForm = LegForm()) {
    val complete: Boolean get() = toCampus.complete && fromCampus.complete
    fun leg(d: Direction): LegForm = if (d == Direction.ToCampus) toCampus else fromCampus
    fun withLeg(d: Direction, leg: LegForm): DayForm = if (d == Direction.ToCampus) copy(toCampus = leg) else copy(fromCampus = leg)
}

/** Holds addresses: never log it. Dates are ISO `YYYY-MM-DD` (Pacific calendar dates). */
data class CreateSeriesUiState(
    val campusStatus: OptionsStatus = OptionsStatus.Loading,
    val campusPoints: List<CampusPoint> = emptyList(),
    val pickup: SelectedAddress? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val price: String = "",
    val riders: Int = SeriesLimits.RIDERS_MIN,
    val vehicle: String = "",
    val weekdays: Set<SeriesWeekday> = emptySet(),
    val days: Map<SeriesWeekday, DayForm> = emptyMap(),
    val activeDay: SeriesWeekday? = null,
    val errors: List<SeriesError> = emptyList(),
    val submitting: Boolean = false,
    /** The draft the last successful route preview saved; the next Continue overwrites it (ADR 0008). Never shown. */
    val draftId: String? = null,
    /** Set when route preview succeeded; the screen navigates to Review and calls `consumeReview`. */
    val reviewDraftId: String? = null,
) {
    /** Selected weekdays, Monday first. */
    val orderedWeekdays: List<SeriesWeekday> get() = SeriesWeekday.entries.filter { it in weekdays }
    fun day(w: SeriesWeekday): DayForm = days[w] ?: DayForm()
    fun errorsAt(path: String): List<SeriesError> = errors.filter { it.path == path }

    override fun toString(): String = "CreateSeriesUiState(redacted)"
}

/** Times are entered and shown in 12-hour Pacific (RC-18) and sent as 24-hour `HH:mm`. */
object SeriesTime {
    fun to24h(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    /** `7:30 AM`; 0 is `12:00 AM` and 720 is `12:00 PM`. */
    fun format12(minutes: Int): String {
        val h24 = minutes / 60
        val h12 = if (h24 % 12 == 0) 12 else h24 % 12
        return "%d:%02d %s".format(h12, minutes % 60, if (h24 < 12) "AM" else "PM")
    }

    /** From the picker's 24-hour hour and minute. */
    fun fromPicker(hour24: Int, minute: Int): Int = hour24 * 60 + minute
}

object SeriesDates {
    /** `MM/DD/YYYY` from an ISO date. */
    fun display(iso: String): String {
        val d = LocalDate.parse(iso)
        return "%02d/%02d/%04d".format(d.monthValue, d.dayOfMonth, d.year)
    }

    /** The date a Material date picker returns (UTC midnight millis) as an ISO date. */
    fun fromPickerMillis(utcMillis: Long): String = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate().toString()

    fun toPickerMillis(iso: String): Long = LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}
