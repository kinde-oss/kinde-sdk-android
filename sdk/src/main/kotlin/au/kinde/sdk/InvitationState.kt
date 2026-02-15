package au.kinde.sdk

/**
 * Centralized state holder for invitation code handling.
 * Thread-safe holder that manages invitation processing state to prevent
 * duplicate handling and coordinate state across multiple entry points.
 */
class InvitationState {
    @Volatile
    private var _isHandling = false
    
    @Volatile
    private var _processedCode: String? = null
    
    /**
     * Check if an invitation is currently being handled.
     */
    val isHandling: Boolean
        get() = _isHandling
    
    /**
     * Get the currently processed invitation code.
     */
    val processedCode: String?
        get() = _processedCode
    
    /**
     * Attempt to start handling an invitation code.
     * 
     * @param code The invitation code to process
     * @return true if handling was started (code is new), false if already processed
     */
    @Synchronized
    fun startHandling(code: String): Boolean {
        if (_processedCode == code) {
            return false
        }
        _processedCode = code
        _isHandling = true
        return true
    }
    
    /**
     * Mark invitation handling as complete.
     */
    @Synchronized
    fun completeHandling() {
        _isHandling = false
    }
    
    /**
     * Reset all invitation state (for logout or error scenarios).
     */
    @Synchronized
    fun reset() {
        _isHandling = false
        _processedCode = null
    }
}
