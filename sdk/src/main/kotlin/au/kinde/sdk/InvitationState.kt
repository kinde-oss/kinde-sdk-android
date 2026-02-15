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
    
    val isHandling: Boolean
        get() = _isHandling
    
    val processedCode: String?
        get() = _processedCode
    
    @Synchronized
    fun startHandling(code: String): Boolean {
        if (_processedCode == code) {
            return false
        }
        _processedCode = code
        _isHandling = true
        return true
    }

    @Synchronized
    fun completeHandling() {
        _isHandling = false
    }
    
    @Synchronized
    fun reset() {
        _isHandling = false
        _processedCode = null
    }
}
