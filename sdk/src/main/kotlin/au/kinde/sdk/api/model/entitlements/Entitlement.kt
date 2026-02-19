package au.kinde.sdk.api.model.entitlements

import com.google.gson.annotations.SerializedName

data class Entitlement(
    @SerializedName("id")
    val id: String,
    @SerializedName("fixed_charge")
    val fixedCharge: Double?,
    @SerializedName("price_name")
    val priceName: String,
    @SerializedName("unit_amount")
    val unitAmount: Double?,
    @SerializedName("feature_key")
    val featureKey: String,
    @SerializedName("feature_name")
    val featureName: String,
    @SerializedName("entitlement_limit_max")
    val entitlementLimitMax: Int,
    @SerializedName("entitlement_limit_min")
    val entitlementLimitMin: Int?
)