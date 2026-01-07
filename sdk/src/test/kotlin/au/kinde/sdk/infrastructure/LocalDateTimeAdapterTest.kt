package au.kinde.sdk.infrastructure

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter
import java.io.StringReader
import java.io.StringWriter

/**
 * Unit tests for [LocalDateTimeAdapter].
 */
class LocalDateTimeAdapterTest {

    private lateinit var adapter: LocalDateTimeAdapter

    @Before
    fun setup() {
        adapter = LocalDateTimeAdapter()
    }

    // ============================================
    // Write Tests
    // ============================================

    @Test
    fun `write outputs formatted datetime string`() {
        val stringWriter = StringWriter()
        val jsonWriter = JsonWriter(stringWriter)
        
        val dateTime = LocalDateTime.of(2024, 3, 15, 10, 30, 45)
        adapter.write(jsonWriter, dateTime)
        
        assertEquals("\"2024-03-15T10:30:45\"", stringWriter.toString())
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
        adapter.write(null, LocalDateTime.of(2024, 1, 1, 0, 0))
    }

    @Test
    fun `write with custom formatter`() {
        val customFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        val customAdapter = LocalDateTimeAdapter(customFormatter)
        val stringWriter = StringWriter()
        val jsonWriter = JsonWriter(stringWriter)
        
        val dateTime = LocalDateTime.of(2024, 3, 15, 14, 30)
        customAdapter.write(jsonWriter, dateTime)
        
        assertEquals("\"15/03/2024 14:30\"", stringWriter.toString())
    }

    // ============================================
    // Read Tests
    // ============================================

    @Test
    fun `read parses ISO datetime string`() {
        val jsonReader = JsonReader(StringReader("\"2024-03-15T10:30:45\""))
        
        val result = adapter.read(jsonReader)
        
        assertEquals(LocalDateTime.of(2024, 3, 15, 10, 30, 45), result)
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
    fun `read with custom formatter`() {
        val customFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        val customAdapter = LocalDateTimeAdapter(customFormatter)
        val jsonReader = JsonReader(StringReader("\"15/03/2024 14:30\""))
        
        val result = customAdapter.read(jsonReader)
        
        assertEquals(LocalDateTime.of(2024, 3, 15, 14, 30), result)
    }

    @Test
    fun `read parses datetime with different time components`() {
        val testCases = listOf(
            "2024-01-01T00:00:00" to LocalDateTime.of(2024, 1, 1, 0, 0, 0),
            "2023-12-31T23:59:59" to LocalDateTime.of(2023, 12, 31, 23, 59, 59),
            "2024-06-15T12:30:00" to LocalDateTime.of(2024, 6, 15, 12, 30, 0)
        )
        
        testCases.forEach { (input, expected) ->
            val jsonReader = JsonReader(StringReader("\"$input\""))
            val result = adapter.read(jsonReader)
            assertEquals(expected, result)
        }
    }

    // ============================================
    // Round-trip Tests
    // ============================================

    @Test
    fun `round trip preserves datetime value`() {
        val originalDateTime = LocalDateTime.of(2024, 7, 20, 15, 45, 30)
        
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

