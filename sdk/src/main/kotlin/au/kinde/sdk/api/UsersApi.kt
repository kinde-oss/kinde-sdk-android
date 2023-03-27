package au.kinde.sdk.api

import au.kinde.sdk.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Call
import okhttp3.RequestBody

import au.kinde.sdk.api.model.CreateUser200Response
import au.kinde.sdk.api.model.CreateUserRequest
import au.kinde.sdk.api.model.User

interface UsersApi {
    /**
     * Creates a user record
     * Creates a user record and optionally zero or more identities for the user. An example identity could be the email address of the user 
     * Responses:
     *  - 200: Successfully created a new user
     *
     * @param createUserRequest The details of the user to create (optional)
     * @return [Call]<[CreateUser200Response]>
     */
    @POST("api/v1/user")
    fun createUser(@Body createUserRequest: CreateUserRequest? = null): Call<CreateUser200Response>

    /**
     * Returns a paginated list of end-user records for a business
     * The returned list can be sorted by full name or email address in ascending or descending order. The number of records to return at a time can also be controlled using the page_size query string parameter. 
     * Responses:
     *  - 200: A succesful response with a list of users or an empty list
     *  - 403: invalid_credentials
     *
     * @param sort Describes the field and order to sort the result by (optional)
     * @param pageSize The number of items to return (optional)
     * @param userId The id of the user to filter by (optional)
     * @param nextToken A string to get the next page of results if there are more results (optional)
     * @return [Call]<[kotlin.collections.List<User>]>
     */
    @GET("api/v1/users")
    fun getUsers(@Query("sort") sort: kotlin.String? = null, @Query("page_size") pageSize: kotlin.Int? = null, @Query("user_id") userId: kotlin.Int? = null, @Query("next_token") nextToken: kotlin.String? = null): Call<kotlin.collections.List<User>>

}
