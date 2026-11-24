package io.github.ethanscottwalden.colpool.ui.series

import io.github.ethanscottwalden.colpool.data.series.FirebaseSeriesBackend
import io.github.ethanscottwalden.colpool.data.series.PostSeriesResult
import io.github.ethanscottwalden.colpool.data.series.ReviewDraftParser
import io.github.ethanscottwalden.colpool.data.series.SeriesError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReviewDraftParserTest {
    @Test fun parsesTheStoredDraft() {
        val d = ReviewTestData.draft()
        assertEquals(ReviewTestData.PICKUP, d.pickupName)
        assertEquals(listOf("mon", "wed"), d.weekdayIds)
        assertEquals(5, d.priceDollars)
        assertEquals(2, d.maxRiders)
        val wed = d.days.getValue("wed")
        assertEquals(1, wed.toCampus.stops.size)
        assertEquals(2, wed.toCampus.hops.size)
        assertEquals(3, wed.toCampus.times.size)
        assertEquals(0, d.days.getValue("mon").toCampus.stops.size)
        assertEquals("yeager-center", wed.toCampus.campusPointId)
    }

    @Test fun keepsTheStoredInputUnchanged() {
        @Suppress("UNCHECKED_CAST")
        assertEquals(ReviewTestData.doc()["input"] as Map<String, Any>, ReviewTestData.draft().input)
    }

    @Test fun rejectsInconsistentOrIncompleteDrafts() {
        assertNull(ReviewDraftParser.parse(emptyMap()))
        assertNull(ReviewDraftParser.parse(ReviewTestData.doc() - "route"))
        // A stored route with one hop too few for the stops entered.
        @Suppress("UNCHECKED_CAST")
        val route = ReviewTestData.doc()["route"] as Map<String, Map<String, Map<String, Any>>>
        val wedTo = route.getValue("wed").getValue("toCampus")
        val short = wedTo + ("legs" to (wedTo["legs"] as List<*>).dropLast(1))
        val doc = ReviewTestData.doc() + ("route" to (route + ("wed" to (route.getValue("wed") + ("toCampus" to short)))))
        assertNull(ReviewDraftParser.parse(doc))
    }

    @Test fun nonIntegerFirestoreNumbersStillParse() {
        @Suppress("UNCHECKED_CAST")
        val input = (ReviewTestData.doc()["input"] as Map<String, Any>) + ("priceDollars" to 5L)
        assertEquals(5, ReviewTestData.draft(ReviewTestData.doc() + ("input" to input)).priceDollars)
    }

    @Test fun postedResultAndErrorsMap() {
        assertEquals(PostSeriesResult.Posted("abc", false), FirebaseSeriesBackend.parsePosted(mapOf("seriesId" to "abc", "alreadyPosted" to false)))
        assertEquals(PostSeriesResult.Posted("abc", true), FirebaseSeriesBackend.parsePosted(mapOf("seriesId" to "abc", "alreadyPosted" to true)))
        assertEquals(PostSeriesResult.Failed, FirebaseSeriesBackend.parsePosted(null))
        assertEquals(PostSeriesResult.Failed, FirebaseSeriesBackend.parsePosted(mapOf("alreadyPosted" to true)))

        val details = mapOf("errors" to listOf(mapOf("code" to "startInPast", "path" to "startDate")))
        assertEquals(PostSeriesResult.Invalid(listOf(SeriesError("startInPast", "startDate"))), FirebaseSeriesBackend.mapPostFailure("INVALID_ARGUMENT", "series-invalid", details))
        assertEquals(PostSeriesResult.NotFound, FirebaseSeriesBackend.mapPostFailure("INVALID_ARGUMENT", "draft-id-invalid", null))
        assertEquals(PostSeriesResult.Failed, FirebaseSeriesBackend.mapPostFailure("INVALID_ARGUMENT", "other", null))
        assertEquals(PostSeriesResult.NotFound, FirebaseSeriesBackend.mapPostFailure("NOT_FOUND", "Draft not found.", null))
        assertEquals(PostSeriesResult.DraftChanged, FirebaseSeriesBackend.mapPostFailure("FAILED_PRECONDITION", "x", mapOf("reason" to "draft-changed")))
        assertEquals(PostSeriesResult.Failed, FirebaseSeriesBackend.mapPostFailure("FAILED_PRECONDITION", "x", mapOf("reason" to "other")))
        assertEquals(PostSeriesResult.Failed, FirebaseSeriesBackend.mapPostFailure("UNAVAILABLE", null, null))
        assertEquals(PostSeriesResult.Failed, FirebaseSeriesBackend.mapPostFailure("INTERNAL", null, null))
    }
}
