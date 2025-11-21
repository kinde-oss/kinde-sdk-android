package au.kinde.sdk.api.model.entitlements

import com.google.gson.annotations.SerializedName

class Entitlements(
    @SerializedName("org_code")
    val orgCode: String,
    @SerializedName("plans")
    val plans: List<EntitlementPlan>,
    @SerializedName("entitlements")
    val entitlements: List<Entitlement>
)