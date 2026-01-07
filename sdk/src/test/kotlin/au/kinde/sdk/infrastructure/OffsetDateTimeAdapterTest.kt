package au.kinde.sdk.infrastructure

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.ZoneOffset
import org.threeten.bp.format.DateTimeFormatter
import java.io.StringReader
import java.io.StringWriter

/**
 * Unit tests for [OffsetDateTimeAdapter].
 */
class OffsetDateTimeAdapterTest {

    private lateinit var adapter: OffsetDateTimeAdapter

    @Before
    fun setup() {
        adapter = OffsetDateTimeAdapter()
    }

    // ============================================
    // Write Tests
    // ============================================

    @Test
    fun `write outputs formatted offset datetime string`() {
        val stringWriter = StringWriter()
        val jsonWriter = JsonWriter(stringWriter)
        
        val dateTime = OffsetDateTime.of(2024, 3, 15, 10, 30, 45, 0, ZoneOffset.UTC)
        adapter.write(jsonWriter, dateTime)
        
        assertEquals("\"2024-03-15T10:30:45Z\"", stringWriter.toString())
    }

    @Test
    fun `write outputs datetime with positive offset`() {
        val stringWriter = StringWriter()
        val jsonWriter = JsonWriter(stringWriter)
        
        val dateTime = OffsetDateTime.of(2024, 3, 15, 10, 30, 45, 0, ZoneOffset.ofHours(5))
        adapter.write(jsonWriter, dateTime)
        
        assertEquals("\"2024-03-15T10:30:45+05:00\"", stringWriter.toString())
    }

    @Test
    fun `write outputs datetime with negative offset`() {
        val stringWriter = StringWriter()
        val jsonWriter = JsonWriter(stringWriter)
        
        val dateTime = OffsetDateTime.of(2024, 3, 15, 10, 30, 45, 0, ZoneOffset.ofHours(-8))
        adapter.write(jsonWriter, dateTime)
        
        assertEquals("\"2024-03-15T10:30:45-08:00\"", stringWriter.toString())
    }

    @Test
    fun `write outputs null for null value`() {
        val stringWriter = StringWriter()
        val jsonWriter = JsonWriter(stringWriter)
        
        adapter.write(jsonWriter, null)
        
        assertEquals("null", stringWriter.toString())
    }

    @Test
    fun `write handles null JsonWriter gracefully`() {
        // Should not throw exception
        adapter.write(null, OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC))
    }

    // ============================================
    // Read Tests
    // ============================================

    @Test
    fun `read parses ISO offset datetime string with UTC`() {
        val jsonReader = JsonReader(StringReader("\"2024-03-15T10:30:45Z\""))
        
        val result = adapter.read(jsonReader)
        
        assertEquals(OffsetDateTime.of(2024, 3, 15, 10, 30, 45, 0, ZoneOffset.UTC), result)
    }

    @Test
    fun `read parses ISO offset datetime string with positive offset`() {
        val jsonReader = JsonReader(StringReader("\"2024-03-15T10:30:45+05:00\""))
        
        val result = adapter.read(jsonReader)
        
        assertEquals(OffsetDateTime.of(2024, 3, 15, 10, 30, 45, 0, ZoneOffset.ofHours(5)), result)
    }

    @Test
    fun `read parses ISO offset datetime string with negative offset`() {
        val jsonReader = JsonReader(StringReader("\"2024-03-15T10:30:45-08:00\""))
        
        val result = adapter.read(jsonReader)
        
        assertEquals(OffsetDateTime.of(2024, 3, 15, 10, 30, 45, 0, ZoneOffset.ofHours(-8)), result)
    }

    @Test
    fun `read returns null for null JSON value`() {
        val jsonReader = JsonReader(StringReader("null"))
        
        val result = adapter.read(jsonReader)
        
        assertNull(result)
    }

    @Test
    fun `read returns null for null JsonReader`() {
        val result = adapter.read(null)
        assertNull(result)
    }

    @Test
    fun `read parses datetime with fractional offset`() {
        val jsonReader = JsonReader(StringReader("\"2024-03-15T10:30:45+05:30\""))
        
        val result = adapter.read(jsonReader)
        
        assertEquals(
            OffsetDateTime.of(2024, 3, 15, 10, 30, 45, 0, ZoneOffset.ofHoursMinutes(5, 30)), 
            result
        )
    }

    // ============================================
    // Round-trip Tests
    // ============================================

    @Test
    fun `round trip preserves offset datetime value`() {
        val originalDateTime = OffsetDateTime.of(2024, 7, 20, 15, 45, 30, 0, ZoneOffset.ofHours(-5))
        
        // Write
        val stringWriter = StringWriter()
        val jsonWriter = JsonWriter(stringWriter)
        adapter.write(jsonWriter, originalDateTime)
        
        // Read back
        val written = stringWriter.toString()
        val jsonReader = JsonReader(StringReader(written))
        val result = adapter.read(jsonReader)
        
        assertEquals(originalDateTime, result)
    }

    @Test
    fun `round trip preserves UTC datetime`() {
        val originalDateTime = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        
        // Write
        val stringWriter = StringWriter()
        val jsonWriter = JsonWriter(stringWriter)
        adapter.write(jsonWriter, originalDateTime)
        
        // Read back
        val written = stringWriter.toString()
        val jsonReader = JsonReader(StringReader(written))
        val result = adapter.read(jsonReader)
        
        assertEquals(originalDateTime, result)
    }
}

