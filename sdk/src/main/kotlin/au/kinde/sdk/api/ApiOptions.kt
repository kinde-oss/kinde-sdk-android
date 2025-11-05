package au.kinde.sdk.api

/**
 * Configuration options for API calls
 * @param forceApi When true, forces the SDK to fetch data from the API instead of token claims
 */
data class ApiOptions(
    val forceApi: Boolean = false
)