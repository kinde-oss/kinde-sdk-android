package au.kinde.sdk

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Unit tests for InvitationState to ensure thread-safe invitation handling.
 */
class InvitationStateTest {

    private lateinit var invitationState: InvitationState

    @Before
    fun setup() {
        invitationState = InvitationState()
    }

    @Test
    fun `initial state should not be handling`() {
        assertFalse(invitationState.isHandling)
        assertNull(invitationState.processedCode)
    }

    @Test
    fun `startHandling with new code should return true and update state`() {
        val code = "test-invitation-123"
        val result = invitationState.startHandling(code)
        
        assertTrue(result)
        assertTrue(invitationState.isHandling)
        assertEquals(code, invitationState.processedCode)
    }

    @Test
    fun `startHandling with same code twice should return false second time`() {
        val code = "test-invitation-123"
        
        // First call should succeed
        val firstResult = invitationState.startHandling(code)
        assertTrue(firstResult)
        
        // Second call with same code should fail (already processed)
        val secondResult = invitationState.startHandling(code)
        assertFalse(secondResult)
        
        // State should remain unchanged
        assertTrue(invitationState.isHandling)
        assertEquals(code, invitationState.processedCode)
    }

    @Test
    fun `startHandling with different code should succeed after first code`() {
        val firstCode = "invitation-1"
        val secondCode = "invitation-2"
        
        invitationState.startHandling(firstCode)
        assertEquals(firstCode, invitationState.processedCode)
        
        // Different code should be accepted and replace the first
        val result = invitationState.startHandling(secondCode)
        assertTrue(result)
        assertEquals(secondCode, invitationState.processedCode)
        assertTrue(invitationState.isHandling)
    }

    @Test
    fun `completeHandling should set isHandling to false but preserve processedCode`() {
        val code = "test-invitation-123"
        invitationState.startHandling(code)
        
        invitationState.completeHandling()
        
        assertFalse(invitationState.isHandling)
        assertEquals(code, invitationState.processedCode)
    }

    @Test
    fun `reset should clear all state`() {
        val code = "test-invitation-123"
        invitationState.startHandling(code)
        
        invitationState.reset()
        
        assertFalse(invitationState.isHandling)
        assertNull(invitationState.processedCode)
    }

    @Test
    fun `startHandling after reset should accept same code again`() {
        val code = "test-invitation-123"
        
        // First handling
        invitationState.startHandling(code)
        assertTrue(invitationState.isHandling)
        
        // Reset
        invitationState.reset()
        
        // Same code should be accepted after reset
        val result = invitationState.startHandling(code)
        assertTrue(result)
        assertTrue(invitationState.isHandling)
        assertEquals(code, invitationState.processedCode)
    }

    @Test
    fun `startHandling after completeHandling should reject same code`() {
        val code = "test-invitation-123"
        
        // First handling
        invitationState.startHandling(code)
        invitationState.completeHandling()
        
        // Same code should still be rejected (not reset)
        val result = invitationState.startHandling(code)
        assertFalse(result)
        assertFalse(invitationState.isHandling)
    }

    @Test
    fun `concurrent startHandling calls should be thread-safe`() {
        val code = "concurrent-test"
        val threadCount = 10
        val latch = CountDownLatch(threadCount)
        val results = mutableListOf<Boolean>()
        val resultsLock = Object()

        repeat(threadCount) {
            thread {
                val result = invitationState.startHandling(code)
                synchronized(resultsLock) {
                    results.add(result)
                }
                latch.countDown()
            }
        }

        latch.await(5, TimeUnit.SECONDS)

        // Only one thread should have succeeded
        val successCount = results.count { it }
        assertEquals("Only one thread should successfully start handling", 1, successCount)
        assertEquals(code, invitationState.processedCode)
        assertTrue(invitationState.isHandling)
    }

    @Test
    fun `concurrent startHandling with different codes should handle race conditions`() {
        val codes = listOf("code-1", "code-2", "code-3", "code-4", "code-5")
        val threadCount = codes.size
        val latch = CountDownLatch(threadCount)
        val results = mutableListOf<Pair<String, Boolean>>()
        val resultsLock = Object()

        codes.forEachIndexed { index, code ->
            thread {
                Thread.sleep(index * 10L) // Stagger threads slightly
                val result = invitationState.startHandling(code)
                synchronized(resultsLock) {
                    results.add(code to result)
                }
                latch.countDown()
            }
        }

        latch.await(5, TimeUnit.SECONDS)

        // All codes should have been processed in order, only first should succeed
        val successResults = results.filter { it.second }
        assertTrue("At least one code should succeed", successResults.isNotEmpty())
        
        // The processed code should be one of the codes we submitted
        assertNotNull(invitationState.processedCode)
        assertTrue(codes.contains(invitationState.processedCode))
    }

    @Test
    fun `concurrent completeHandling and startHandling should be thread-safe`() {
        val code = "race-condition-test"
        invitationState.startHandling(code)
        
        val threadCount = 20
        val latch = CountDownLatch(threadCount)
        val exceptions = mutableListOf<Exception>()
        val exceptionsLock = Object()

        repeat(threadCount) { index ->
            thread {
                try {
                    if (index % 2 == 0) {
                        invitationState.completeHandling()
                    } else {
                        invitationState.startHandling("new-code-$index")
                    }
                } catch (e: Exception) {
                    synchronized(exceptionsLock) {
                        exceptions.add(e)
                    }
                }
                latch.countDown()
            }
        }

        latch.await(5, TimeUnit.SECONDS)

        // No exceptions should be thrown
        assertTrue("No exceptions should occur during concurrent operations", exceptions.isEmpty())
        
        // State should be consistent
        assertNotNull(invitationState.processedCode)
    }

    @Test
    fun `reset during active handling should clear state safely`() {
        val code = "test-invitation"
        invitationState.startHandling(code)
        assertTrue(invitationState.isHandling)
        
        invitationState.reset()
        
        assertFalse(invitationState.isHandling)
        assertNull(invitationState.processedCode)
    }
}
