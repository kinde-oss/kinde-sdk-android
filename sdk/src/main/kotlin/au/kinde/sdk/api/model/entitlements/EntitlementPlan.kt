package au.kinde.sdk.api.model.entitlements

import com.google.gson.annotations.SerializedName

data class EntitlementPlan(
    @SerializedName("key")
    val key: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("subscribed_on")
    val subscribedOn: String
)