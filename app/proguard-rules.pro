# ReplyForge ProGuard Rules

# Keep all Room entities and converters
-keep class com.wahyuzero.replyforge.data.model.** { *; }
-keep class com.wahyuzero.replyforge.data.db.Converters { *; }
-keep class * extends androidx.room.Entity

# Keep enums used in Room TypeConverters
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep MatchType (used in TypeConverter, not in data.model package)
-keep class com.wahyuzero.replyforge.ui.rule.MatchType { *; }
-keep class com.wahyuzero.replyforge.ui.rule.ContactFilter { *; }
-keep class com.wahyuzero.replyforge.ui.rule.ResponseMode { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# DataStore
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# Kotlin coroutines (don't strip continuation classes)
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Gson / Retrofit (if used)
-keepattributes Signature
-keepattributes *Annotation*

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep all Activity and Fragment classes
-keep class * extends android.app.Activity
-keep class * extends androidx.fragment.app.Fragment
-keep class * extends androidx.appcompat.app.AppCompatActivity

# Keep ViewBinding generated classes
-keep class com.wahyuzero.replyforge.databinding.** { *; }

# Keep Application class
-keep class com.wahyuzero.replyforge.ReplyForgeApp { *; }

# Keep NotificationListenerService
-keep class * extends android.service.notification.NotificationListenerService
