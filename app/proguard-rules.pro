# MapLibre
-keep class org.maplibre.** { *; }
-keep interface org.maplibre.** { *; }
-dontwarn org.maplibre.**

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn javax.annotation.**
-dontwarn retrofit2.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Gson (used by Retrofit converter)
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# App DTOs — keep all fields for Gson deserialization
-keep class org.github.nynosy.adiresy_mobile.data.api.dto.** { *; }

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Room entities and DAOs — keep all fields and method signatures
-keep class org.github.nynosy.adiresy_mobile.data.cache.** { *; }

# ViewModel keep
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Enum stability
-keepclassmembers enum org.github.nynosy.adiresy_mobile.** { *; }
