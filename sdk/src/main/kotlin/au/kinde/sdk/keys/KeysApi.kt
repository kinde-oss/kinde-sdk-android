package au.kinde.sdk.keys

import au.kinde.sdk.infrastructure.CollectionFormats.*
import retrofit2.Call
import retrofit2.http.*

interface KeysApi {
    @GET(".well-known/jwks.json")
    fun getKeys(): Call<Keys>
}
