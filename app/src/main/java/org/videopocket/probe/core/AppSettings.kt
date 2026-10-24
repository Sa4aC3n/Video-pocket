package org.videopocket.probe.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode(val label: String, val arabicLabel: String) {
    SYSTEM("System Default", "تلقائي حسب النظام"),
    LIGHT("Light Mode", "الوضع الفاتح"),
    DARK("Dark Mode", "الوضع الداكن")
}

enum class DefaultQualityPreset(val label: String, val arabicLabel: String) {
    ASK_EVERY_TIME("Ask Every Time", "السؤال في كل مرة"),
    BEST_AVAILABLE("Best Available", "أفضل جودة متاحة"),
    BALANCED("Balanced (HD)", "متوازن (دقة عالية)"),
    SMALLEST_FILE("Smallest File", "أصغر حجم ملف")
}

object AppSettings {
    private const val PREFS_NAME = "video_pocket_settings"
    private const val KEY_THEME = "app_theme_mode"
    private const val KEY_LANGUAGE = "app_language_arabic"
    private const val KEY_DEFAULT_QUALITY = "default_quality_preset"
    private const val KEY_DEFAULT_FORMAT = "default_video_format"
    private const val KEY_DEFAULT_AUDIO_FORMAT = "default_audio_format"
    private const val KEY_CLIPBOARD_DETECTION = "smart_clipboard_detection"
    private const val KEY_FLOATING_BUBBLE = "floating_bubble_enabled"
    private const val KEY_MAX_CONCURRENT = "max_concurrent_downloads"

    private lateinit var prefs: SharedPreferences

    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set
    var isArabic by mutableStateOf(true)
        private set
    var defaultQuality by mutableStateOf(DefaultQualityPreset.BEST_AVAILABLE)
        private set
    var defaultVideoFormat by mutableStateOf("mp4")
        private set
    var defaultAudioFormat by mutableStateOf("mp3")
        private set
    var smartClipboardEnabled by mutableStateOf(true)
        private set
    var floatingBubbleEnabled by mutableStateOf(false)
        private set
    var maxConcurrentDownloads by mutableIntStateOf(2)
        private set

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val themeStr = prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
            themeMode = try { ThemeMode.valueOf(themeStr) } catch (_: Exception) { ThemeMode.SYSTEM }

            isArabic = prefs.getBoolean(KEY_LANGUAGE, true)

            val qualityStr = prefs.getString(KEY_DEFAULT_QUALITY, DefaultQualityPreset.BEST_AVAILABLE.name)
                ?: DefaultQualityPreset.BEST_AVAILABLE.name
            defaultQuality = try { DefaultQualityPreset.valueOf(qualityStr) } catch (_: Exception) { DefaultQualityPreset.BEST_AVAILABLE }

            defaultVideoFormat = prefs.getString(KEY_DEFAULT_FORMAT, "mp4") ?: "mp4"
            defaultAudioFormat = prefs.getString(KEY_DEFAULT_AUDIO_FORMAT, "mp3") ?: "mp3"
            smartClipboardEnabled = prefs.getBoolean(KEY_CLIPBOARD_DETECTION, true)
            floatingBubbleEnabled = prefs.getBoolean(KEY_FLOATING_BUBBLE, false)
            maxConcurrentDownloads = prefs.getInt(KEY_MAX_CONCURRENT, 2).coerceIn(1, 4)
        }
    }

    fun setTheme(mode: ThemeMode) {
        themeMode = mode
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_THEME, mode.name).apply()
        }
    }

    fun setLanguage(arabic: Boolean) {
        isArabic = arabic
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_LANGUAGE, arabic).apply()
        }
    }

    fun setDefaultQualityPreset(preset: DefaultQualityPreset) {
        defaultQuality = preset
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_DEFAULT_QUALITY, preset.name).apply()
        }
    }

    fun setDefaultVideoFormatSetting(format: String) {
        defaultVideoFormat = format.lowercase()
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_DEFAULT_FORMAT, defaultVideoFormat).apply()
        }
    }

    fun setDefaultAudioFormatSetting(format: String) {
        defaultAudioFormat = format.lowercase()
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_DEFAULT_AUDIO_FORMAT, defaultAudioFormat).apply()
        }
    }

    fun setSmartClipboard(enabled: Boolean) {
        smartClipboardEnabled = enabled
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_CLIPBOARD_DETECTION, enabled).apply()
        }
    }

    fun setFloatingBubbleSetting(enabled: Boolean) {
        floatingBubbleEnabled = enabled
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_FLOATING_BUBBLE, enabled).apply()
        }
    }

    fun setMaxConcurrent(count: Int) {
        maxConcurrentDownloads = count.coerceIn(1, 4)
        if (::prefs.isInitialized) {
            prefs.edit().putInt(KEY_MAX_CONCURRENT, maxConcurrentDownloads).apply()
        }
    }
}
