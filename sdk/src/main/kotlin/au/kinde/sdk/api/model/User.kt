/**
 * Kinde Management API
 *
 * Provides endpoints to manage your Kinde Businesses
 *
 * The version of the OpenAPI document: 0.0.1
 * Contact: support@kinde.com
 *
 * Please note:
 * This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * Do not edit this file manually.
 */

@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package au.kinde.sdk.api.model


import com.google.gson.annotations.SerializedName

/**
 * 
 *
 * @param id 
 * @param email 
 * @param fullName 
 * @param lastName 
 * @param firstName 
 * @param isSuspended 
 */

data class User (

    @SerializedName("id")
    val id: kotlin.Int? = null,

    @SerializedName("email")
    val email: kotlin.String? = null,

    @SerializedName("full_name")
    val fullName: kotlin.String? = null,

    @SerializedName("last_name")
    val lastName: kotlin.String? = null,

    @SerializedName("first_name")
    val firstName: kotlin.String? = null,

    @SerializedName("is_suspended")
    val isSuspended: kotlin.Boolean? = null

)

