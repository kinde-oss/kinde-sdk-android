# Keep entitlements model classes for Gson serialization/deserialization
-keep class au.kinde.sdk.api.model.entitlements.** { *; }
-keepclassmembers class au.kinde.sdk.api.model.entitlements.** {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault