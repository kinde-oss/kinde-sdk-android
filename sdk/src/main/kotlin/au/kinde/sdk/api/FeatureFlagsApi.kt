package au.kinde.sdk.api

import au.kinde.sdk.api.model.FeatureFlagsResponse
import retrofit2.Call
import retrofit2.http.GET

/**
 * API interface for fetching feature flags
 */
interface FeatureFlagsApi {

    @GET("account_api/v1/feature_flags")
    fun getFeatureFlags(): Call<FeatureFlagsResponse>
}
