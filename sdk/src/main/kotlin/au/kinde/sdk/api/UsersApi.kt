package au.kinde.sdk.api

import au.kinde.sdk.api.model.CreateUser200Response
import au.kinde.sdk.api.model.CreateUserRequest
import au.kinde.sdk.api.model.User
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** The two Kinde Management API endpoints the SDK exposed. See [au.kinde.sdk.KindeClient.getUsers]. */
@Deprecated(
    "Calls the Kinde Management API, which only accepts machine-to-machine tokens. Their client " +
    "secret must never ship in an app, so call the Management API from your backend instead. " +
    "Will be removed in the next major version."
)
interface UsersApi {

    @POST("api/v1/user")
    fun createUser(@Body createUserRequest: CreateUserRequest? = null): Call<CreateUser200Response>

    @GET("api/v1/users")
    fun getUsers(
        @Query("sort") sort: String? = null,
        @Query("page_size") pageSize: Int? = null,
        @Query("user_id") userId: Int? = null,
        @Query("next_token") nextToken: String? = null
    ): Call<List<User>>
}
