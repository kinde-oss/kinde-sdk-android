# UsersApi

All URIs are relative to *https://app.kinde.com*

Method | HTTP request | Description
------------- | ------------- | -------------
[**createUser**](UsersApi.md#createUser) | **POST** api/v1/user | Creates a user record
[**getUsers**](UsersApi.md#getUsers) | **GET** api/v1/users | Returns a paginated list of end-user records for a business



Creates a user record

Creates a user record and optionally zero or more identities for the user. An example identity could be the email address of the user 

### Example
```kotlin
// Import classes:
//import au.kinde.sdk.*
//import au.kinde.sdk.infrastructure.*
//import au.kinde.sdk.api.model.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(UsersApi::class.java)
val createUserRequest : CreateUserRequest =  // CreateUserRequest | The details of the user to create

val result : CreateUser200Response = webService.createUser(createUserRequest)
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **createUserRequest** | [**CreateUserRequest**](CreateUserRequest.md)| The details of the user to create | [optional]

### Return type

[**CreateUser200Response**](CreateUser200Response.md)

### Authorization


Configure kindeBearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


Returns a paginated list of end-user records for a business

The returned list can be sorted by full name or email address in ascending or descending order. The number of records to return at a time can also be controlled using the page_size query string parameter. 

### Example
```kotlin
// Import classes:
//import au.kinde.sdk.*
//import au.kinde.sdk.infrastructure.*
//import au.kinde.sdk.api.model.*

val apiClient = ApiClient()
apiClient.setBearerToken("TOKEN")
val webService = apiClient.createWebservice(UsersApi::class.java)
val sort : kotlin.String = sort_example // kotlin.String | Describes the field and order to sort the result by
val pageSize : kotlin.Int = 56 // kotlin.Int | The number of items to return
val userId : kotlin.Int = 56 // kotlin.Int | The id of the user to filter by
val nextToken : kotlin.String = nextToken_example // kotlin.String | A string to get the next page of results if there are more results

val result : kotlin.collections.List<User> = webService.getUsers(sort, pageSize, userId, nextToken)
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **sort** | **kotlin.String**| Describes the field and order to sort the result by | [optional] [enum: name_asc, name_desc, email_asc, email_desc]
 **pageSize** | **kotlin.Int**| The number of items to return | [optional]
 **userId** | **kotlin.Int**| The id of the user to filter by | [optional]
 **nextToken** | **kotlin.String**| A string to get the next page of results if there are more results | [optional]

### Return type

[**kotlin.collections.List&lt;User&gt;**](User.md)

### Authorization


Configure kindeBearerAuth:
    ApiClient().setBearerToken("TOKEN")

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

