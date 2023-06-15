package au.kinde.sdk.token

import retrofit2.Call
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.QueryMap

/**
 * @author roman
 * @since 1.0
 */
interface TokenApi {

    @POST("oauth2/token")
    @FormUrlEncoded
    fun retrieveToken(
        @Header("Kinde-SDK") version: String,
        @FieldMap params: Map<String, String>
    ): Call<String>
}