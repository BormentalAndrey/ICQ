# Nexus Player ProGuard Rules

# Keep Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep ExoPlayer
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# Keep RenderScript
-keep class android.renderscript.** { *; }
-dontwarn android.renderscript.**

# Keep Audio Effects
-keep class android.media.audiofx.** { *; }
-keep class android.media.AudioTrack { *; }
-keep class android.media.AudioRecord { *; }

# Keep our models
-keep class com.nexus.player.data.model.** { *; }
-keep class com.nexus.player.player.core.CorruptedFileHandler { *; }
-keep class com.nexus.player.player.core.NexusExtractor { *; }
