package au.kinde.sdk.api

import au.kinde.sdk.api.model.UserProfile
import au.kinde.sdk.api.model.UserProfileV2
import au.kinde.sdk.api.model.entitlements.EntitlementResponse
import au.kinde.sdk.api.model.entitlements.EntitlementsResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface OAuthApi {

    @GET("oauth2/user_profile")
    fun getUser(): Call<UserProfile>

    @GET("oauth2/v2/user_profile")
    fun getUserProfileV2(): Call<UserProfileV2>

    @GET("account_api/v1/entitlement")
    fun getEntitlement(@Query("key") key: String): Call<EntitlementResponse>

    @GET("account_api/v1/entitlements")
    fun getEntitlements(): Call<EntitlementsResponse>
}
