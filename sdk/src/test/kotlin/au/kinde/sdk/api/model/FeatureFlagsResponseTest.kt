package au.kinde.sdk.api.model

import au.kinde.sdk.model.FlagType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [FeatureFlagsResponse] and related data classes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class FeatureFlagsResponseTest {

    // ============================================
    // FeatureFlagsResponse isValid() Tests
    // ============================================

    @Test
    fun `isValid returns true when success is true and data is present`() {
        val response = FeatureFlagsResponse(
            data = FeatureFlagsData(featureFlags = emptyList()),
            success = true
        )
        assertTrue(response.isValid())
    }

    @Test
    fun `isValid returns false when success is false`() {
        val response = FeatureFlagsResponse(
            data = FeatureFlagsData(featureFlags = emptyList()),
            success = false
        )
        assertFalse(response.isValid())
    }

    @Test
    fun `isValid returns false when data is null`() {
        val response = FeatureFlagsResponse(
            data = null,
            success = true
        )
        assertFalse(response.isValid())
    }

    @Test
    fun `isValid returns false when both success is false and data is null`() {
        val response = FeatureFlagsResponse(
            data = null,
            success = false
        )
        assertFalse(response.isValid())
    }

    // ============================================
    // FeatureFlagsResponse toFlagMap() Tests
    // ============================================

    @Test
    fun `toFlagMap returns empty map when success is false`() {
        val response = FeatureFlagsResponse(
            data = FeatureFlagsData(
                featureFlags = listOf(
                    FeatureFlagItem(id = "1", key = "flag1", type = "Boolean", value = true)
                )
            ),
            success = false
        )
        
        val flagMap = response.toFlagMap()
        
        assertTrue(flagMap.isEmpty())
    }

    @Test
    fun `toFlagMap returns empty map when data is null`() {
        val response = FeatureFlagsResponse(
            data = null,
            success = true
        )
        
        val flagMap = response.toFlagMap()
        
        assertTrue(flagMap.isEmpty())
    }

    @Test
    fun `toFlagMap returns empty map when featureFlags list is empty`() {
        val response = FeatureFlagsResponse(
            data = FeatureFlagsData(featureFlags = emptyList()),
            success = true
        )
        
        val flagMap = response.toFlagMap()
        
        assertTrue(flagMap.isEmpty())
    }

    @Test
    fun `toFlagMap correctly maps Boolean type flags`() {
        val response = FeatureFlagsResponse(
            data = FeatureFlagsData(
                featureFlags = listOf(
                    FeatureFlagItem(id = "1", key = "dark_mode", name = "Dark Mode", type = "Boolean", value = true)
                )
            ),
            success = true
        )
        
        val flagMap = response.toFlagMap()
        
        assertEquals(1, flagMap.size)
        val flag = flagMap["dark_mode"]!!
        assertEquals("dark_mode", flag.code)
        assertEquals(FlagType.Boolean, flag.type)
        assertEquals(true, flag.value)
        assertFalse(flag.isDefault)
    }

    @Test
    fun `toFlagMap correctly maps String type flags`() {
        val response = FeatureFlagsResponse(
            data = FeatureFlagsData(
                featureFlags = listOf(
                    FeatureFlagItem(id = "1", key = "theme", name = "Theme", type = "String", value = "dark")
                )
            ),
            success = true
        )
        
        val flagMap = response.toFlagMap()
        
        assertEquals(1, flagMap.size)
        val flag = flagMap["theme"]!!
        assertEquals("theme", flag.code)
        assertEquals(FlagType.String, flag.type)
        assertEquals("dark", flag.value)
        assertFalse(flag.isDefault)
    }

    @Test
    fun `toFlagMap correctly maps Integer type flags`() {
        val response = FeatureFlagsResponse(
            data = FeatureFlagsData(
                featureFlags = listOf(
                    FeatureFlagItem(id = "1", key = "max_items", name = "Max Items", type = "Integer", value = 100)
                )
            ),
            success = true
        )
        
        val flagMap = response.toFlagMap()
        
        assertEquals(1, flagMap.size)
        val flag = flagMap["max_items"]!!
        assertEquals("max_items", flag.code)
        assertEquals(FlagType.Integer, flag.type)
        assertEquals(100, flag.value)
        assertFalse(flag.isDefault)
    }

    @Test
    fun `toFlagMap correctly maps multiple flags`() {
        val response = FeatureFlagsResponse(
            data = FeatureFlagsData(
                featureFlags = listOf(
                    FeatureFlagItem(id = "1", key = "bool_flag", type = "Boolean", value = true),
                    FeatureFlagItem(id = "2", key = "string_flag", type = "String", value = "test"),
                    FeatureFlagItem(id = "3", key = "int_flag", type = "Integer", value = 42)
                )
            ),
            success = true
        )
        
        val flagMap = response.toFlagMap()
        
        assertEquals(3, flagMap.size)
        
        assertTrue(flagMap.containsKey("bool_flag"))
        assertEquals(FlagType.Boolean, flagMap["bool_flag"]?.type)
        assertEquals(true, flagMap["bool_flag"]?.value)
        
        assertTrue(flagMap.containsKey("string_flag"))
        assertEquals(FlagType.String, flagMap["string_flag"]?.type)
        assertEquals("test", flagMap["string_flag"]?.value)
        
        assertTrue(flagMap.containsKey("int_flag"))
        assertEquals(FlagType.Integer, flagMap["int_flag"]?.type)
        assertEquals(42, flagMap["int_flag"]?.value)
    }

    @Test
    fun `toFlagMap skips flags with null key`() {
        val response = FeatureFlagsResponse(
            data = FeatureFlagsData(
                featureFlags = listOf(
                    FeatureFlagItem(id = "1", key = null, type = "Boolean", value = true),
                    FeatureFlagItem(id = "2", key = "valid_flag", type = "Boolean", value = false)
                )
            ),
            success = true
        )
        
        val flagMap = response.toFlagMap()
        
        assertEquals(1, flagMap.size)
        assertTrue(flagMap.containsKey("valid_flag"))
    }

    @Test
    fun `toFlagMap skips flags with null value`() {
        val response = FeatureFlagsResponse(
            data = FeatureFlagsData(
                featureFlags = listOf(
                    FeatureFlagItem(id = "1", key = "null_value_flag", type = "Boolean", value = null),
                    FeatureFlagItem(id = "2", key = "valid_flag", type = "Boolean", value = true)
                )
            ),
            success = true
        )
        
        val flagMap = response.toFlagMap()
        
        assertEquals(1, flagMap.size)
        assertTrue(flagMap.containsKey("valid_flag"))
        assertFalse(flagMap.containsKey("null_value_flag"))
    }

    @Test
    fun `toFlagMap skips flags with unknown type`() {
        val response = FeatureFlagsResponse(
            data = FeatureFlagsData(
                featureFlags = listOf(
                    FeatureFlagItem(id = "1", key = "unknown_type", type = "Unknown", value = "test"),
                    FeatureFlagItem(id = "2", key = "valid_flag", type = "String", value = "valid")
                )
            ),
            success = true
        )
        
        val flagMap = response.toFlagMap()
        
        assertEquals(1, flagMap.size)
        assertTrue(flagMap.containsKey("valid_flag"))
        assertFalse(flagMap.containsKey("unknown_type"))
    }

    @Test
    fun `toFlagMap skips flags with null type`() {
        val response = FeatureFlagsResponse(
            data = FeatureFlagsData(
                featureFlags = listOf(
                    FeatureFlagItem(id = "1", key = "null_type", type = null, value = "test"),
                    FeatureFlagItem(id = "2", key = "valid_flag", type = "String", value = "valid")
                )
            ),
            success = true
        )
        
        val flagMap = response.toFlagMap()
        
        assertEquals(1, flagMap.size)
        assertTrue(flagMap.containsKey("valid_flag"))
    }

    @Test
    fun `toFlagMap is case-sensitive for type names`() {
        val response = FeatureFlagsResponse(
            data = FeatureFlagsData(
                featureFlags = listOf(
                    FeatureFlagItem(id = "1", key = "lowercase_bool", type = "boolean", value = true),
                    FeatureFlagItem(id = "2", key = "uppercase_bool", type = "BOOLEAN", value = true),
                    FeatureFlagItem(id = "3", key = "correct_bool", type = "Boolean", value = true)
                )
            ),
            success = true
        )
        
        val flagMap = response.toFlagMap()
        
        // Only "Boolean" (exact case) should be recognized
        assertEquals(1, flagMap.size)
        assertTrue(flagMap.containsKey("correct_bool"))
    }

    @Test
    fun `toFlagMap returns empty map when all flags are invalid`() {
        val response = FeatureFlagsResponse(
            data = FeatureFlagsData(
                featureFlags = listOf(
                    FeatureFlagItem(id = "1", key = null, type = "Boolean", value = true),
                    FeatureFlagItem(id = "2", key = "no_value", type = "Boolean", value = null),
                    FeatureFlagItem(id = "3", key = "bad_type", type = "invalid", value = "test")
                )
            ),
            success = true
        )
        
        val flagMap = response.toFlagMap()
        
        assertTrue(flagMap.isEmpty())
    }

    @Test
    fun `default FeatureFlagsResponse has null data and false success`() {
        val response = FeatureFlagsResponse()
        
        assertNull(response.data)
        assertFalse(response.success)
    }

    // ============================================
    // FeatureFlagsData Tests
    // ============================================

    @Test
    fun `FeatureFlagsData stores featureFlags correctly`() {
        val flags = listOf(
            FeatureFlagItem(key = "flag1"),
            FeatureFlagItem(key = "flag2")
        )
        val data = FeatureFlagsData(featureFlags = flags)
        
        assertEquals(2, data.featureFlags.size)
        assertEquals("flag1", data.featureFlags[0].key)
        assertEquals("flag2", data.featureFlags[1].key)
    }

    @Test
    fun `default FeatureFlagsData has empty featureFlags list`() {
        val data = FeatureFlagsData()
        assertTrue(data.featureFlags.isEmpty())
    }

    // ============================================
    // FeatureFlagItem Tests
    // ============================================

    @Test
    fun `FeatureFlagItem stores all fields correctly`() {
        val item = FeatureFlagItem(
            id = "flag_123",
            key = "dark_mode",
            name = "Dark Mode",
            type = "Boolean",
            value = true
        )
        
        assertEquals("flag_123", item.id)
        assertEquals("dark_mode", item.key)
        assertEquals("Dark Mode", item.name)
        assertEquals("Boolean", item.type)
        assertEquals(true, item.value)
    }

    @Test
    fun `FeatureFlagItem can have null id`() {
        val item = FeatureFlagItem(id = null, key = "flag", type = "Boolean", value = true)
        assertNull(item.id)
    }

    @Test
    fun `FeatureFlagItem can have null key`() {
        val item = FeatureFlagItem(id = "1", key = null, type = "Boolean", value = true)
        assertNull(item.key)
    }

    @Test
    fun `FeatureFlagItem can have null name`() {
        val item = FeatureFlagItem(id = "1", key = "flag", name = null, type = "Boolean", value = true)
        assertNull(item.name)
    }

    @Test
    fun `FeatureFlagItem can have null type`() {
        val item = FeatureFlagItem(id = "1", key = "flag", type = null, value = true)
        assertNull(item.type)
    }

    @Test
    fun `FeatureFlagItem can have null value`() {
        val item = FeatureFlagItem(id = "1", key = "flag", type = "Boolean", value = null)
        assertNull(item.value)
    }

    @Test
    fun `default FeatureFlagItem has all null fields`() {
        val item = FeatureFlagItem()
        assertNull(item.id)
        assertNull(item.key)
        assertNull(item.name)
        assertNull(item.type)
        assertNull(item.value)
    }

    @Test
    fun `FeatureFlagItem can store Boolean value`() {
        val item = FeatureFlagItem(key = "flag", type = "Boolean", value = true)
        assertEquals(true, item.value)
    }

    @Test
    fun `FeatureFlagItem can store String value`() {
        val item = FeatureFlagItem(key = "flag", type = "String", value = "hello")
        assertEquals("hello", item.value)
    }

    @Test
    fun `FeatureFlagItem can store Integer value`() {
        val item = FeatureFlagItem(key = "flag", type = "Integer", value = 42)
        assertEquals(42, item.value)
    }

    @Test
    fun `FeatureFlagItem equality works correctly`() {
        val item1 = FeatureFlagItem(id = "1", key = "flag", type = "Boolean", value = true)
        val item2 = FeatureFlagItem(id = "1", key = "flag", type = "Boolean", value = true)
        
        assertEquals(item1, item2)
        assertEquals(item1.hashCode(), item2.hashCode())
    }

    @Test
    fun `FeatureFlagItem copy works correctly`() {
        val original = FeatureFlagItem(id = "1", key = "flag", type = "Boolean", value = true)
        val copied = original.copy(value = false)
        
        assertEquals("1", copied.id)
        assertEquals("flag", copied.key)
        assertEquals("Boolean", copied.type)
        assertEquals(false, copied.value)
    }
}

