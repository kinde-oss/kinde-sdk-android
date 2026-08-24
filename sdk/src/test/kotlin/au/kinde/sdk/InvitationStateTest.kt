package au.kinde.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InvitationStateTest {

    @Test
    fun `startHandling marks code as processed and handling`() {
        val state = InvitationState()

        state.startHandling("code-1")

        assertTrue(state.isHandling)
        assertTrue(state.isProcessed("code-1"))
        assertEquals("code-1", state.processedCode)
    }

    @Test
    fun `different code is not considered processed`() {
        val state = InvitationState()

        state.startHandling("code-1")

        assertFalse(state.isProcessed("code-2"))
    }

    @Test
    fun `completeHandling clears handling flag but keeps processed code`() {
        val state = InvitationState()
        state.startHandling("code-1")

        state.completeHandling()

        assertFalse(state.isHandling)
        assertTrue(state.isProcessed("code-1"))
    }

    @Test
    fun `reset clears both handling flag and processed code`() {
        val state = InvitationState()
        state.startHandling("code-1")

        state.reset()

        assertFalse(state.isHandling)
        assertFalse(state.isProcessed("code-1"))
        assertNull(state.processedCode)
    }
}
