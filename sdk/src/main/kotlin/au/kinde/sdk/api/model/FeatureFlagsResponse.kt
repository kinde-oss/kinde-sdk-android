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
    val success: Boolean? = null
) {
    /**
     * Convert the API response to a map of flag keys to Flag objects
     */
    fun toFlagMap(): Map<String, Flag> {
        val flags = this.data?.featureFlags ?: return emptyMap()
        return flags.mapNotNull { item ->
            val key = item.key ?: return@mapNotNull null
            val value = item.value ?: return@mapNotNull null
            val flagType = when (item.type) {
                "Boolean" -> FlagType.Boolean
                "String" -> FlagType.String
                "Integer" -> FlagType.Integer
                else -> null
            }
            key to Flag(key, flagType, value, false)
        }.toMap()
    }
}

data class FeatureFlagsData(
    @SerializedName("feature_flags")
    val featureFlags: List<FeatureFlagItem>? = null
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
