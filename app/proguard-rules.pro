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

# Keep all component entry points for Android OS instantiation
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.app.Application
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Kotlin metadata & attributes
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn kotlin.reflect.**

# ── Video Pocket: JSON / Data Serialization Models ───────────────────────────
# Keep data fields and constructors for Jackson / JSONObject parsing while
# allowing all UI, business logic, and engines to be aggressively obfuscated.

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    !private <fields>;
    !static !transient <methods>;
    !private <methods>;
    <init>(...);
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembers class org.videopocket.probe.core.** {
    <init>(...);
    <fields>;
}

-keepclassmembers class org.videopocket.probe.resolver.** {
    <init>(...);
    <fields>;
}

-keepclassmembers class org.videopocket.probe.sponsorblock.** {
    <init>(...);
    <fields>;
}

-keepclassmembers class org.videopocket.probe.vault.VaultItem {
    <init>(...);
    <fields>;
}

-keepclassmembers class org.videopocket.probe.download.DownloadTask {
    <init>(...);
    <fields>;
}

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

# ── Google Mobile Ads (AdMob) ─────────────────────────────────────────────────
-keep public class com.google.android.gms.ads.** {
   public *;
}
-keep public class com.google.ads.** {
   public *;
}
-dontwarn com.google.android.gms.ads.**
