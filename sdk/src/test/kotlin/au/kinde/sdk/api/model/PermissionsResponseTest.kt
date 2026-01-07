package au.kinde.sdk.api.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [PermissionsResponse] and related data classes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PermissionsResponseTest {

    // ============================================
    // PermissionsResponse Tests
    // ============================================

    @Test
    fun `isValid returns true when success is true and data is present`() {
        val response = PermissionsResponse(
            data = PermissionsData(orgCode = "org_123"),
            success = true
        )
        assertTrue(response.isValid())
    }

    @Test
    fun `isValid returns false when success is false`() {
        val response = PermissionsResponse(
            data = PermissionsData(orgCode = "org_123"),
            success = false
        )
        assertFalse(response.isValid())
    }

    @Test
    fun `isValid returns false when data is null`() {
        val response = PermissionsResponse(
            data = null,
            success = true
        )
        assertFalse(response.isValid())
    }

    @Test
    fun `isValid returns false when both success is false and data is null`() {
        val response = PermissionsResponse(
            data = null,
            success = false
        )
        assertFalse(response.isValid())
    }

    @Test
    fun `getPermissionKeys returns empty list when success is false`() {
        val response = PermissionsResponse(
            data = PermissionsData(
                orgCode = "org_123",
                permissions = listOf(
                    PermissionItem(id = "1", key = "read:users", name = "Read Users")
                )
            ),
            success = false
        )
        
        val keys = response.getPermissionKeys()
        
        assertTrue(keys.isEmpty())
    }

    @Test
    fun `getPermissionKeys returns empty list when data is null`() {
        val response = PermissionsResponse(
            data = null,
            success = true
        )
        
        val keys = response.getPermissionKeys()
        
        assertTrue(keys.isEmpty())
    }

    @Test
    fun `getPermissionKeys returns all keys when all permissions have keys`() {
        val response = PermissionsResponse(
            data = PermissionsData(
                orgCode = "org_123",
                permissions = listOf(
                    PermissionItem(id = "1", key = "read:users", name = "Read Users"),
                    PermissionItem(id = "2", key = "write:users", name = "Write Users"),
                    PermissionItem(id = "3", key = "delete:users", name = "Delete Users")
                )
            ),
            success = true
        )
        
        val keys = response.getPermissionKeys()
        
        assertEquals(3, keys.size)
        assertTrue(keys.contains("read:users"))
        assertTrue(keys.contains("write:users"))
        assertTrue(keys.contains("delete:users"))
    }

    @Test
    fun `getPermissionKeys filters out permissions with null keys`() {
        val response = PermissionsResponse(
            data = PermissionsData(
                orgCode = "org_123",
                permissions = listOf(
                    PermissionItem(id = "1", key = "read:users", name = "Read Users"),
                    PermissionItem(id = "2", key = null, name = "Invalid Permission"),
                    PermissionItem(id = "3", key = "write:users", name = "Write Users")
                )
            ),
            success = true
        )
        
        val keys = response.getPermissionKeys()
        
        assertEquals(2, keys.size)
        assertTrue(keys.contains("read:users"))
        assertTrue(keys.contains("write:users"))
    }

    @Test
    fun `getPermissionKeys returns empty list when all permissions have null keys`() {
        val response = PermissionsResponse(
            data = PermissionsData(
                orgCode = "org_123",
                permissions = listOf(
                    PermissionItem(id = "1", key = null, name = "Invalid 1"),
                    PermissionItem(id = "2", key = null, name = "Invalid 2")
                )
            ),
            success = true
        )
        
        val keys = response.getPermissionKeys()
        
        assertTrue(keys.isEmpty())
    }

    @Test
    fun `getPermissionKeys returns empty list when permissions list is empty`() {
        val response = PermissionsResponse(
            data = PermissionsData(
                orgCode = "org_123",
                permissions = emptyList()
            ),
            success = true
        )
        
        val keys = response.getPermissionKeys()
        
        assertTrue(keys.isEmpty())
    }

    @Test
    fun `default PermissionsResponse has null data and false success`() {
        val response = PermissionsResponse()
        
        assertNull(response.data)
        assertFalse(response.success)
    }

    // ============================================
    // PermissionsData Tests
    // ============================================

    @Test
    fun `PermissionsData stores orgCode correctly`() {
        val data = PermissionsData(orgCode = "org_abc123")
        assertEquals("org_abc123", data.orgCode)
    }

    @Test
    fun `PermissionsData stores permissions list correctly`() {
        val permissions = listOf(
            PermissionItem(id = "1", key = "perm1"),
            PermissionItem(id = "2", key = "perm2")
        )
        val data = PermissionsData(orgCode = "org_123", permissions = permissions)
        
        assertEquals(2, data.permissions.size)
        assertEquals("perm1", data.permissions[0].key)
        assertEquals("perm2", data.permissions[1].key)
    }

    @Test
    fun `PermissionsData has empty permissions list by default`() {
        val data = PermissionsData(orgCode = "org_123")
        assertTrue(data.permissions.isEmpty())
    }

    @Test
    fun `PermissionsData can have null orgCode`() {
        val data = PermissionsData(orgCode = null)
        assertNull(data.orgCode)
    }

    @Test
    fun `default PermissionsData has null orgCode and empty permissions`() {
        val data = PermissionsData()
        assertNull(data.orgCode)
        assertTrue(data.permissions.isEmpty())
    }

    // ============================================
    // PermissionItem Tests
    // ============================================

    @Test
    fun `PermissionItem stores all fields correctly`() {
        val item = PermissionItem(
            id = "perm_123",
            key = "read:documents",
            name = "Read Documents"
        )
        
        assertEquals("perm_123", item.id)
        assertEquals("read:documents", item.key)
        assertEquals("Read Documents", item.name)
    }

    @Test
    fun `PermissionItem can have null id`() {
        val item = PermissionItem(id = null, key = "read:users", name = "Read Users")
        assertNull(item.id)
    }

    @Test
    fun `PermissionItem can have null key`() {
        val item = PermissionItem(id = "1", key = null, name = "Invalid")
        assertNull(item.key)
    }

    @Test
    fun `PermissionItem can have null name`() {
        val item = PermissionItem(id = "1", key = "read:users", name = null)
        assertNull(item.name)
    }

    @Test
    fun `default PermissionItem has all null fields`() {
        val item = PermissionItem()
        assertNull(item.id)
        assertNull(item.key)
        assertNull(item.name)
    }

    @Test
    fun `PermissionItem equality works correctly`() {
        val item1 = PermissionItem(id = "1", key = "read:users", name = "Read")
        val item2 = PermissionItem(id = "1", key = "read:users", name = "Read")
        
        assertEquals(item1, item2)
        assertEquals(item1.hashCode(), item2.hashCode())
    }

    @Test
    fun `PermissionItem copy works correctly`() {
        val original = PermissionItem(id = "1", key = "read:users", name = "Read")
        val copied = original.copy(key = "write:users")
        
        assertEquals("1", copied.id)
        assertEquals("write:users", copied.key)
        assertEquals("Read", copied.name)
    }
}

