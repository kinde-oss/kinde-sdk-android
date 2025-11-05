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
    val success: Boolean? = null
)

data class RolesData(
    @SerializedName("org_code")
    val orgCode: String? = null,

    @SerializedName("roles")
    val roles: List<RoleData>? = null
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
