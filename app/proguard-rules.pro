# ReplyForge ProGuard Rules

# ═══════════════════════════════════════════
# Room Database
# ═══════════════════════════════════════════
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class *
-keep class * extends androidx.room.Entity
-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}

# Keep all Room entities (data models)
-keep class com.wahyuzero.replyforge.data.model.** { *; }

# Keep Room DAOs
-keep interface com.wahyuzero.replyforge.data.db.*Dao { *; }

# Keep RateLimitEntry (in data.db package, not data.model)
-keep class com.wahyuzero.replyforge.data.db.RateLimitEntry { *; }

# Keep Converters
-keep class com.wahyuzero.replyforge.data.db.Converters { *; }

# ═══════════════════════════════════════════
# Enums (used in TypeConverters — must survive obfuscation)
# ═══════════════════════════════════════════
-keepclassmembers,allowshrinking,allowobfuscation enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Explicitly keep all app enums
-keep enum com.wahyuzero.replyforge.data.model.** { *; }
-keep enum com.wahyuzero.replyforge.ui.rule.MatchType { *; }

# ═══════════════════════════════════════════
# Retrofit + OkHttp + Gson
# ═══════════════════════════════════════════
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Retrofit does not support reflection on the platform types
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Gson model classes (prevent field name obfuscation for JSON serialization)
-keep class com.wahyuzero.replyforge.network.** { *; }
-keep class com.wahyuzero.replyforge.network.ChatCompletionRequest { *; }
-keep class com.wahyuzero.replyforge.network.ChatCompletionResponse { *; }
-keep class com.wahyuzero.replyforge.network.ChatMessage { *; }
-keep class com.wahyuzero.replyforge.network.ChatChoice { *; }
-keep class com.wahyuzero.replyforge.network.ChatUsage { *; }

# Gson @SerializedName fields
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Gson generic type resolution
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# ═══════════════════════════════════════════
# Kotlin Coroutines
# ═══════════════════════════════════════════
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ═══════════════════════════════════════════
# DataStore
# ═══════════════════════════════════════════
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# ═══════════════════════════════════════════
# Android Components
# ═══════════════════════════════════════════
# Keep Activities and Fragments
-keep class * extends android.app.Activity
-keep class * extends androidx.fragment.app.Fragment
-keep class * extends androidx.appcompat.app.AppCompatActivity

# ViewBinding generated classes
-keep class com.wahyuzero.replyforge.databinding.** { *; }

# Application class
-keep class com.wahyuzero.replyforge.ReplyForgeApp { *; }

# NotificationListenerService
-keep class * extends android.service.notification.NotificationListenerService

# CrashHandler (uses reflection-free Thread.setDefaultUncaughtExceptionHandler)
-keep class com.wahyuzero.replyforge.CrashHandler { *; }

# ═══════════════════════════════════════════
# General Kotlin
# ═══════════════════════════════════════════
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
