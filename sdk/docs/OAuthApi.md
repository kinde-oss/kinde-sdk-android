# OAuthApi

All URIs are relative to *https://app.kinde.com*

Method | HTTP request | Description
------------- | ------------- | -------------
[**getUser**](OAuthApi.md#getUser) | **GET** oauth2/user_profile | Returns the details of the currently logged in user
[**getUserProfileV2**](OAuthApi.md#getUserProfileV2) | **GET** oauth2/v2/user_profile | Returns the details of the currently logged in user



Returns the details of the currently logged in user

Contains the id, names and email of the currently logged in user 

### Example
```kotlin
// Import classes:
//import au.kinde.sdk.*
//import au.kinde.sdk.infrastructure.*
//import au.kinde.sdk.api.model.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(OAuthApi::class.java)

val result : UserProfile = webService.getUser()
```

### Parameters
This endpoint does not need any parameter.

### Return type

[**UserProfile**](UserProfile.md)

### Authorization


Configure kindeBearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


Returns the details of the currently logged in user

Contains the id, names and email of the currently logged in user 

### Example
```kotlin
// Import classes:
//import au.kinde.sdk.*
//import au.kinde.sdk.infrastructure.*
//import au.kinde.sdk.api.model.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(OAuthApi::class.java)

val result : UserProfileV2 = webService.getUserProfileV2()
```

### Parameters
This endpoint does not need any parameter.

### Return type

[**UserProfileV2**](UserProfileV2.md)

### Authorization


Configure kindeBearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

