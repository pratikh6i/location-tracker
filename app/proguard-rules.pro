# Add project specific ProGuard rules here.

# Keep Google API Client classes
-keep class com.google.api.** { *; }
-keep class com.google.auth.** { *; }
-keep class com.google.http.** { *; }

# Keep Room entities
-keep class com.antigravity.locationtracker.data.db.** { *; }

# Keep Kotlin metadata
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Suppress warnings for Google API dependencies
-dontwarn org.apache.http.**
-dontwarn com.google.api.client.http.apache.**
-dontwarn com.google.common.**
-dontwarn javax.annotation.**

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
