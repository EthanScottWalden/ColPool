package io.github.ethanscottwalden.colpool.ui.series

import io.github.ethanscottwalden.colpool.R
import io.github.ethanscottwalden.colpool.data.series.SeriesError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesErrorTextTest {
    private fun text(code: String, path: String = "", vararg p: Pair<String, Any?>) = SeriesErrorText.of(SeriesError(code, path, mapOf(*p)))

    @Test fun everyBackendCodeHasItsOwnText() {
        assertEquals(UiText(R.string.series_err_required), text("required", "vehicle"))
        assertEquals(UiText(R.string.series_err_weekdays_required), text("required", "weekdays"))
        assertEquals(UiText(R.string.series_err_not_integer), text("notInteger", "priceDollars"))
        assertEquals(UiText(R.string.series_err_price_range, listOf(1, 99)), text("outOfRange", "priceDollars", "min" to 1, "max" to 99))
        assertEquals(UiText(R.string.series_err_riders_range, listOf(1, 6)), text("outOfRange", "maxRiders", "min" to 1, "max" to 6))
        assertEquals(UiText(R.string.series_err_stop_minutes_range, listOf(0, 120)), text("outOfRange", "days.mon.toCampus.stops.0.minutes", "min" to 0, "max" to 120))
        assertEquals(UiText(R.string.series_err_too_long, listOf(60)), text("tooLong", "vehicle", "limit" to 60))
        assertEquals(UiText(R.string.series_err_invalid_address), text("invalidAddress", "pickupSpot"))
        assertEquals(UiText(R.string.series_err_invalid_date), text("invalidDate", "startDate"))
        assertEquals(UiText(R.string.series_err_invalid_time), text("invalidTime", "days.mon.toCampus.departTime"))
        assertEquals(UiText(R.string.series_err_unknown_campus_point), text("unknownCampusPoint", "days.mon.toCampus.campusPointId"))
        // The app's cap is 10 even when the backend still says 25.
        assertEquals(UiText(R.string.series_err_too_many_stops, listOf(10)), text("tooManyStops", "days.mon.toCampus.stops", "limit" to 25))
        assertEquals(UiText(R.string.series_err_duplicate_weekday, listOf(StringRef(R.string.weekday_tue_full))), text("duplicateWeekday", "weekdays", "weekday" to "tue"))
        assertEquals(UiText(R.string.series_err_start_in_past), text("startInPast", "startDate"))
        assertEquals(UiText(R.string.series_err_end_before_start), text("endBeforeStart", "endDate"))
        assertEquals(UiText(R.string.series_err_no_date_on_weekdays), text("noDateOnWeekdays"))
        assertEquals(UiText(R.string.series_err_campus_departure_before_arrival, listOf(StringRef(R.string.weekday_wed_full))), text("campusDepartureBeforeArrival", "days.wed.fromCampus.departTime", "weekday" to "wed"))
        assertEquals(UiText(R.string.series_err_hosting_limit, listOf(5)), text("hostingLimitReached", "", "limit" to 5))
        assertEquals(UiText(R.string.series_err_hosting_overlap, listOf(StringRef(R.string.weekday_fri_full))), text("hostingWeekdayOverlap", "days.fri", "weekday" to "fri"))
        assertEquals(UiText(R.string.series_err_rider_conflict, listOf(StringRef(R.string.weekday_mon_full))), text("riderScheduleConflict", "days.mon", "weekday" to "mon"))
        assertEquals(UiText(R.string.series_err_unavailable), text("unavailable"))
        assertEquals(UiText(R.string.series_err_unknown), text("internal"))
        assertEquals(UiText(R.string.series_err_unknown), text("somethingNew", "pickupSpot"))
    }

    @Test fun routeNotFoundDependsOnWhereItPoints() {
        assertEquals(UiText(R.string.series_err_route_not_found_address), text("routeNotFound", "pickupSpot"))
        assertEquals(UiText(R.string.series_err_route_not_found_address), text("routeNotFound", "days.tue.toCampus.stops.1.address"))
        assertEquals(
            UiText(R.string.series_err_route_not_found_leg, listOf(StringRef(R.string.weekday_tue_full), StringRef(R.string.series_direction_to_campus_phrase))),
            text("routeNotFound", "days.tue.toCampus"),
        )
        assertEquals(
            UiText(R.string.series_err_route_not_found_leg, listOf(StringRef(R.string.weekday_thu_full), StringRef(R.string.series_direction_from_campus_phrase))),
            text("routeNotFound", "days.thu.fromCampus"),
        )
    }

    @Test fun errorsAreShownAtTheirFieldOrInTheBox() {
        for (p in listOf("pickupSpot", "startDate", "endDate", "priceDollars", "maxRiders", "vehicle", "weekdays", "days.mon", "days.fri.fromCampus", "days.mon.toCampus.departTime", "days.mon.toCampus.campusPointId", "days.mon.toCampus.stops", "days.mon.toCampus.stops.3.address", "days.mon.fromCampus.stops.0.minutes")) {
            assertTrue(p, SeriesErrorText.hasInlineSpot(p))
        }
        for (p in listOf("", "days.sat", "days.mon.sideways", "days.mon.toCampus.stops.x.address", "somethingNew")) assertFalse(p, SeriesErrorText.hasInlineSpot(p))
    }
}
