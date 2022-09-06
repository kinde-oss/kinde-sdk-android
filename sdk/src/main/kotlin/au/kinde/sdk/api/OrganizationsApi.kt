package au.kinde.sdk.api

import au.kinde.sdk.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Call
import okhttp3.RequestBody

import au.kinde.sdk.api.model.OrganizationCode

interface OrganizationsApi {
    /**
     * Returns the created organization&#39;s code.
     * Create an organization given the organization name. 
     * Responses:
     *  - 200: A successfull response with the created organization's code
     *  - 500: Could not create organization
     *
     * @param name The name of the organization to be created. (optional)
     * @return [Call]<[OrganizationCode]>
     */
    @GET("create-organization")
    fun createOrganization(@Query("name") name: kotlin.String? = null): Call<OrganizationCode>

}
