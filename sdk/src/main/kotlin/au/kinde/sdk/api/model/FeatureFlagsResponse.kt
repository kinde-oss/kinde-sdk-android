@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package au.kinde.sdk.api.model

import au.kinde.sdk.model.Flag
import au.kinde.sdk.model.FlagType
import com.google.gson.annotations.SerializedName

/**
 * Response from the feature flags API endpoint
 *
 * @param data The data wrapper containing feature flags
 */
data class FeatureFlagsResponse(
    @SerializedName("data")
    val data: FeatureFlagsData? = null,
    @SerializedName("success")
    val success: Boolean = false
) {
    /**
     * Check if the response is valid
     * @return true if success is true and data is present
     */
    fun isValid(): Boolean = success && data != null
    /**
     * Convert the API response to a map of flag keys to Flag objects
     * 
     * @return Map of flag keys to Flag objects. Invalid flags (null key/value or unknown type) are skipped.
     * Valid types: "Boolean", "String", "Integer" (case-sensitive)
     */
    fun toFlagMap(): Map<String, Flag> {
        if (!success) {
            android.util.Log.w("KindeSDK", "Feature flags API returned success=false")
            return emptyMap()
        }
        val flags = this.data?.featureFlags ?: emptyList()
        return flags.mapNotNull { item ->
            val key = item.key
            if (key == null) {
                android.util.Log.w("KindeSDK", "Skipping feature flag with null key")
                return@mapNotNull null
            }
            val value = item.value
            if (value == null) {
                android.util.Log.w("KindeSDK", "Skipping feature flag '$key' with null value")
                return@mapNotNull null
            }
            val flagType = when (item.type) {
                "Boolean" -> FlagType.Boolean
                "String" -> FlagType.String
                "Integer" -> FlagType.Integer
                else -> {
                    android.util.Log.w("KindeSDK", "Unknown flag type '${item.type}' for flag '$key'")
                    return@mapNotNull null
                }
            }
            key to Flag(key, flagType, value, false)
        }.toMap()
    }
}

data class FeatureFlagsData(
    @SerializedName("feature_flags")
    val featureFlags: List<FeatureFlagItem> = emptyList()
)

data class FeatureFlagItem(
    @SerializedName("id")
    val id: String? = null,
    @SerializedName("key")
    val key: String? = null,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("type")
    val type: String? = null,
    @SerializedName("value")
    val value: Any? = null
)
