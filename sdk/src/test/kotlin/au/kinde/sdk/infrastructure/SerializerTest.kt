package au.kinde.sdk.infrastructure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.threeten.bp.LocalDate
import org.threeten.bp.LocalDateTime
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.ZoneOffset

/**
 * Unit tests for [Serializer] object.
 */
class SerializerTest {

    // ============================================
    // GsonBuilder Tests
    // ============================================

    @Test
    fun `gsonBuilder is not null`() {
        assertNotNull(Serializer.gsonBuilder)
    }

    @Test
    fun `gsonBuilder creates valid Gson instance`() {
        val gson = Serializer.gsonBuilder.create()
        assertNotNull(gson)
    }

    // ============================================
    // Gson Tests
    // ============================================

    @Test
    fun `gson is not null`() {
        assertNotNull(Serializer.gson)
    }

    @Test
    fun `gson is lazily initialized and returns same instance`() {
        val gson1 = Serializer.gson
        val gson2 = Serializer.gson
        assertTrue(gson1 === gson2)
    }

    // ============================================
    // LocalDate Serialization Tests
    // ============================================

    @Test
    fun `gson serializes LocalDate correctly`() {
        val date = LocalDate.of(2024, 3, 15)
        val json = Serializer.gson.toJson(date)
        assertEquals("\"2024-03-15\"", json)
    }

    @Test
    fun `gson deserializes LocalDate correctly`() {
        val json = "\"2024-03-15\""
        val date = Serializer.gson.fromJson(json, LocalDate::class.java)
        assertEquals(LocalDate.of(2024, 3, 15), date)
    }

    @Test
    fun `gson handles null LocalDate`() {
        val date: LocalDate? = null
        val json = Serializer.gson.toJson(date)
        assertEquals("null", json)
    }

    @Test
    fun `gson LocalDate round trip`() {
        val original = LocalDate.of(2024, 6, 20)
        val json = Serializer.gson.toJson(original)
        val restored = Serializer.gson.fromJson(json, LocalDate::class.java)
        assertEquals(original, restored)
    }

    // ============================================
    // LocalDateTime Serialization Tests
    // ============================================

    @Test
    fun `gson serializes LocalDateTime correctly`() {
        val dateTime = LocalDateTime.of(2024, 3, 15, 10, 30, 45)
        val json = Serializer.gson.toJson(dateTime)
        assertEquals("\"2024-03-15T10:30:45\"", json)
    }

    @Test
    fun `gson deserializes LocalDateTime correctly`() {
        val json = "\"2024-03-15T10:30:45\""
        val dateTime = Serializer.gson.fromJson(json, LocalDateTime::class.java)
        assertEquals(LocalDateTime.of(2024, 3, 15, 10, 30, 45), dateTime)
    }

    @Test
    fun `gson handles null LocalDateTime`() {
        val dateTime: LocalDateTime? = null
        val json = Serializer.gson.toJson(dateTime)
        assertEquals("null", json)
    }

    @Test
    fun `gson LocalDateTime round trip`() {
        val original = LocalDateTime.of(2024, 6, 20, 14, 30, 0)
        val json = Serializer.gson.toJson(original)
        val restored = Serializer.gson.fromJson(json, LocalDateTime::class.java)
        assertEquals(original, restored)
    }

    // ============================================
    // OffsetDateTime Serialization Tests
    // ============================================

    @Test
    fun `gson serializes OffsetDateTime correctly`() {
        val dateTime = OffsetDateTime.of(2024, 3, 15, 10, 30, 45, 0, ZoneOffset.UTC)
        val json = Serializer.gson.toJson(dateTime)
        assertEquals("\"2024-03-15T10:30:45Z\"", json)
    }

    @Test
    fun `gson deserializes OffsetDateTime correctly`() {
        val json = "\"2024-03-15T10:30:45Z\""
        val dateTime = Serializer.gson.fromJson(json, OffsetDateTime::class.java)
        assertEquals(OffsetDateTime.of(2024, 3, 15, 10, 30, 45, 0, ZoneOffset.UTC), dateTime)
    }

    @Test
    fun `gson handles OffsetDateTime with offset`() {
        val dateTime = OffsetDateTime.of(2024, 3, 15, 10, 30, 45, 0, ZoneOffset.ofHours(5))
        val json = Serializer.gson.toJson(dateTime)
        assertEquals("\"2024-03-15T10:30:45+05:00\"", json)
    }

    @Test
    fun `gson handles null OffsetDateTime`() {
        val dateTime: OffsetDateTime? = null
        val json = Serializer.gson.toJson(dateTime)
        assertEquals("null", json)
    }

    @Test
    fun `gson OffsetDateTime round trip with UTC`() {
        val original = OffsetDateTime.of(2024, 6, 20, 14, 30, 0, 0, ZoneOffset.UTC)
        val json = Serializer.gson.toJson(original)
        val restored = Serializer.gson.fromJson(json, OffsetDateTime::class.java)
        assertEquals(original, restored)
    }

    @Test
    fun `gson OffsetDateTime round trip with positive offset`() {
        val original = OffsetDateTime.of(2024, 6, 20, 14, 30, 0, 0, ZoneOffset.ofHours(8))
        val json = Serializer.gson.toJson(original)
        val restored = Serializer.gson.fromJson(json, OffsetDateTime::class.java)
        assertEquals(original, restored)
    }

    // ============================================
    // ByteArray Serialization Tests
    // ============================================

    @Test
    fun `gson serializes ByteArray correctly`() {
        val bytes = "Hello World".toByteArray()
        val json = Serializer.gson.toJson(bytes)
        assertEquals("\"Hello World\"", json)
    }

    @Test
    fun `gson deserializes ByteArray correctly`() {
        val json = "\"Hello World\""
        val bytes = Serializer.gson.fromJson(json, ByteArray::class.java)
        assertEquals("Hello World", String(bytes))
    }

    @Test
    fun `gson handles empty ByteArray`() {
        val bytes = ByteArray(0)
        val json = Serializer.gson.toJson(bytes)
        assertEquals("\"\"", json)
    }

    @Test
    fun `gson handles null ByteArray`() {
        val bytes: ByteArray? = null
        val json = Serializer.gson.toJson(bytes)
        assertEquals("null", json)
    }

    @Test
    fun `gson ByteArray round trip`() {
        val original = "Test data for round trip".toByteArray()
        val json = Serializer.gson.toJson(original)
        val restored = Serializer.gson.fromJson(json, ByteArray::class.java)
        assertEquals(String(original), String(restored))
    }
}
