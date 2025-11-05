@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package au.kinde.sdk.api.model

import com.google.gson.annotations.SerializedName

/**
 * Response from the permissions API endpoint
 *
 * @param data The data wrapper containing permissions
 */
data class PermissionsResponse(
    @SerializedName("data")
    val data: PermissionsData? = null,
    
    @SerializedName("success")
    val success: Boolean = false
) {
    /**
     * Check if the response is valid
     * @return true if success is true and data is present
     */
    fun isValid(): Boolean = success && data != null
    /**
     * Extract permission keys from the API response
     * 
     * @return List of permission keys. Permissions with null keys are skipped.
     */
    fun getPermissionKeys(): List<String> {
        if (!success) {
            android.util.Log.w("KindeSDK", "Permissions API returned success=false")
            return emptyList()
        }
        val permissions = this.data?.permissions ?: emptyList()
        val nullKeyCount = permissions.count { it.key == null }
        if (nullKeyCount > 0) {
            android.util.Log.w("KindeSDK", "Dropping $nullKeyCount permission(s) with null keys")
        }
        return permissions.mapNotNull { it.key }
    }
}

data class PermissionsData(
    @SerializedName("org_code")
    val orgCode: String? = null,

    @SerializedName("permissions")
    val permissions: List<PermissionItem> = emptyList()
)

data class PermissionItem(
    @SerializedName("id")
    val id: String? = null,
    
    @SerializedName("key")
    val key: String? = null,
    
    @SerializedName("name")
    val name: String? = null
)
