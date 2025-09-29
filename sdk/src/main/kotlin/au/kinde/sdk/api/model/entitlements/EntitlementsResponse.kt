package au.kinde.sdk.api.model.entitlements

import com.google.gson.annotations.SerializedName

class EntitlementsResponse(
    @SerializedName("data")
    val data: Entitlements,
    @SerializedName("metadata")
    val metadata: EntitlementsMetadata
)