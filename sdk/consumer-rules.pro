# Applied to apps that use the SDK and shrink their code with R8.

# Models Gson reads and writes by reflection. Their fields must keep their names
# and survive shrinking even where nothing in the app reads them directly: without
# this R8 removes au.kinde.sdk.keys.Key and the field that holds the signing keys,
# so every token fails signature verification and isAuthenticated() is always false.
-keep class au.kinde.sdk.keys.Keys { *; }
-keep class au.kinde.sdk.keys.Key { *; }
-keep class au.kinde.sdk.api.model.** { *; }
-keep class au.kinde.sdk.model.** { *; }

# Retrofit service interfaces: Retrofit reads their annotations and generic return
# types (Call<Keys>, Call<UserProfileV2>, …) at runtime.
-keep,allowobfuscation interface au.kinde.sdk.api.* { *; }
-keep,allowobfuscation interface au.kinde.sdk.keys.KeysApi { *; }
-keep,allowobfuscation interface au.kinde.sdk.token.TokenApi { *; }

-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
