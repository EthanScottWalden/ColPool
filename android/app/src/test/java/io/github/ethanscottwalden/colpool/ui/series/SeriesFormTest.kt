package io.github.ethanscottwalden.colpool.ui.series

import io.github.ethanscottwalden.colpool.ui.series.SeriesTestData.TODAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesFormTest {
    @Test fun twelveHourTimesAndTwentyFourHourStrings() {
        assertEquals("12:00 AM", SeriesTime.format12(0))
        assertEquals("12:30 AM", SeriesTime.format12(30))
        assertEquals("7:30 AM", SeriesTime.format12(7 * 60 + 30))
        assertEquals("11:59 AM", SeriesTime.format12(11 * 60 + 59))
        assertEquals("12:00 PM", SeriesTime.format12(12 * 60))
        assertEquals("3:30 PM", SeriesTime.format12(15 * 60 + 30))
        assertEquals("11:59 PM", SeriesTime.format12(23 * 60 + 59))
        assertEquals("00:00", SeriesTime.to24h(0))
        assertEquals("07:30", SeriesTime.to24h(7 * 60 + 30))
        assertEquals("12:00", SeriesTime.to24h(12 * 60))
        assertEquals("15:30", SeriesTime.to24h(15 * 60 + 30))
        assertEquals("23:59", SeriesTime.to24h(23 * 60 + 59))
    }

    @Test fun datesShowAsMonthDayYear() {
        assertEquals("11/30/2026", SeriesDates.display("2026-11-30"))
        assertEquals("2026-11-30", SeriesDates.fromPickerMillis(SeriesDates.toPickerMillis("2026-11-30")))
    }

    @Test fun payloadMatchesSeriesInputShape() {
        val p = SeriesPayload.build(SeriesTestData.filled().copy(vehicle = "  Blue 2017 Honda Fit "))
        assertEquals(listOf("mon", "wed"), p["weekdays"])
        assertEquals("2026-11-30", p["startDate"])
        assertEquals("2026-12-11", p["endDate"])
        assertEquals(5, p["priceDollars"])
        assertEquals(2, p["maxRiders"])
        assertEquals("Blue 2017 Honda Fit", p["vehicle"])
        assertEquals(
            mapOf("text" to SeriesTestData.NORCO.text, "placeId" to "place-norco", "lat" to 33.9311, "lng" to -117.5481),
            p["pickupSpot"],
        )
        @Suppress("UNCHECKED_CAST") val days = p["days"] as Map<String, Map<String, Map<String, Any>>>
        // days keys match weekdays exactly.
        assertEquals(setOf("mon", "wed"), days.keys)
        assertEquals("07:30", days["wed"]!!["toCampus"]!!["departTime"])
        assertEquals("yeager-center", days["wed"]!!["toCampus"]!!["campusPointId"])
        assertEquals("15:30", days["wed"]!!["fromCampus"]!!["departTime"])
        assertEquals("lancer-plaza", days["wed"]!!["fromCampus"]!!["campusPointId"])
        assertEquals(emptyList<Any>(), days["mon"]!!["toCampus"]!!["stops"])
        val stop = (days["wed"]!!["toCampus"]!!["stops"] as List<*>).single() as Map<*, *>
        assertEquals(5, stop["minutes"])
        assertEquals("place-starbucks", (stop["address"] as Map<*, *>)["placeId"])
    }

    @Test fun payloadWeekdaysAreMondayFirstWhateverTheTapOrder() {
        val s = SeriesTestData.filled().copy(
            weekdays = setOf(SeriesWeekday.Fri, SeriesWeekday.Mon, SeriesWeekday.Wed),
            days = mapOf(SeriesWeekday.Fri to DayForm(LegForm(0, "yeager-center"), LegForm(12 * 60, "yeager-center")), SeriesWeekday.Mon to DayForm(LegForm(60, "yeager-center"), LegForm(61, "yeager-center")), SeriesWeekday.Wed to DayForm(LegForm(60, "yeager-center"), LegForm(61, "yeager-center"))),
        )
        val p = SeriesPayload.build(s)
        assertEquals(listOf("mon", "wed", "fri"), p["weekdays"])
        @Suppress("UNCHECKED_CAST") val days = p["days"] as Map<String, Map<String, Map<String, Any>>>
        assertEquals("00:00", days["fri"]!!["toCampus"]!!["departTime"])
        assertEquals("12:00", days["fri"]!!["fromCampus"]!!["departTime"])
    }

    @Test fun completeFormPassesChecks() {
        assertEquals(emptyList<Any>(), SeriesChecks.run(SeriesTestData.filled(), TODAY))
    }

    private fun codes(s: CreateSeriesUiState) = SeriesChecks.run(s, TODAY).map { it.code to it.path }

    @Test fun emptyFormListsEveryRequiredField() {
        val found = codes(CreateSeriesUiState(campusPoints = SeriesTestData.CAMPUS_POINTS))
        assertTrue("required" to "pickupSpot" in found)
        assertTrue("required" to "startDate" in found)
        assertTrue("required" to "endDate" in found)
        assertTrue("required" to "priceDollars" in found)
        assertTrue("required" to "vehicle" in found)
        assertTrue("required" to "weekdays" in found)
    }

    @Test fun priceRange() {
        val s = SeriesTestData.filled()
        assertTrue(("outOfRange" to "priceDollars") in codes(s.copy(price = "0")))
        assertTrue(("outOfRange" to "priceDollars") in codes(s.copy(price = "100")))
        assertTrue(("notInteger" to "priceDollars") in codes(s.copy(price = "5.5")))
        assertEquals(emptyList<Any>(), codes(s.copy(price = "1")))
        assertEquals(emptyList<Any>(), codes(s.copy(price = "99")))
    }

    @Test fun datesAndWeekdayRange() {
        val s = SeriesTestData.filled()
        assertTrue(("startInPast" to "startDate") in codes(s.copy(startDate = "2026-11-22")))
        assertEquals(emptyList<Any>(), codes(s.copy(startDate = "2026-11-23")))
        assertTrue(("endBeforeStart" to "endDate") in codes(s.copy(endDate = "2026-11-29")))
        // Tue 2026-12-01 only; Mon and Wed have no date in range.
        assertTrue(("noDateOnWeekdays" to "") in codes(s.copy(startDate = "2026-12-01", endDate = "2026-12-01")))
        // One weekday without a date is fine when another has one (Wed 2026-12-02 is in range, Mon is not).
        assertEquals(emptyList<Any>(), codes(s.copy(startDate = "2026-12-01", endDate = "2026-12-02")))
    }

    @Test fun vehicleLimit() {
        val s = SeriesTestData.filled()
        assertTrue(("required" to "vehicle") in codes(s.copy(vehicle = "   ")))
        assertTrue(("tooLong" to "vehicle") in codes(s.copy(vehicle = "x".repeat(61))))
        assertEquals(emptyList<Any>(), codes(s.copy(vehicle = "x".repeat(60))))
    }

    @Test fun legsNeedATimeAndACampusPoint() {
        val s = SeriesTestData.filled().let { it.copy(days = it.days + (SeriesWeekday.Mon to DayForm())) }
        val found = codes(s)
        assertTrue("required" to "days.mon.toCampus.departTime" in found)
        assertTrue("required" to "days.mon.toCampus.campusPointId" in found)
        assertTrue("required" to "days.mon.fromCampus.departTime" in found)
        assertTrue("required" to "days.mon.fromCampus.campusPointId" in found)
    }

    @Test fun stopMinutesRangeAndStopCap() {
        val s = SeriesTestData.filled()
        val wed = s.day(SeriesWeekday.Wed)
        val bad = s.copy(days = s.days + (SeriesWeekday.Wed to wed.withLeg(Direction.ToCampus, wed.toCampus.copy(stops = listOf(SeriesTestData.stop(minutes = 121))))))
        assertTrue(("outOfRange" to "days.wed.toCampus.stops.0.minutes") in codes(bad))
        val tooMany = s.copy(days = s.days + (SeriesWeekday.Wed to wed.withLeg(Direction.FromCampus, wed.fromCampus.copy(stops = List(11) { SeriesTestData.stop() }))))
        assertTrue(("tooManyStops" to "days.wed.fromCampus.stops") in codes(tooMany))
    }
}
