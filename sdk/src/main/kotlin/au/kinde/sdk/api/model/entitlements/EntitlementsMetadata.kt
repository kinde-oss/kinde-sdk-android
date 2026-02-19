package au.kinde.sdk.api.model.entitlements

import com.google.gson.annotations.SerializedName

class EntitlementsMetadata(
    @SerializedName("has_more")
    val hasMore: Boolean,
    @SerializedName("next_page_starting_after")
    val nextPageStartingAfter: String
)