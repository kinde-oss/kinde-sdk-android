package au.kinde.sdk.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [UserDetails] data class.
 */
class UserDetailsTest {

    @Test
    fun `UserDetails stores all fields correctly`() {
        val userDetails = UserDetails(
            id = "user_123",
            givenName = "John",
            familyName = "Doe",
            email = "john@example.com",
            picture = "https://example.com/photo.jpg"
        )

        assertEquals("user_123", userDetails.id)
        assertEquals("John", userDetails.givenName)
        assertEquals("Doe", userDetails.familyName)
        assertEquals("john@example.com", userDetails.email)
        assertEquals("https://example.com/photo.jpg", userDetails.picture)
    }

    @Test
    fun `UserDetails can have empty strings`() {
        val userDetails = UserDetails(
            id = "",
            givenName = "",
            familyName = "",
            email = "",
            picture = ""
        )

        assertEquals("", userDetails.id)
        assertEquals("", userDetails.givenName)
        assertEquals("", userDetails.familyName)
        assertEquals("", userDetails.email)
        assertEquals("", userDetails.picture)
    }

    @Test
    fun `UserDetails equality works correctly`() {
        val user1 = UserDetails(
            id = "user_123",
            givenName = "John",
            familyName = "Doe",
            email = "john@example.com",
            picture = "https://example.com/photo.jpg"
        )
        val user2 = UserDetails(
            id = "user_123",
            givenName = "John",
            familyName = "Doe",
            email = "john@example.com",
            picture = "https://example.com/photo.jpg"
        )

        assertEquals(user1, user2)
        assertEquals(user1.hashCode(), user2.hashCode())
    }

    @Test
    fun `UserDetails inequality when id differs`() {
        val user1 = UserDetails("user_1", "John", "Doe", "john@example.com", "")
        val user2 = UserDetails("user_2", "John", "Doe", "john@example.com", "")

        assertFalse(user1 == user2)
    }

    @Test
    fun `UserDetails inequality when givenName differs`() {
        val user1 = UserDetails("user_1", "John", "Doe", "john@example.com", "")
        val user2 = UserDetails("user_1", "Jane", "Doe", "john@example.com", "")

        assertFalse(user1 == user2)
    }

    @Test
    fun `UserDetails inequality when familyName differs`() {
        val user1 = UserDetails("user_1", "John", "Doe", "john@example.com", "")
        val user2 = UserDetails("user_1", "John", "Smith", "john@example.com", "")

        assertFalse(user1 == user2)
    }

    @Test
    fun `UserDetails inequality when email differs`() {
        val user1 = UserDetails("user_1", "John", "Doe", "john@example.com", "")
        val user2 = UserDetails("user_1", "John", "Doe", "jane@example.com", "")

        assertFalse(user1 == user2)
    }

    @Test
    fun `UserDetails inequality when picture differs`() {
        val user1 = UserDetails("user_1", "John", "Doe", "john@example.com", "pic1.jpg")
        val user2 = UserDetails("user_1", "John", "Doe", "john@example.com", "pic2.jpg")

        assertFalse(user1 == user2)
    }

    @Test
    fun `UserDetails copy works correctly`() {
        val original = UserDetails(
            id = "user_123",
            givenName = "John",
            familyName = "Doe",
            email = "john@example.com",
            picture = "https://example.com/photo.jpg"
        )
        
        val copied = original.copy(givenName = "Jane")

        assertEquals("user_123", copied.id)
        assertEquals("Jane", copied.givenName)
        assertEquals("Doe", copied.familyName)
        assertEquals("john@example.com", copied.email)
        assertEquals("https://example.com/photo.jpg", copied.picture)
    }

    @Test
    fun `UserDetails toString contains all properties`() {
        val userDetails = UserDetails(
            id = "user_123",
            givenName = "John",
            familyName = "Doe",
            email = "john@example.com",
            picture = "pic.jpg"
        )
        
        val string = userDetails.toString()

        assertTrue(string.contains("user_123"))
        assertTrue(string.contains("John"))
        assertTrue(string.contains("Doe"))
        assertTrue(string.contains("john@example.com"))
        assertTrue(string.contains("pic.jpg"))
    }

    @Test
    fun `UserDetails component functions work correctly`() {
        val userDetails = UserDetails(
            id = "user_123",
            givenName = "John",
            familyName = "Doe",
            email = "john@example.com",
            picture = "pic.jpg"
        )

        val (id, givenName, familyName, email, picture) = userDetails

        assertEquals("user_123", id)
        assertEquals("John", givenName)
        assertEquals("Doe", familyName)
        assertEquals("john@example.com", email)
        assertEquals("pic.jpg", picture)
    }
}

