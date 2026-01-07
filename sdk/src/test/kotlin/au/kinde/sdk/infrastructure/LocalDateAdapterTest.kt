package au.kinde.sdk.infrastructure

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.threeten.bp.LocalDate
import org.threeten.bp.format.DateTimeFormatter
import java.io.StringReader
import java.io.StringWriter

/**
 * Unit tests for [LocalDateAdapter].
 */
class LocalDateAdapterTest {

    private lateinit var adapter: LocalDateAdapter

    @Before
    fun setup() {
        adapter = LocalDateAdapter()
    }

    // ============================================
    // Write Tests
    // ============================================

    @Test
    fun `write outputs formatted date string`() {
        val stringWriter = StringWriter()
        val jsonWriter = JsonWriter(stringWriter)
        
        val date = LocalDate.of(2024, 3, 15)
        adapter.write(jsonWriter, date)
        
        assertEquals("\"2024-03-15\"", stringWriter.toString())
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
        adapter.write(null, LocalDate.of(2024, 1, 1))
    }

    @Test
    fun `write with custom formatter`() {
        val customFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val customAdapter = LocalDateAdapter(customFormatter)
        val stringWriter = StringWriter()
        val jsonWriter = JsonWriter(stringWriter)
        
        val date = LocalDate.of(2024, 3, 15)
        customAdapter.write(jsonWriter, date)
        
        assertEquals("\"15/03/2024\"", stringWriter.toString())
    }

    // ============================================
    // Read Tests
    // ============================================

    @Test
    fun `read parses ISO date string`() {
        val jsonReader = JsonReader(StringReader("\"2024-03-15\""))
        
        val result = adapter.read(jsonReader)
        
        assertEquals(LocalDate.of(2024, 3, 15), result)
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
        val customFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val customAdapter = LocalDateAdapter(customFormatter)
        val jsonReader = JsonReader(StringReader("\"15/03/2024\""))
        
        val result = customAdapter.read(jsonReader)
        
        assertEquals(LocalDate.of(2024, 3, 15), result)
    }

    @Test
    fun `read parses various valid date formats`() {
        val testCases = listOf(
            "2024-01-01" to LocalDate.of(2024, 1, 1),
            "2023-12-31" to LocalDate.of(2023, 12, 31),
            "2000-06-15" to LocalDate.of(2000, 6, 15)
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
    fun `round trip preserves date value`() {
        val originalDate = LocalDate.of(2024, 7, 20)
        
        // Write
        val stringWriter = StringWriter()
        val jsonWriter = JsonWriter(stringWriter)
        adapter.write(jsonWriter, originalDate)
        
        // Read back
        val written = stringWriter.toString()
        val jsonReader = JsonReader(StringReader(written))
        val result = adapter.read(jsonReader)
        
        assertEquals(originalDate, result)
    }
}

