package au.kinde.sdk.infrastructure

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.StringReader
import java.io.StringWriter

/**
 * Unit tests for [ByteArrayAdapter].
 */
class ByteArrayAdapterTest {

    private lateinit var adapter: ByteArrayAdapter

    @Before
    fun setup() {
        adapter = ByteArrayAdapter()
    }

    // ============================================
    // Write Tests
    // ============================================

    @Test
    fun `write outputs string from byte array`() {
        val stringWriter = StringWriter()
        val jsonWriter = JsonWriter(stringWriter)
        
        val bytes = "Hello World".toByteArray()
        adapter.write(jsonWriter, bytes)
        
        assertEquals("\"Hello World\"", stringWriter.toString())
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
        adapter.write(null, "test".toByteArray())
    }

    @Test
    fun `write handles empty byte array`() {
        val stringWriter = StringWriter()
        val jsonWriter = JsonWriter(stringWriter)
        
        adapter.write(jsonWriter, ByteArray(0))
        
        assertEquals("\"\"", stringWriter.toString())
    }

    @Test
    fun `write preserves special characters`() {
        val stringWriter = StringWriter()
        val jsonWriter = JsonWriter(stringWriter)
        
        val bytes = "Special chars: @#$%".toByteArray()
        adapter.write(jsonWriter, bytes)
        
        assertEquals("\"Special chars: @#\$%\"", stringWriter.toString())
    }

    // ============================================
    // Read Tests
    // ============================================

    @Test
    fun `read parses string to byte array`() {
        val jsonReader = JsonReader(StringReader("\"Hello World\""))
        
        val result = adapter.read(jsonReader)
        
        assertArrayEquals("Hello World".toByteArray(), result)
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
    fun `read handles empty string`() {
        val jsonReader = JsonReader(StringReader("\"\""))
        
        val result = adapter.read(jsonReader)
        
        assertArrayEquals(ByteArray(0), result)
    }

    @Test
    fun `read preserves special characters`() {
        val jsonReader = JsonReader(StringReader("\"Special chars: @#\$%\""))
        
        val result = adapter.read(jsonReader)
        
        assertArrayEquals("Special chars: @#$%".toByteArray(), result)
    }

    @Test
    fun `read handles unicode characters`() {
        val jsonReader = JsonReader(StringReader("\"Hello 世界\""))
        
        val result = adapter.read(jsonReader)
        
        assertArrayEquals("Hello 世界".toByteArray(Charsets.UTF_8), result)
    }

    // ============================================
    // Round-trip Tests
    // ============================================

    @Test
    fun `round trip preserves byte array value`() {
        val originalBytes = "Test data for round trip".toByteArray()
        
        // Write
        val stringWriter = StringWriter()
        val jsonWriter = JsonWriter(stringWriter)
        adapter.write(jsonWriter, originalBytes)
        
        // Read back
        val written = stringWriter.toString()
        val jsonReader = JsonReader(StringReader(written))
        val result = adapter.read(jsonReader)
        
        assertArrayEquals(originalBytes, result)
    }

    @Test
    fun `round trip preserves empty byte array`() {
        val originalBytes = ByteArray(0)
        
        // Write
        val stringWriter = StringWriter()
        val jsonWriter = JsonWriter(stringWriter)
        adapter.write(jsonWriter, originalBytes)
        
        // Read back
        val written = stringWriter.toString()
        val jsonReader = JsonReader(StringReader(written))
        val result = adapter.read(jsonReader)
        
        assertArrayEquals(originalBytes, result)
    }
}

