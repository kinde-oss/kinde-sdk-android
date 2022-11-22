# Kinde Android SDK
## Requirements
Minimum supported Android SDK is Android SDK 21 (Android 5.0 Lollipop)

## How to build
```
./gradlew build
```


## How to integrate
1. Set up Kinde environment
   - Open Kinde settings in the webapp.
1.1 Create a new app
   - Navigate to `Applications` section
   - Click to `Add application` button
   - Fill a name of this application:
     For example:
        Collapp.Android  
   - Select `Regular Web Application` type
   - Click to `Save` button
1.2 Set up the application
   - Open `Details` section of the your newly created app
   - Navigate to `Callback URLs` section
   - Add the following url to the `Allowed callback URLs` section:
         au.kinde://<your kinde url>//kinde_callback
   
      For example:
         au.kinde://coolapp.kinde.com/kinde_callback
   - Add the following url to the `Allowed logout redirect URLs` section :
         au.kinde://<your kinde url>//kinde_callback
    
     For example:
         au.kinde://coolapp.kinde.com/kinde_callback
   - Click `Save` button
   - Navigate to the `Authentication` section
   - Switch on `Email authentication`
   - Select `Passwordless` type
   - Click `Save` button

2. Setup Android project
   - Add `kinde-sdk.arr` to your Android project:
      * Put it in `libs` folder. Make sure to include libs folder in your gradle file.   
         To do so, add the following line to the `dependencies` section
               
            implementation fileTree(include: ['*.aar'],dir: 'libs')
      * Alternatively you can add `kinde-sdk.aar` as separate module. Don't forget to add dependecies for this module to build.gradle

   - Add dependencies to Retrofit and Gson converter:
   
         implementation "com.squareup.retrofit2:retrofit:$retrofit_version"
         implementation "com.squareup.retrofit2:converter-gson:$retrofit_version"
   - Add meta-data to `<application>` section of your `AndroidManifest.xml`:
      * Kinde domain (Token host):

            <meta-data
            android:name="au.kinde.domain"
            android:value="coolapp.kinde.com" />
      * Client ID:

            <meta-data
            android:name="au.kinde.clientId"
            android:value="example@example" />

## How to initialize
To get instance of SDK just instantiate `KindeSDK` ([See KindeSDK](sdk/src/main/java/au/kinde/sdk/KindeSDK.kt)) class with `activity` ([See AppCompatActivity](androidx.appcompat.app.AppCompatActivity.java)) and `sdkListener` ([See SDKListener](sdk/src/main/java/au/kinde/sdk/KindeSDK.SDKListener.kt))  

## How to authorize
1. With PKCE
   - call `login` function of SDK with `grantType` parameter set to `GrantType.PKCE` ([See GrantType class for available options](sdk/src/main/java/au/kinde/sdk/GrantType.kt))

2. Without PKCE
    - call `login` function of SDK without parameters or with `grantType` parameter set to `GrantType.NONE` ([See GrantType class for available options](sdk/src/main/java/au/kinde/sdk/GrantType.kt))

## How to use API
SDK provides all available API methods as functions: just call one of functions to get data or pass information to server.

In case request is failed, `null` will be returned and additional information would be passed as exception to `onException` method of SDKListener [See How to initialize section](#how-to-initialize).

Supported exceptions:
1. `WrongThreadException`. Thrown if API method is called from main (UI) thread
2. `NotAuthorizedException`. Thrown if API method is called before `login`, so there is no token yet
3. `Exception`. Thrown if API request is failed. Http response code and message will be passed
