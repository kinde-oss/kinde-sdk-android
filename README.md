# Kinde Android SDK

## Overview
Integrate Kinde authentication with your Android app. Simply configure, register, log in, and log out, and the authentication state is securely stored across app restarts.

These instructions assume you already have a Kinde account. You can register for free [here](https://app.kinde.com/register) (no credit card required).

## Install
KindeSDK is available through [Maven](https://search.maven.org/). To install it, simply add the following line to your build.gradle:

    implementation "com.kinde:android-sdk:<sdk_version>"

You should also include Retrofit and the GSON converter as dependencies:

    implementation "com.squareup.retrofit2:retrofit:<retrofit_version>"
    implementation "com.squareup.retrofit2:converter-gson:<retrofit_version>"

## Configure Kinde
### Set callback URLs
In Kinde, go to <b>Settings</b> > <b>Applications</b>.

View the application details. This is where you get app keys and set the callback URLs.
Add your callback URLs in the relevant fields. For example:
- Allowed callback URLs: 
    kinde.sdk://<your_kinde_host>/kinde_callback - for example kinde.sdk://myhost.kinde.com//kinde_callback
- Allowed logout redirect URLs:
    kinde.sdk://<your_kinde_host>/kinde_logoutcallback - for example kinde.sdk://myhost.kinde.com/kinde_logoutcallback

Select `Save`.

### Add environments
If you would like to use our Environments feature as part of your development process. You will need to create them within your Kinde account. In this case you would use the Environment subdomain in the code block above.

## Configure your app
### Environment variables
The SDK reads configuration from `meta-data`, so you should add `meta-data` to `<application>` section of your `AndroidManifest.xml`.

You can find these variables on your Kinde Settings -> App keys page.

- `au.kinde.domain`: your Kinde domain
- `au.kinde.clientId`: you can find this on the App Keys page

````
  ...
  <application ...>
      ...
      <meta-data
        android:name="au.kinde.domain"
        android:value="your_kinde_url" />

      <meta-data
        android:name="au.kinde.clientId"
        android:value="your_kinde_client_id" />
      ...
  </application>
  ...
````

Configuration example:
````
  ...
  <application ...>
  ...
      <meta-data
        android:name="au.kinde.domain"
        android:value="app.kinde.com" />

      <meta-data
        android:name="au.kinde.clientId"
        android:value="test@live" />
        
  ...
  </application>
  ...
````

## Integrate with your app
Before `KindeSDK` can be used it should be initialized at `onCreate(savedInstance)` method of your `Activity`:
````
  ...
  import android.util.Log
  import androidx.appcompat.app.AppCompatActivity
  import au.kinde.sdk.KindeSDK
  import au.kinde.sdk.SDKListener
  ...
  class YourActivity : AppCompatActivity() {
    ...
    private lateinit var sdk: KindeSDK
    ...

    override fun onCreate(savedInstanceState: Bundle?) {
      ...
      sdk = KindeSDK(
        activity = this, 
        loginRedirect = "kinde.sdk://myhost.kinde.com/kinde_callback",
        logoutRedirect = "kinde.sdk://myhost.kinde.com/kinde_logoutcallback",
        sdkListener = object : KindeSDK.SDKListener {
    
            override fun onNewToken(token: String) {
                // Need to implement
            }
        
            override fun onLogout() {
                // Need to implement
            }
        
            override fun onException(exception: Exception) {
                Log.e("MyActivity", "Something wrong init KindeSDK: " + exception.message)
            }
        }
      )
    ...
    }
    ...
  }
````

## Login / Register
The Kinde client provides methods for a simple login / register flow. Add buttons to your `layout` xml file and handle clicks as follows:

````
    ...
    findViewById<View>(R.id.b_sign_in).setOnClickListener {
        sdk.login(GrantType.PKCE)
    }
    
    findViewById<View>(R.id.b_sign_up).setOnClickListener {
        sdk.register(GrantType.PKCE)
    }
    ...
````
### Handle redirect
Once your user is redirected back to your app from Kinde (it means you’ve logged in successfully), you need to implement the `onNewToken` function from the `SDKListener`.
````
    sdk = KindeSDK(
        activity = this, 
        sdkListener = object : KindeSDK.SDKListener {
            
            override fun onNewToken(token: String) {
                // Need to implement
            }
            ...
        }
    )

````

## Logout
This is implemented in much the same way as logging in or registering. The Kinde SDK client comes with a logout method.
````
    ....
    findViewById<View>(R.id.b_sign_out).setOnClickListener {
	    sdk.logout()
    }
    ....
````
### Handle redirect
Once your user is redirected back to your app from Kinde (it means you’ve logged out successfully), you need to implement the `onLogout` function from the `SDKListener`.
````
    sdk = KindeSDK(
        activity = this, 
        sdkListener = object : KindeSDK.SDKListener {
            
            override fun onLogout(token: String) {
                // Need to implement
            }
            ...
        }
    )

````
## Get user information
To access the user information, call one of the `getUser` or `getUserProfileV2` methods.
NOTE: these methods are synchronous and should be called outside of the `Main` (or `UI`) thread 

````
    ...
    sdk.getUser()?.let {
        Handler(Looper.getMainLooper()).post {
            Log.i("MyActivity", it.firstName + " " + it.lastName)
        }
    }
    ...
````

## View users in Kinde
Navigate to the <b>Users</b> page within Kinde to see your newly registered user.

## User Permissions
Once a user has been verified, your application will be returned the JWT token with an array of permissions for that user. You will need to configure your application to read permissions and unlock the respective functions.

[Set roles and permissions](https://kinde.com/docs/user-management/apply-roles-and-permissions-to-users/) at the Business level in Kinde. Here’s an example of permissions.
````
    val permissions = listOf(
        “create:todos”,
        “update:todos”,
        “read:todos”,
        “delete:todos”,
        “create:tasks”,
        “update:tasks”,
        “read:tasks”,
        “delete:tasks”,
    )
````

## Feature flags
When a user signs in the Access token your product/application receives contains a custom claim called `feature_flags` which is an object detailing the feature flags for that user.
You can set feature flags in your Kinde account. Here’s an example.
````
    feature_flags: {
        theme: {
              "t": "s",
              "v": "pink"
        },
        is_dark_mode: {
              "t": "b",
              "v": true
        },
        competitions_limit: {
              "t": "i",
              "v": 5
        }
    }
````
In order to minimize the payload in the token we have used single letter keys / values where possible. The single letters represent the following:
`t` = type,
`v` = value, 
`s` = String,
`b` = Boolean,
`i` = Integer,

````
/**
  * Get a flag from the feature_flags claim of the access_token.
  * @param {String} code - The name of the flag.
  * @param {Any?} [defaultValue] - A fallback value if the flag isn't found.
  * @param {[FlagType?](/sdk/src/main/kotlin/au/kinde/sdk/model/Flag.kt} [flagType] - The data type of the flag (integer / boolean / string).
  * @return {[Flag?](/sdk/src/main/kotlin/au/kinde/sdk/model/Flag.kt} Flag details.
*/
sdk.getFlag(code, defaultValue, flagType);

/* Example usage */

sdk.getFlag("theme");
/*{
//   "code": "theme",
//   "type": "String",
//   "value": "pink",
//   "isDefault": false // whether the fallback value had to be used
*/}

sdk.getFlag("create_competition", false);
/*{
     "code": "create_competition",
     "value": false,
     "isDefault": true // because fallback value had to be used
}*/
````
We also require wrapper functions by type which should leverage `getFlag` above.

We provide helper functions to more easily access feature flags:
- Booleans:
    ````
  /**
  * Get a boolean flag from the feature_flags claim of the access_token.
  * @param {String} code - The name of the flag.
  * @param {Boolean} [defaultValue] - A fallback value if the flag isn't found.
  * @return {Boolean}
    **/
    sdk.getBooleanFlag(code, defaultValue);

    /* Example usage */
    sdk.getBooleanFlag("is_dark_mode");
    // true

    sdk.getBooleanFlag("is_dark_mode", false);
    // true

    sdk.getBooleanFlag("new_feature", false);
    // false (flag does not exist so falls back to default)
  ````
- Strings and integers work in the same way as booleans above:
    ````
  /**
  * Get a string flag from the feature_flags claim of the access_token.
  * @param {String} code - The name of the flag.
  * @param {String} [defaultValue] - A fallback value if the flag isn't found.
  * @return {String}
  */
  getStringFlag(code, defaultValue);

  /**
  * Get an integer flag from the feature_flags claim of the access_token.
  * @param {String} code - The name of the flag.
  * @param {Int} [defaultValue] - A fallback value if the flag isn't found.
  * @return {Int}
  */
  getIntegerFlag(code, defaultValue);
  ````

## Audience
An `audience` is the intended recipient of an access token - for example the API for your application. 
The audience argument can be passed to the `meta-data` to `<application>` section of your `AndroidManifest.xml` to request an audience be added to the provided token.

The audience of a token is the intended recipient of the token.
````
  ...
  <application ...>
    ...
        
    <meta-data
        android:name="au.kinde.audience"
        android:value="your_kinde_audience" />
    ...
  </application>
  ...
````
Configuration example:
````
  ...
  <application ...>
    ...        
    <meta-data
        android:name="au.kinde.audience"
        android:value="https://app.kinde.com/api" />
    ...
  </application>
  ...
````
## Overriding scope
By default the KindeSDK requests the following scopes:

- profile
- email
- offline
- openid

You can override this by passing scope into the KindeSDK
````
    sdk = KindeSDK(
        ...
        scopes = listOf("openid", "offline", "email", "profile"), 
        ...
    )

````
## Getting claims
We have provided a helper to grab any claim from your id or access tokens. The helper defaults to access tokens:
````
    ...
    sdk.getClaim("aud")
    // {name: "aud", "value": ["api.yourapp.com"]}
    sdk.getClaim("given_name", TokenType.ID_TOKEN)
    // {name: "given_name", "value": "David"}
    ...
````

## Organizations Control
### Create an organization
To have a new organization created within your application, you will need to add button to your `layout` xml file and handle clicks as follows:

````
    ...
    findViewById<View>(R.id.create_org).setOnClickListener {
        sdk.createOrg(orgName = "Your Organization")
    }
    ...
````
### Sign up and sign in to organizations
Kinde has a unique code for every organization. You’ll have to pass this code through when you register a new user. Example function below:

````
    ...
   
    findViewById<View>(R.id.b_sign_up).setOnClickListener {
        sdk.register(GrantType.PKCE, org_code = "your_org_code")
    }
    ...
````
If you want a user to sign into a particular organization, pass this code along with the sign in method:

````
    ...
   
    findViewById<View>(R.id.b_sign_in).setOnClickListener {
        sdk.login(GrantType.PKCE, org_code = "your_org_code")
    }
    ...
````
Following authentication, Kinde provides a json web token (jwt) to your application. 
Along with the standard information we also include the `org_code` and the `permissions` for that organization (this is important as a user can belong to multiple organizations and have different permissions for each).

Example of a returned token:
````
    {
        "aud": [],
        "exp": 1658475930,
        "iat": 1658472329,
        "iss": "https://your_subdomain.kinde.com",
        "jti": "123457890",
        "org_code": "org_1234",
        "permissions": ["read:todos", "create:todos"],
        "scp": ["openid", "profile", "email", "offline"],
        "sub": "kp:123457890"
    }
````
The id_token will also contain an array of organizations that a user belongs to - this is useful if you wanted to build out an organization switcher for example.
````
    {
        ...
        "org_codes": ["org_1234", "org_4567"]
        ...
    }
````
There are two helper functions you can use to extract information:
````
    ...
    sdk.getOrganization()
    // {orgCode: "org_1234"}
    sdk.getUserOrganizations()
    // {orgCodes: ["org_1234", "org_abcd"]}
    ...
````
## Token Storage
Once the user has successfully authenticated, you’ll have a JWT and possibly a refresh token that should be stored securely. To achieve this Kinde SDK stores this data at the app's private folder.

## SDK API Reference
| Property       | Type                                                            | Is required | Default description                                            |
|----------------|-----------------------------------------------------------------|-------------|----------------------------------------------------------------|
| activity       | AppCompatActivity                                               | Yes         | Activity of the application                                    |
| loginRedirect  | String                                                          | Yes         | The url that the user will be returned to after authentication |
| logoutRedirect | String                                                          | Yes         | Where your user will be redirected upon logout                 |
| scopes         | List<Sting>                                                     | No          | List of scopes to override the default ones                    |     
| SDKListener    | [SDKListener](/sdk/src/main/kotlin/au/kinde/sdk/SDKListener.kt) | Yes         | The listener that receives callbacks from the SDK              |     

## KindeSDK methods
| Method                 | Description                                                                            | Arguments                                                                                       | Usage                                                                                                    | Sample output                                                                                                                  |
|------------------------|----------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| `login`                | Starts the authorization flow                                                          | `grantType`: [GrantType?](/sdk/src/main/kotlin/au/kinde/sdk/GrantType.kt), `orgCode`: String?   | `sdk.login(GrantType.PKCE, "your_org_cde")`                                                              |                                                                                                                                |
| `register`             | Starts the registration flow                                                           | `grantType`: [GrantType?](/sdk/src/main/kotlin/au/kinde/sdk/GrantType.kt), `orgCode`: String?   | `sdk.register(GrantType.PKCE, "your_org_code")`                                                          |                                                                                                                                |
| `createOrg`            | Starts the registration flow and creates a new organization for your business          | `grantType`: [GrantType?](/sdk/src/main/kotlin/au/kinde/sdk/GrantType.kt), `orgName`: String?   | `sdk.createOrg("your_organization_name")`; or  `sdk.createOrg(GrantType.PKCE, "your_organization_name")` |                                                                                                                                |
| `logout`               | Logs the user out of Kinde                                                             |                                                                                                 | `sdk.logout()`                                                                                           |                                                                                                                                |
| `isAuthenticated`      | Checks that access token is present                                                    |                                                                                                 | `sdk.isAuthenticated()`                                                                                  | `true`                                                                                                                         |
| `getUserDetails`       | Returns the profile for the current user                                               |                                                                                                 | `sdk.getUserDetails()`                                                                                   | `{givenName: "Dave", id: "abcdef", familyName: "Smith", email: "dave@smith.com", picture: "coolavatar"}`                       |
| `getClaim`             | Gets a claim from an access or id token                                                | `claim`: String, `tokenType`: [TokenType](/sdk/src/main/kotlin/au/kinde/sdk/model/TokenType.kt) | `sdk.getClaim('given_name', TokenType.ID_TOKEN);`                                                        | `{name: "given_name", "value": "David"}`                                                                                       |
| `getPermissions`       | Returns all permissions for the current user for the organization they are logged into |                                                                                                 | `sdk.getPermissions()`                                                                                   | `{orgCode: "org_1234", permissions: ["create:todos", "update:todos", "read:todos""create:todos","update:todos","read:todos"]}` |
| `getPermission`        | Returns the state of a given permission                                                | `permission`: String                                                                            | `sdk.getPermission("read:todos")`                                                                        | `{orgCode: "org_1234", isGranted: true}`                                                                                       |
| `getUserOrganizations` | Gets an array of all organizations the user has access to                              |                                                                                                 | `sdk.getUserOrganizations()`                                                                             | `{orgCodes: ["org_1234", "org_5678""org1_234","org_5678"]}`                                                                    |
| `getOrganization`      | Get details for the organization your user is logged into                              |                                                                                                 | `sdk.getOrganization()`                                                                                  | `{orgCode: "org_1234"}`                                                                                                        |