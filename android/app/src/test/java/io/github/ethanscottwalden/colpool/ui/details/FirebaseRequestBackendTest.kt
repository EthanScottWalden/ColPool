package io.github.ethanscottwalden.colpool.ui.details

import io.github.ethanscottwalden.colpool.data.requests.DayFailure
import io.github.ethanscottwalden.colpool.data.requests.DayFailureReason
import io.github.ethanscottwalden.colpool.data.requests.FirebaseRequestBackend.Companion.mapFailure
import io.github.ethanscottwalden.colpool.data.requests.FirebaseRequestBackend.Companion.parseHostProfile
import io.github.ethanscottwalden.colpool.data.requests.SendRequestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirebaseRequestBackendTest {
    private fun reason(r: String) = mapOf("reason" to r)

    @Test fun mapsEachDocumentedError() {
        assertEquals(SendRequestResult.Invalid, mapFailure("INVALID_ARGUMENT", reason("series-id-invalid")))
        assertEquals(SendRequestResult.Invalid, mapFailure("INVALID_ARGUMENT", reason("weekdays-invalid")))
        assertEquals(SendRequestResult.NotAvailable, mapFailure("NOT_FOUND", null))
        assertEquals(SendRequestResult.NotAvailable, mapFailure("FAILED_PRECONDITION", reason("series-not-active")))
        assertEquals(SendRequestResult.OwnSeries, mapFailure("FAILED_PRECONDITION", reason("own-series")))
        assertEquals(SendRequestResult.AlreadyRequested, mapFailure("ALREADY_EXISTS", reason("already-requested")))
    }

    @Test fun mapsPerDayFailures() {
        val details = mapOf(
            "reason" to "weekdays-unavailable",
            "weekdays" to listOf(
                mapOf("weekday" to "mon", "reason" to "full"),
                mapOf("weekday" to "wed", "reason" to "no-trips-left"),
                mapOf("weekday" to "fri", "reason" to "conflict"),
                mapOf("weekday" to "tue", "reason" to "mystery"),
                "junk",
            ),
        )
        assertEquals(
            SendRequestResult.WeekdaysUnavailable(listOf(DayFailure("mon", DayFailureReason.Full), DayFailure("wed", DayFailureReason.NoTripsLeft), DayFailure("fri", DayFailureReason.Conflict))),
            mapFailure("FAILED_PRECONDITION", details),
        )
    }

    @Test fun everythingElseIsAFailure() {
        assertEquals(SendRequestResult.Failed, mapFailure("UNAVAILABLE", null))
        assertEquals(SendRequestResult.Failed, mapFailure("INTERNAL", null))
        assertEquals(SendRequestResult.Failed, mapFailure("INVALID_ARGUMENT", null))
        assertEquals(SendRequestResult.Failed, mapFailure("FAILED_PRECONDITION", reason("weekdays-unavailable")))
        assertEquals(SendRequestResult.Failed, mapFailure("FAILED_PRECONDITION", "text"))
        assertEquals(SendRequestResult.Failed, mapFailure("ALREADY_EXISTS", null))
    }

    @Test fun hostProfileKeepsOnlyPublicFieldsAndNeedsBothNames() {
        val p = parseHostProfile(mapOf("firstName" to " Ava ", "lastName" to "Thompson", "age" to 20L, "interests" to listOf("music", 3), "email" to "ava@calbaptist.edu", "startingAddress" to "x"))!!
        assertEquals("Ava", p.firstName)
        assertEquals(20, p.age)
        assertEquals(listOf("music"), p.interests)
        assertEquals("HostProfile(redacted)", p.toString())
        assertNull(parseHostProfile(mapOf("firstName" to "Ava")))
        assertNull(parseHostProfile(null))
    }
}
