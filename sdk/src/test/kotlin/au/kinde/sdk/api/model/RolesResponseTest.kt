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
 * Unit tests for [RolesResponse] and related data classes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RolesResponseTest {

    // ============================================
    // RolesResponse Tests
    // ============================================

    @Test
    fun `isValid returns true when success is true and data is present`() {
        val response = RolesResponse(
            data = RolesData(orgCode = "org_123"),
            success = true
        )
        assertTrue(response.isValid())
    }

    @Test
    fun `isValid returns false when success is false`() {
        val response = RolesResponse(
            data = RolesData(orgCode = "org_123"),
            success = false
        )
        assertFalse(response.isValid())
    }

    @Test
    fun `isValid returns false when data is null`() {
        val response = RolesResponse(
            data = null,
            success = true
        )
        assertFalse(response.isValid())
    }

    @Test
    fun `isValid returns false when both success is false and data is null`() {
        val response = RolesResponse(
            data = null,
            success = false
        )
        assertFalse(response.isValid())
    }

    @Test
    fun `getRoleKeys returns empty list when success is false`() {
        val response = RolesResponse(
            data = RolesData(
                orgCode = "org_123",
                roles = listOf(
                    RoleData(id = "1", key = "admin", name = "Administrator")
                )
            ),
            success = false
        )
        
        val keys = response.getRoleKeys()
        
        assertTrue(keys.isEmpty())
    }

    @Test
    fun `getRoleKeys returns empty list when data is null`() {
        val response = RolesResponse(
            data = null,
            success = true
        )
        
        val keys = response.getRoleKeys()
        
        assertTrue(keys.isEmpty())
    }

    @Test
    fun `getRoleKeys returns all keys when all roles have keys`() {
        val response = RolesResponse(
            data = RolesData(
                orgCode = "org_123",
                roles = listOf(
                    RoleData(id = "1", key = "admin", name = "Administrator"),
                    RoleData(id = "2", key = "editor", name = "Editor"),
                    RoleData(id = "3", key = "viewer", name = "Viewer")
                )
            ),
            success = true
        )
        
        val keys = response.getRoleKeys()
        
        assertEquals(3, keys.size)
        assertTrue(keys.contains("admin"))
        assertTrue(keys.contains("editor"))
        assertTrue(keys.contains("viewer"))
    }

    @Test
    fun `getRoleKeys filters out roles with null keys`() {
        val response = RolesResponse(
            data = RolesData(
                orgCode = "org_123",
                roles = listOf(
                    RoleData(id = "1", key = "admin", name = "Administrator"),
                    RoleData(id = "2", key = null, name = "Invalid Role"),
                    RoleData(id = "3", key = "editor", name = "Editor")
                )
            ),
            success = true
        )
        
        val keys = response.getRoleKeys()
        
        assertEquals(2, keys.size)
        assertTrue(keys.contains("admin"))
        assertTrue(keys.contains("editor"))
    }

    @Test
    fun `getRoleKeys returns empty list when all roles have null keys`() {
        val response = RolesResponse(
            data = RolesData(
                orgCode = "org_123",
                roles = listOf(
                    RoleData(id = "1", key = null, name = "Invalid 1"),
                    RoleData(id = "2", key = null, name = "Invalid 2")
                )
            ),
            success = true
        )
        
        val keys = response.getRoleKeys()
        
        assertTrue(keys.isEmpty())
    }

    @Test
    fun `getRoleKeys returns empty list when roles list is empty`() {
        val response = RolesResponse(
            data = RolesData(
                orgCode = "org_123",
                roles = emptyList()
            ),
            success = true
        )
        
        val keys = response.getRoleKeys()
        
        assertTrue(keys.isEmpty())
    }

    @Test
    fun `default RolesResponse has null data and false success`() {
        val response = RolesResponse()
        
        assertNull(response.data)
        assertFalse(response.success)
    }

    // ============================================
    // RolesData Tests
    // ============================================

    @Test
    fun `RolesData stores orgCode correctly`() {
        val data = RolesData(orgCode = "org_abc123")
        assertEquals("org_abc123", data.orgCode)
    }

    @Test
    fun `RolesData stores roles list correctly`() {
        val roles = listOf(
            RoleData(id = "1", key = "admin"),
            RoleData(id = "2", key = "user")
        )
        val data = RolesData(orgCode = "org_123", roles = roles)
        
        assertEquals(2, data.roles.size)
        assertEquals("admin", data.roles[0].key)
        assertEquals("user", data.roles[1].key)
    }

    @Test
    fun `RolesData has empty roles list by default`() {
        val data = RolesData(orgCode = "org_123")
        assertTrue(data.roles.isEmpty())
    }

    @Test
    fun `RolesData can have null orgCode`() {
        val data = RolesData(orgCode = null)
        assertNull(data.orgCode)
    }

    @Test
    fun `default RolesData has null orgCode and empty roles`() {
        val data = RolesData()
        assertNull(data.orgCode)
        assertTrue(data.roles.isEmpty())
    }

    // ============================================
    // RoleData Tests
    // ============================================

    @Test
    fun `RoleData stores all fields correctly`() {
        val role = RoleData(
            id = "role_123",
            key = "administrator",
            name = "Administrator"
        )
        
        assertEquals("role_123", role.id)
        assertEquals("administrator", role.key)
        assertEquals("Administrator", role.name)
    }

    @Test
    fun `RoleData can have null id`() {
        val role = RoleData(id = null, key = "admin", name = "Admin")
        assertNull(role.id)
    }

    @Test
    fun `RoleData can have null key`() {
        val role = RoleData(id = "1", key = null, name = "Invalid")
        assertNull(role.key)
    }

    @Test
    fun `RoleData can have null name`() {
        val role = RoleData(id = "1", key = "admin", name = null)
        assertNull(role.name)
    }

    @Test
    fun `default RoleData has all null fields`() {
        val role = RoleData()
        assertNull(role.id)
        assertNull(role.key)
        assertNull(role.name)
    }

    @Test
    fun `RoleData equality works correctly`() {
        val role1 = RoleData(id = "1", key = "admin", name = "Administrator")
        val role2 = RoleData(id = "1", key = "admin", name = "Administrator")
        
        assertEquals(role1, role2)
        assertEquals(role1.hashCode(), role2.hashCode())
    }

    @Test
    fun `RoleData copy works correctly`() {
        val original = RoleData(id = "1", key = "admin", name = "Administrator")
        val copied = original.copy(key = "super_admin")
        
        assertEquals("1", copied.id)
        assertEquals("super_admin", copied.key)
        assertEquals("Administrator", copied.name)
    }

    @Test
    fun `RoleData inequality when keys differ`() {
        val role1 = RoleData(id = "1", key = "admin", name = "Admin")
        val role2 = RoleData(id = "1", key = "user", name = "Admin")
        
        assertFalse(role1 == role2)
    }

    @Test
    fun `multiple roles with same key are treated as equal`() {
        val role1 = RoleData(id = "1", key = "admin", name = "Admin")
        val role2 = RoleData(id = "1", key = "admin", name = "Admin")
        
        assertEquals(role1, role2)
    }
}

