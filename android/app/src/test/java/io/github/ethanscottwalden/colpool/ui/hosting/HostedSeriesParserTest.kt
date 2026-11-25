package io.github.ethanscottwalden.colpool.ui.hosting

import io.github.ethanscottwalden.colpool.data.series.HostedSeriesParser
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HostedSeriesParserTest {
    @Test fun parsesAStoredSeries() {
        val s = HostingTestData.series("s1")
        assertEquals(listOf("tue", "thu"), s.weekdayIds)
        assertEquals(mapOf("tue" to 2, "thu" to 3), s.openSeats)
        assertEquals(3, s.maxRiders)
        // Tue has one stop each way; times and hops come from the stored route.
        assertEquals(1, s.days.getValue("tue").toCampus.stops.size)
        assertEquals(2, s.days.getValue("tue").toCampus.hops.size)
        assertEquals(0, s.days.getValue("thu").fromCampus.stops.size)
        assertEquals(450, s.days.getValue("tue").toCampus.times.first().departMin)
    }

    @Test fun keepsLastDepartureAsInstants() {
        val s = HostingTestData.series("s1")
        assertEquals(Instant.ofEpochSecond(1_769_000_000), s.lastDepartureAt["tue"])
    }

    @Test fun aMissingLastDepartureKeyIsFine() {
        val doc = HostingTestData.doc() - "lastDepartureAt"
        assertEquals(emptyMap<String, Instant>(), HostingTestData.series("s1", doc).lastDepartureAt)
    }

    @Test fun extraFieldsAreIgnored() {
        assertNotNull(HostedSeriesParser.parse("s1", HostingTestData.doc() + ("somethingNew" to 7)))
    }

    @Test fun anIncompleteDocumentIsSkipped() {
        for (key in listOf("pickupSpot", "startDate", "endDate", "priceDollars", "maxRiders", "vehicle", "weekdays", "days", "openSeats")) {
            assertNull(key, HostedSeriesParser.parse("s1", HostingTestData.doc() - key))
        }
    }

    @Test fun aWeekdayWithoutSeatsOrRouteIsSkipped() {
        assertNull(HostedSeriesParser.parse("s1", HostingTestData.doc(open = mapOf("tue" to 2))))
        val days = (HostingTestData.doc()["days"] as Map<*, *>) - "thu"
        assertNull(HostedSeriesParser.parse("s1", HostingTestData.doc() + ("days" to days)))
    }

    @Test fun seatsAreClampedToZeroThroughMax() {
        val s = HostingTestData.series("s1", HostingTestData.doc(open = mapOf("tue" to -1, "thu" to 9)))
        assertEquals(mapOf("tue" to 0, "thu" to 3), s.openSeats)
    }

    @Test fun anUnknownWeekdayIsRejected() {
        assertNull(HostedSeriesParser.parse("s1", HostingTestData.doc() + ("weekdays" to listOf("tue", "sat"))))
    }

    @Test fun weekdaysAreOrderedMondayFirst() {
        val s = HostingTestData.series("s1", HostingTestData.doc() + ("weekdays" to listOf("thu", "tue")))
        assertEquals(listOf("tue", "thu"), s.weekdayIds)
    }
}
