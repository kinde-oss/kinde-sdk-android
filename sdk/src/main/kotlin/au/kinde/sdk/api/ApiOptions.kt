package au.kinde.sdk.api

/**
 * Configuration options for API calls
 * @param forceApi When true, forces the SDK to fetch data from the API instead of token claims
 * @param useCache When true (default), uses cached API responses within the TTL window to avoid redundant calls
 */
data class ApiOptions(
    val forceApi: Boolean = false,
    val useCache: Boolean = true
)
