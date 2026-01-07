package au.kinde.sdk.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [Flag] data class.
 */
class FlagTest {

    @Test
    fun `Flag stores code correctly`() {
        val flag = Flag("feature_enabled", FlagType.Boolean, true, false)
        assertEquals("feature_enabled", flag.code)
    }

    @Test
    fun `Flag stores type correctly`() {
        val boolFlag = Flag("bool_flag", FlagType.Boolean, true, false)
        val stringFlag = Flag("string_flag", FlagType.String, "value", false)
        val intFlag = Flag("int_flag", FlagType.Integer, 42, false)

        assertEquals(FlagType.Boolean, boolFlag.type)
        assertEquals(FlagType.String, stringFlag.type)
        assertEquals(FlagType.Integer, intFlag.type)
    }

    @Test
    fun `Flag can have null type`() {
        val flag = Flag("unknown_flag", null, "value", false)
        assertNull(flag.type)
    }

    @Test
    fun `Flag stores boolean value correctly`() {
        val trueFlag = Flag("enabled", FlagType.Boolean, true, false)
        val falseFlag = Flag("disabled", FlagType.Boolean, false, false)

        assertEquals(true, trueFlag.value)
        assertEquals(false, falseFlag.value)
    }

    @Test
    fun `Flag stores string value correctly`() {
        val flag = Flag("theme", FlagType.String, "dark", false)
        assertEquals("dark", flag.value)
    }

    @Test
    fun `Flag stores integer value correctly`() {
        val flag = Flag("max_items", FlagType.Integer, 100, false)
        assertEquals(100, flag.value)
    }

    @Test
    fun `Flag stores isDefault correctly when false`() {
        val flag = Flag("feature", FlagType.Boolean, true, false)
        assertFalse(flag.isDefault)
    }

    @Test
    fun `Flag stores isDefault correctly when true`() {
        val flag = Flag("feature", FlagType.Boolean, true, true)
        assertTrue(flag.isDefault)
    }

    @Test
    fun `Flag equality works for identical flags`() {
        val flag1 = Flag("feature", FlagType.Boolean, true, false)
        val flag2 = Flag("feature", FlagType.Boolean, true, false)
        assertEquals(flag1, flag2)
    }

    @Test
    fun `Flag inequality for different codes`() {
        val flag1 = Flag("feature1", FlagType.Boolean, true, false)
        val flag2 = Flag("feature2", FlagType.Boolean, true, false)
        assertFalse(flag1 == flag2)
    }

    @Test
    fun `Flag inequality for different types`() {
        val flag1 = Flag("feature", FlagType.Boolean, true, false)
        val flag2 = Flag("feature", FlagType.String, true, false)
        assertFalse(flag1 == flag2)
    }

    @Test
    fun `Flag inequality for different values`() {
        val flag1 = Flag("feature", FlagType.Boolean, true, false)
        val flag2 = Flag("feature", FlagType.Boolean, false, false)
        assertFalse(flag1 == flag2)
    }

    @Test
    fun `Flag inequality for different isDefault`() {
        val flag1 = Flag("feature", FlagType.Boolean, true, false)
        val flag2 = Flag("feature", FlagType.Boolean, true, true)
        assertFalse(flag1 == flag2)
    }

    @Test
    fun `Flag hashCode is consistent for equal flags`() {
        val flag1 = Flag("feature", FlagType.Boolean, true, false)
        val flag2 = Flag("feature", FlagType.Boolean, true, false)
        assertEquals(flag1.hashCode(), flag2.hashCode())
    }

    @Test
    fun `Flag copy works correctly`() {
        val original = Flag("feature", FlagType.Boolean, true, false)
        val copied = original.copy(isDefault = true)

        assertEquals(original.code, copied.code)
        assertEquals(original.type, copied.type)
        assertEquals(original.value, copied.value)
        assertTrue(copied.isDefault)
    }

    @Test
    fun `Flag toString contains all properties`() {
        val flag = Flag("feature", FlagType.Boolean, true, false)
        val string = flag.toString()

        assertTrue(string.contains("feature"))
        assertTrue(string.contains("Boolean"))
        assertTrue(string.contains("true"))
        assertTrue(string.contains("false"))
    }
}

