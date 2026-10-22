# ═══════════════════════════════════════════════════════════════════════════════
# Video Pocket — ProGuard / R8 Rules
# ═══════════════════════════════════════════════════════════════════════════════
#
# R8 full mode is activated by the gradle.properties entry:
#   android.enableR8.fullMode=true
#
# Shrinking percentage target:  ~40–60% reduction on release APK
# Optimisation passes:          R8 uses multiple rounds by default;
#                               proguard-android-optimize.txt enables them.
# DEX optimisation:             D8/R8 applies constant folding, dead code
#                               elimination, and class merging automatically.
#
# ═══════════════════════════════════════════════════════════════════════════════

# ── General Android / Kotlin ──────────────────────────────────────────────────

# Keep all entry points (Activities, Services, BroadcastReceivers, etc.)
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Kotlin metadata (needed for reflection, serialization, coroutines)
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# Kotlin coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ── Jetpack Compose ───────────────────────────────────────────────────────────

# Compose compiler plugin generates synthetic classes; keep their structure.
-keep class androidx.compose.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
-dontwarn androidx.compose.**

# ── ViewModel / Lifecycle ─────────────────────────────────────────────────────

-keep class androidx.lifecycle.** { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ── Video Pocket: App Models ──────────────────────────────────────────────────
# Keep all data classes and enums used for JSON persistence and state.

-keep class org.videopocket.probe.core.** { *; }
-keep class org.videopocket.probe.download.DownloadTask { *; }
-keep class org.videopocket.probe.download.DownloadState { *; }
-keep class org.videopocket.probe.idm.** { *; }
-keep class org.videopocket.probe.torrent.** { *; }
-keep class org.videopocket.probe.processing.ProcessingRequest { *; }
-keep class org.videopocket.probe.processing.ProcessingCapabilities { *; }
-keep class org.videopocket.probe.processing.CompressionPreset { *; }
-keep class org.videopocket.probe.processing.ProcessingMode { *; }
-keep class org.videopocket.probe.resolver.** { *; }
-keep class org.videopocket.probe.engine.** { *; }

# R8 default passes (do not force multi-pass optimization in constrained CI/Cloud environments)

# ── yt-dlp / youtubedl-android wrapper ───────────────────────────────────────
# The wrapper uses reflection, JNI, and Apache Commons Compress to unpack binaries.
# Aggressive shrinking and interface merging break ZipUtils and ServiceLoader.

-keep class com.yausername.** { *; }
-keepclassmembers class com.yausername.** { *; }
-keep class io.github.junkfood02.** { *; }
-keepclassmembers class io.github.junkfood02.** { *; }
-dontwarn com.yausername.**
-dontwarn io.github.junkfood02.**

# ── Apache Commons Compress (used by youtubedl-android ZipUtils) ───────────────
-keep class org.apache.commons.compress.** { *; }
-keepclassmembers class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**
-keep class org.tukaani.xz.** { *; }
-dontwarn org.tukaani.xz.**
-keep class * implements org.apache.commons.compress.archivers.ArchiveStreamProvider { *; }
-keep class * implements org.apache.commons.compress.compressors.CompressorStreamProvider { *; }

# Keep JNI-accessible native method declarations
-keepclasseswithmembernames class * {
    native <methods>;
}

# ── FFmpeg (libffmpeg.so, loaded via ProcessBuilder) ─────────────────────────
# FFmpeg is a native binary invoked via ProcessBuilder — no reflection needed.
# Keep the classes that parse its output.

-keep class org.videopocket.probe.processing.BundledFfmpeg { *; }
-keep class org.videopocket.probe.processing.LocalMediaProcessor { *; }
-keep class org.videopocket.probe.processing.ProcessingPolicy { *; }

# ── Jackson (JSON serialisation) ──────────────────────────────────────────────

-keep class com.fasterxml.jackson.** { *; }
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.* *;
}
-dontwarn com.fasterxml.jackson.**

# ── Apache Commons IO ─────────────────────────────────────────────────────────
-dontwarn org.apache.commons.io.**

# ── libtorrent4j (full flavor only — safe to include in standard too) ─────────
-keep class org.libtorrent4j.** { *; }
-keepclasseswithmembernames class org.libtorrent4j.** {
    native <methods>;
}
-dontwarn org.libtorrent4j.**

# ── Coil (image loading) ──────────────────────────────────────────────────────
-keep class coil.** { *; }
-dontwarn coil.**

# ── Okio / OkHttp (transitive from Coil) ─────────────────────────────────────
-dontwarn okio.**
-dontwarn okhttp3.**

# ── Suppress noisy warnings from the Android SDK stubs ───────────────────────
-dontwarn android.content.pm.PackageInstaller**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.**

# ── R8 / DEX optimisation hints ───────────────────────────────────────────────
# Allow R8 to inline where safe. Do NOT merge interfaces aggressively as this breaks ServiceLoader.

-allowaccessmodification

# ── Debugging: keep source file names and line numbers in stack traces ─────────
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
