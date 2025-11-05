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
    val success: Boolean? = null
)

data class PermissionsData(
    @SerializedName("org_code")
    val orgCode: String? = null,

    @SerializedName("permissions")
    val permissions: List<PermissionItem>? = null
)

data class PermissionItem(
    @SerializedName("id")
    val id: String? = null,
    
    @SerializedName("key")
    val key: String? = null,
    
    @SerializedName("name")
    val name: String? = null
)
