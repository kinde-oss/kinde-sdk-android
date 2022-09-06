package au.kinde.sdk.api

import au.kinde.sdk.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Call
import okhttp3.RequestBody

import au.kinde.sdk.api.model.UserProfile

interface OAuthApi {
    /**
     * Returns the details of the currently logged in user
     * Contains the id, names and email of the currently logged in user 
     * Responses:
     *  - 200: A succesful response with the user details
     *  - 403: Invalid credentials
     *
     * @return [Call]<[UserProfile]>
     */
    @GET("oauth2/user_profile")
    fun getUser(): Call<UserProfile>

}
