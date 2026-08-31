# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.examples.android.model.** { <fields>; }
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements com.google.gson.TypeAdapterFactory
-keep public class * implements com.google.gson.JsonSerializer
-keep public class * implements com.google.gson.JsonDeserializer

# Hilt / Dagger
-keep class dagger.hilt.android.internal.managers.** { *; }
-keep class * extends hilt_aggregated_deps.** { *; }
-keep class * implements hilt_aggregated_deps.** { *; }
-dontwarn dagger.hilt.android.internal.managers.**

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Media3
-dontwarn androidx.media3.**

# App Specific Data Models
-keep class com.doohtracker.box.data.remote.** { *; }
-keep class com.doohtracker.box.data.manager.Identity { *; }
-keep class com.doohtracker.box.data.hardware.SourceMetadata { *; }
