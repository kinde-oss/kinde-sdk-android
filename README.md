# Kinde Android SDK

The Kinde SDK for Android.

You can also use the Android starter kit [here](https://github.com/kinde-starter-kits/android-starter-kit).

[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat-square)](https://makeapullrequest.com) [![Kinde Docs](https://img.shields.io/badge/Kinde-Docs-eee?style=flat-square)](https://kinde.com/docs/developer-tools) [![Kinde Community](https://img.shields.io/badge/Kinde-Community-eee?style=flat-square)](https://thekindecommunity.slack.com)

## Documentation

For details on integrating this SDK into your project, head over to the [Kinde docs](https://kinde.com/docs/) and see the [Android SDK](https://kinde.com/docs/developer-tools/android-sdk/) doc 👍🏼.

## Configuring the redirect scheme

The SDK uses [AppAuth](https://github.com/openid/AppAuth-Android) to receive the
authentication redirect. You choose the redirect scheme for your app by defining
the `appAuthRedirectScheme` manifest placeholder. This must match the scheme of
the redirect URLs you register in Kinde and pass to `KindeSDK`.

> **Important:** This placeholder must be set in **your own app module's**
> `build.gradle` (e.g. `app/build.gradle`).

```groovy
// app/build.gradle
android {
    defaultConfig {
        manifestPlaceholders = [appAuthRedirectScheme: 'com.example.myapp']
    }
}
```

With the placeholder above, your redirect URLs would look like:

```kotlin
val kinde = KindeSDK(
    activity = this,
    loginRedirect = "com.example.myapp://callback",
    logoutRedirect = "com.example.myapp://logout",
    sdkListener = listener
)
```

> **Note:** From **v2.0.0**, defining `appAuthRedirectScheme` in your own app
> module's `build.gradle` is required. If the placeholder is missing, the
> manifest merge will fail at build time.
>
> **Upgrading from v1.6.0 or earlier:** those versions hardcoded the `kinde.sdk`
> scheme. To keep that exact behavior (keeping your redirect URLs or
> Kinde dashboard configuration as `kinde.sdk`), set `appAuthRedirectScheme: 'kinde.sdk'` in
> your app module's `build.gradle` e.g.

```groovy
// app/build.gradle  (your application module, NOT the SDK)
android {
    defaultConfig {
        manifestPlaceholders = [appAuthRedirectScheme: 'kinde.sdk']
    }
}
```

## Publishing

The core team handles publishing.

## Contributing

Please refer to Kinde’s [contributing guidelines](https://github.com/kinde-oss/.github/blob/489e2ca9c3307c2b2e098a885e22f2239116394a/CONTRIBUTING.md).

## License

By contributing to Kinde, you agree that your contributions will be licensed under its MIT License.
