package au.kinde.sdk.api

import au.kinde.sdk.api.model.PermissionsResponse
import retrofit2.Call
import retrofit2.http.GET

/**
 * API interface for fetching user permissions
 */
interface PermissionsApi {

    @GET("account_api/v1/permissions")
    fun getPermissions(): Call<PermissionsResponse>
}
