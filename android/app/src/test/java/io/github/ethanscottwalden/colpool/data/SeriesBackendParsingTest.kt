package io.github.ethanscottwalden.colpool.data

import io.github.ethanscottwalden.colpool.data.series.CampusPoint
import io.github.ethanscottwalden.colpool.data.series.FirebaseSeriesBackend
import io.github.ethanscottwalden.colpool.data.series.RoutePreviewResult
import io.github.ethanscottwalden.colpool.data.series.SeriesError
import org.junit.Assert.assertEquals
import org.junit.Test

class SeriesBackendParsingTest {
    @Test fun campusPointsAreActiveAndInOrder() {
        val docs = listOf(
            "b" to mapOf("name" to "Music Building", "order" to 2, "active" to true),
            "x" to mapOf("name" to "Retired", "order" to 3, "active" to false),
            "a" to mapOf("name" to "Yeager Center", "order" to 1L, "active" to true),
            "n" to mapOf("order" to 4, "active" to true),
        )
        assertEquals(listOf(CampusPoint("a", "Yeager Center"), CampusPoint("b", "Music Building")), FirebaseSeriesBackend.parseCampusPoints(docs))
    }

    @Test fun invalidArgumentCarriesTheErrors() {
        val details = mapOf("errors" to listOf(
            mapOf("code" to "routeNotFound", "path" to "pickupSpot"),
            mapOf("code" to "outOfRange", "path" to "maxRiders", "params" to mapOf("min" to 1, "max" to 6)),
            mapOf("path" to "no-code"),
        ))
        assertEquals(
            RoutePreviewResult.Invalid(listOf(SeriesError("routeNotFound", "pickupSpot"), SeriesError("outOfRange", "maxRiders", mapOf("min" to 1, "max" to 6)))),
            FirebaseSeriesBackend.mapFailure("INVALID_ARGUMENT", details),
        )
    }

    @Test fun otherFailuresMap() {
        assertEquals(RoutePreviewResult.Unavailable, FirebaseSeriesBackend.mapFailure("UNAVAILABLE", mapOf("reason" to "routes-unavailable")))
        assertEquals(RoutePreviewResult.Failed, FirebaseSeriesBackend.mapFailure("INTERNAL", null))
        assertEquals(RoutePreviewResult.Failed, FirebaseSeriesBackend.mapFailure("INVALID_ARGUMENT", null))
        assertEquals(RoutePreviewResult.Failed, FirebaseSeriesBackend.mapFailure("FAILED_PRECONDITION", mapOf("reason" to "draft-posted")))
    }

    @Test fun successNeedsADraftId() {
        assertEquals(RoutePreviewResult.Success("d", "h"), FirebaseSeriesBackend.parseSuccess(mapOf("draftId" to "d", "inputHash" to "h")))
        assertEquals(RoutePreviewResult.Failed, FirebaseSeriesBackend.parseSuccess(mapOf("inputHash" to "h")))
        assertEquals(RoutePreviewResult.Failed, FirebaseSeriesBackend.parseSuccess(null))
    }
}
