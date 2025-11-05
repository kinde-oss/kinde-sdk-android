package au.kinde.sdk.api

import au.kinde.sdk.api.model.CreateUser200Response
import au.kinde.sdk.api.model.CreateUserRequest
import au.kinde.sdk.api.model.User
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

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
