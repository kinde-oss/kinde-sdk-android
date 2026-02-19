package au.kinde.sdk.api.model.entitlements

import com.google.gson.annotations.SerializedName

class EntitlementResponse(
    @SerializedName("data")
    val data: EntitlementMetadata,
    @SerializedName("metadata")
    val metadata: Map<String, Any>
)