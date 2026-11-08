# Nocturn Proguard / R8 Rules

# Preserve Kotlin Reflection & Serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    static ** Companion;
}
-dontwarn kotlinx.serialization.**

# Ktor Client
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }

# Accompanist Lyrics
-keep class com.mocharealm.accompanist.** { *; }

# Miuix Blur
-keep class top.yukonga.miuix.** { *; }

# AndroidX / Media3 / ExoPlayer
-dontwarn androidx.media3.**

# Lyricon Provider SDK
-keep class io.github.proify.lyricon.** { *; }
-dontwarn io.github.proify.lyricon.**
