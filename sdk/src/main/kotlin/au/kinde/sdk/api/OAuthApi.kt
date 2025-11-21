package au.kinde.sdk.api

import au.kinde.sdk.api.model.UserProfile
import au.kinde.sdk.api.model.UserProfileV2
import retrofit2.Call
import retrofit2.http.GET

interface OAuthApi {

    @GET("oauth2/user_profile")
    fun getUser(): Call<UserProfile>

    @GET("oauth2/v2/user_profile")
    fun getUserProfileV2(): Call<UserProfileV2>
}
