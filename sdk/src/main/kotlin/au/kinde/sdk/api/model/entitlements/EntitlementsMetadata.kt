package au.kinde.sdk.api.model.entitlements

import com.google.gson.annotations.SerializedName

class EntitlementsMetadata(
    @SerializedName("has_more")
    val hasMore: Boolean,
    /**
     * Pass to getEntitlements(startingAfter = …) for the next page. May be set even on
     * the last page: check [hasMore], not this, to know whether another page exists.
     */
    @SerializedName("next_page_starting_after")
    val nextPageStartingAfter: String?
)