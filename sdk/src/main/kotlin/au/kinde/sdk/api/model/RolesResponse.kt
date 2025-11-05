@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package au.kinde.sdk.api.model

import com.google.gson.annotations.SerializedName

/**
 * Response from the roles API endpoint
 *
 * @param data The data wrapper containing roles
 */
data class RolesResponse(
    @SerializedName("data")
    val data: RolesData? = null,
    
    @SerializedName("success")
    val success: Boolean = false
) {
    /**
     * Check if the response is valid
     * @return true if success is true and data is present
     */
    fun isValid(): Boolean = success && data != null
    /**
     * Extract role keys from the API response
     * 
     * @return List of role keys. Roles with null keys are skipped.
     */
    fun getRoleKeys(): List<String> {
        if (!success) {
            android.util.Log.w("KindeSDK", "Roles API returned success=false")
            return emptyList()
        }
        val roles = this.data?.roles ?: emptyList()
        val nullKeyCount = roles.count { it.key == null }
        if (nullKeyCount > 0) {
            android.util.Log.w("KindeSDK", "Dropping $nullKeyCount role(s) with null keys")
        }
        return roles.mapNotNull { it.key }
    }
}

data class RolesData(
    @SerializedName("org_code")
    val orgCode: String? = null,

    @SerializedName("roles")
    val roles: List<RoleData> = emptyList()
)

/**
 * Individual role data
 *
 * @param id Role ID
 * @param key Role key/name
 */
data class RoleData(
    @SerializedName("id")
    val id: String? = null,

    @SerializedName("key")
    val key: String? = null,
    
    @SerializedName("name")
    val name: String? = null
)
