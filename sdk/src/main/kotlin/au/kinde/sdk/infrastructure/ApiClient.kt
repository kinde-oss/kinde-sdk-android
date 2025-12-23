package au.kinde.sdk.infrastructure

import au.kinde.sdk.auth.HttpBearerAuth
import com.google.gson.GsonBuilder
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory


class ApiClient(
    private var baseUrl: String = defaultBasePath,
    private val okHttpClientBuilder: OkHttpClient.Builder? = null,
    private val serializerBuilder: GsonBuilder = Serializer.gsonBuilder,
    private val callFactory: Call.Factory? = null,
    private val converterFactory: Converter.Factory? = null,
) {
    private val apiAuthorizations = mutableMapOf<String, Interceptor>()
    var logger: ((String) -> Unit)? = null

    private var retrofitBuilder: Retrofit.Builder = createRetrofitBuilder()
    
    private fun createRetrofitBuilder(): Retrofit.Builder {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(serializerBuilder.create()))
            .apply {
                if (converterFactory != null) {
                    addConverterFactory(converterFactory)
                }
            }
    }

    private val clientBuilder: OkHttpClient.Builder by lazy {
        okHttpClientBuilder ?: defaultClientBuilder
    }

    private val defaultClientBuilder: OkHttpClient.Builder by lazy {
        OkHttpClient().newBuilder()
            .apply {
                if (au.kinde.sdk.BuildConfig.DEBUG) {
                    addInterceptor { chain ->
                        val request = chain.request()
                        val response = chain.proceed(request)
                        val responseBody = response.body()
                        val contentType = responseBody?.contentType()
                        val bodyString = responseBody?.string()
                        android.util.Log.d("ApiClient", "URL: ${request.url()}")
                        android.util.Log.d("ApiClient", "Response code: ${response.code()}")
                        // Only log body length, not content, to avoid exposing sensitive data
                        android.util.Log.d("ApiClient", "Response body length: ${bodyString?.length ?: 0}")
                        response.newBuilder()
                            .body(okhttp3.ResponseBody.create(contentType, bodyString ?: ""))
                            .build()
                    }
                }
            }
    }

    init {
        normalizeBaseUrl()
    }

    constructor(
        baseUrl: String = defaultBasePath,
        okHttpClientBuilder: OkHttpClient.Builder? = null,
        serializerBuilder: GsonBuilder = Serializer.gsonBuilder,
        authNames: Array<String>
    ) : this(baseUrl, okHttpClientBuilder, serializerBuilder) {
        authNames.forEach { authName ->
            val auth = when (authName) {
                "kindeBearerAuth" -> HttpBearerAuth("bearer")
                else -> throw RuntimeException("auth name $authName not found in available auth names")
            }
            addAuthorization(authName, auth)
        }
    }

    constructor(
        baseUrl: String = defaultBasePath,
        okHttpClientBuilder: OkHttpClient.Builder? = null,
        serializerBuilder: GsonBuilder = Serializer.gsonBuilder,
        authName: String,
        bearerToken: String
    ) : this(baseUrl, okHttpClientBuilder, serializerBuilder, arrayOf(authName)) {
        setBearerToken(bearerToken)
    }

    fun setBearerToken(bearerToken: String): ApiClient {
        apiAuthorizations.values.runOnFirst<Interceptor, HttpBearerAuth> {
            this.bearerToken = bearerToken
        }
        return this
    }

    /**
     * Adds an authorization to be used by the client
     * @param authName Authentication name
     * @param authorization Authorization interceptor
     * @return ApiClient
     */
    fun addAuthorization(authName: String, authorization: Interceptor): ApiClient {
        if (apiAuthorizations.containsKey(authName)) {
            throw RuntimeException("auth name $authName already in api authorizations")
        }
        apiAuthorizations[authName] = authorization
        clientBuilder.addInterceptor(authorization)
        return this
    }

    fun setLogger(logger: (String) -> Unit): ApiClient {
        this.logger = logger
        return this
    }
    
    /**
     * Updates the base URL for API calls
     * @param newBaseUrl The new base URL
     * @return ApiClient
     */
    @Synchronized
    fun setBaseUrl(newBaseUrl: String): ApiClient {
        baseUrl = newBaseUrl
        normalizeBaseUrl()
        retrofitBuilder = createRetrofitBuilder()
        return this
    }
    
    /**
     * Gets the current base URL
     * @return The current base URL
     */
    @Synchronized
    fun getBaseUrl(): String = baseUrl

    @Synchronized
    fun <S> createService(serviceClass: Class<S>): S {
        val usedCallFactory = this.callFactory ?: clientBuilder.build()
        return retrofitBuilder.callFactory(usedCallFactory).build().create(serviceClass)
    }

    private fun normalizeBaseUrl() {
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/"
        }
    }

    private inline fun <T, reified U> Iterable<T>.runOnFirst(callback: U.() -> Unit) {
        for (element in this) {
            if (element is U) {
                callback.invoke(element)
                break
            }
        }
    }

    companion object {
        @JvmStatic
        protected val baseUrlKey = "au.kinde.sdk.baseUrl"

        @JvmStatic
        val defaultBasePath: String by lazy {
            System.getProperties().getProperty(baseUrlKey, "https://app.kinde.com/api/v1")
        }
    }
}
