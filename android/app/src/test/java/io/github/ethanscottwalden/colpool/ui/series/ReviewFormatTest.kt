package io.github.ethanscottwalden.colpool.ui.series

import io.github.ethanscottwalden.colpool.data.series.DraftStop
import io.github.ethanscottwalden.colpool.data.series.DraftLeg
import io.github.ethanscottwalden.colpool.data.series.RouteHop
import io.github.ethanscottwalden.colpool.data.series.RouteTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewFormatTest {
    private val nd = "(next day)"

    @Test fun timesAre12HourPacific() {
        assertEquals("12:00 AM", ReviewFormat.time(0, nd))
        assertEquals("12:00 PM", ReviewFormat.time(720, nd))
        assertEquals("7:30 AM", ReviewFormat.time(450, nd))
        assertEquals("3:52 PM", ReviewFormat.time(15 * 60 + 52, nd))
        assertEquals("11:59 PM", ReviewFormat.time(1439, nd))
    }

    @Test fun nextDayTimesShowClockTimeAndMarker() {
        assertEquals("12:10 AM (next day)", ReviewFormat.time(1450, nd))
        assertEquals("12:00 AM (next day)", ReviewFormat.time(1440, nd))
        assertEquals("1:05 PM (next day)", ReviewFormat.time(1440 + 13 * 60 + 5, nd))
    }

    @Test fun milesUseOneDecimal() {
        assertEquals("0.0", ReviewFormat.miles(0.0))
        assertEquals("10.0", ReviewFormat.miles(16093.44))
        assertEquals("19.9", ReviewFormat.miles(32000.0))
        assertEquals("1.0", ReviewFormat.miles(1609.344))
    }

    @Test fun eachHopRoundsOnItsOwnAndHalfRoundsUp() {
        assertEquals(31, ReviewFormat.hopMinutes(1830.0))
        assertEquals(18, ReviewFormat.hopMinutes(1109.4))
        assertEquals(19, ReviewFormat.hopMinutes(1110.0))
        assertEquals(11, ReviewFormat.hopMinutes(689.5))
        assertEquals(12, ReviewFormat.hopMinutes(690.0))
        assertEquals(0, ReviewFormat.hopMinutes(29.0))
        assertEquals(1, ReviewFormat.hopMinutes(30.0))
    }

    private fun leg(stops: List<DraftStop>, hops: List<RouteHop>, times: List<RouteTime>) = DraftLeg("yeager-center", stops, hops, times)

    @Test fun zeroStopsGivesStartAndEnd() {
        val c = ReviewCards.build(leg(emptyList(), listOf(RouteHop(32000.0, 3000.0)), listOf(RouteTime(null, 450), RouteTime(500, null))), "Pickup", "Yeager Center")
        assertEquals(listOf(ReviewRole.Start, ReviewRole.End), c.points.map { it.role })
        assertEquals(listOf("Pickup", "Yeager Center"), c.points.map { it.name })
        assertEquals(listOf(450, 500), c.points.map { it.time })
        assertEquals(listOf(ReviewHopRow(50, 199)), c.hops)
        assertEquals(50, c.totalMinutes)
    }

    @Test fun oneStopAddsItsMinutesAndLeaveTime() {
        val c = ReviewCards.build(
            leg(
                listOf(DraftStop("Starbucks", 5)),
                listOf(RouteHop(10000.0, 1830.0), RouteHop(5000.0, 1110.0)),
                listOf(RouteTime(null, 450), RouteTime(481, 486), RouteTime(505, null)),
            ),
            "Pickup", "Yeager Center",
        )
        assertEquals(listOf(ReviewRole.Start, ReviewRole.Stop, ReviewRole.End), c.points.map { it.role })
        val stop = c.points[1]
        assertEquals("Starbucks", stop.name)
        assertEquals(481, stop.time)
        assertEquals(486, stop.leaveMin)
        assertEquals(5, stop.stopMinutes)
        assertEquals(listOf(31, 19), c.hops.map { it.minutes })
        // Sum of the displayed hops plus the stop, not round(2940 / 60) + 5 = 54.
        assertEquals(31 + 19 + 5, c.totalMinutes)
        assertEquals(listOf(62, 31), c.hops.map { it.milesTenths })
        assertEquals(93, c.totalMilesTenths)
    }

    @Test fun twoStopsKeepOrderAndTotals() {
        val c = ReviewCards.build(
            leg(
                listOf(DraftStop("A", 2), DraftStop("B", 10)),
                listOf(RouteHop(1000.0, 300.0), RouteHop(2000.0, 90.0), RouteHop(3000.0, 600.0)),
                listOf(RouteTime(null, 1400), RouteTime(1405, 1407), RouteTime(1409, 1419), RouteTime(1429, null)),
            ),
            "Campus", "Pickup",
        )
        assertEquals(listOf("Campus", "A", "B", "Pickup"), c.points.map { it.name })
        assertEquals(listOf(5, 2, 10), c.hops.map { it.minutes })
        assertEquals(5 + 2 + 10 + 2 + 10, c.totalMinutes)
        assertEquals(37, c.totalMilesTenths)
        assertEquals("11:49 PM", ReviewFormat.time(c.points.last().time, nd))
    }

    @Test fun milesTotalsEqualTheSumOfDisplayedHopMiles() {
        // Each 2000 m hop shows 1.2 mi; raw 6000 m would show 3.7, but the rider sees 1.2 + 1.2 + 1.2 = 3.6.
        fun card(from: String, to: String) = ReviewCards.build(
            leg(
                listOf(DraftStop("A", 1), DraftStop("B", 1)),
                List(3) { RouteHop(2000.0, 300.0) },
                listOf(RouteTime(null, 400), RouteTime(405, 406), RouteTime(411, 412), RouteTime(417, null)),
            ),
            from, to,
        )
        val a = card("Pickup", "Yeager Center")
        val b = card("Yeager Center", "Pickup")
        assertEquals(listOf("1.2", "1.2", "1.2"), a.hops.map { ReviewFormat.tenths(it.milesTenths) })
        assertEquals(a.hops.sumOf { it.milesTenths }, a.totalMilesTenths)
        assertEquals("3.6", ReviewFormat.tenths(a.totalMilesTenths))
        assertEquals("7.2", ReviewFormat.tenths(a.totalMilesTenths + b.totalMilesTenths))
    }
}
