package io.github.ethanscottwalden.colpool.data

import com.google.firebase.Timestamp
import io.github.ethanscottwalden.colpool.data.notifications.FirebaseNotificationsBackend
import io.github.ethanscottwalden.colpool.data.notifications.NotificationParser
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationParserTest {
    private val full = mapOf<String, Any?>(
        "type" to "day_accepted", "seriesId" to "s1", "weekdays" to listOf("wed"),
        "title" to "Day accepted", "body" to "Priya Nair accepted your request for Wednesday.",
        "seriesLine" to "Priya Nair’s series from Riverside Plaza, Target lot",
        "createdAt" to Timestamp(Date(5_000)), "read" to false,
    )

    @Test fun fullRecord() {
        val n = NotificationParser.parse("n1", full)!!
        assertEquals("day_accepted", n.type)
        assertEquals("Day accepted", n.title)
        assertEquals("Priya Nair accepted your request for Wednesday.", n.body)
        assertEquals("Priya Nair’s series from Riverside Plaza, Target lot", n.seriesLine)
        assertEquals(5_000L, n.createdAtMillis)
        assertFalse(n.read)
    }

    @Test fun extraFieldsAndAnUnknownTypeAreFine() {
        val n = NotificationParser.parse("n1", full + mapOf("type" to "something_new", "future" to 42))!!
        assertEquals("something_new", n.type)
        assertEquals("Day accepted", n.title)
    }

    @Test fun missingOptionalFieldsGetSafeDefaults() {
        val n = NotificationParser.parse("n1", mapOf("title" to "Series ended", "body" to "It ended."))!!
        assertEquals("", n.type)
        assertEquals("", n.seriesLine)
        assertNull(n.createdAtMillis)
        assertTrue(n.read) // a malformed record never raises the badge
    }

    @Test fun wrongTypesAreIgnored() {
        val n = NotificationParser.parse("n1", mapOf("title" to 5, "body" to "Hello", "read" to "no", "createdAt" to "today"))!!
        assertEquals("", n.title)
        assertTrue(n.read)
        assertNull(n.createdAtMillis)
    }

    @Test fun aRecordWithNoTextIsSkipped() {
        assertNull(NotificationParser.parse("n1", mapOf("type" to "x")))
        assertNull(NotificationParser.parse("n1", mapOf("title" to "  ", "body" to "")))
        assertNotNull(NotificationParser.parse("n1", mapOf("body" to "Only a body")))
    }

    @Test fun toStringHidesTheContents() {
        val n = NotificationParser.parse("n1", full)!!
        assertFalse(n.toString().contains("Priya"))
        assertFalse(n.toString().contains("n1"))
    }

    @Test fun theOnlyClientWriteIsReadTrue() {
        assertEquals(mapOf<String, Any>("read" to true), FirebaseNotificationsBackend.READ_ONLY_UPDATE)
    }
}
