package au.kinde.sdk.api.model.entitlements

import com.google.gson.annotations.SerializedName

data class EntitlementMetadata(
    @SerializedName("org_code")
    val orgCode: String,
    @SerializedName("entitlement")
    val entitlement: Entitlement
)