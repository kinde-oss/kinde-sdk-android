package au.kinde.sdk.api

import au.kinde.sdk.api.model.RolesResponse
import retrofit2.Call
import retrofit2.http.GET

/**
 * API interface for fetching user roles
 */
interface RolesApi {

    @GET("account_api/v1/roles")
    fun getRoles(): Call<RolesResponse>
}
