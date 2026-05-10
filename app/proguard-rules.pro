# Kotlin
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# Compose
-keep class androidx.compose.** { *; }

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keep class com.aviator.predictor.data.models.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Google Identity
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.credentials.** { *; }

# Keep model classes for Drive JSON serialisation
-keep class com.aviator.predictor.data.models.Signal { *; }
-keep class com.aviator.predictor.data.models.TimeObj { *; }
-keep class com.aviator.predictor.data.models.WindowTimes { *; }
-keep class com.aviator.predictor.data.models.UserProfile { *; }
-keep class com.aviator.predictor.data.models.AppSettings { *; }
-keep class com.aviator.predictor.data.models.DriveStorage { *; }
