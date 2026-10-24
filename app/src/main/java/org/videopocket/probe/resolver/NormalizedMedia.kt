package org.videopocket.probe.resolver

import org.videopocket.probe.core.MediaInfo
import org.videopocket.probe.core.SubtitleTrack

enum class MediaType(val label: String, val arabicLabel: String) {
    VIDEO("Video", "فيديو"),
    AUDIO("Audio", "صوت"),
    IMAGE("Image", "صورة"),
    IMAGE_GALLERY("Gallery", "معرض صور"),
    GIF("GIF", "صورة متحركة"),
    STORY("Story", "قصة"),
    REEL("Reel", "ريلز"),
    SHORT_VIDEO("Short", "فيديو قصير"),
    POST("Post", "منشور"),
    PLAYLIST("Playlist", "قائمة تشغيل"),
    CLIP("Clip", "مقطع"),
    SUBTITLE("Subtitle", "ترجمة")
}

enum class ResolutionClassification(val label: String, val arabicLabel: String) {
    PUBLIC_RESOLVED("Publicly Resolved", "تم الاستخراج العام بنجاح"),
    PUBLIC_MEDIA_UNAVAILABLE("Public Media Unavailable", "الوسائط العامة غير متوفرة"),
    LOGIN_REQUIRED("Login Required", "يتطلب تسجيل الدخول"),
    PRIVATE_MEDIA("Private Media", "محتوى خاص"),
    GEO_RESTRICTED("Geo-Restricted", "مقيد جغرافياً"),
    RATE_LIMITED("Rate Limited", "تجاوز حد الطلبات"),
    DRM_PROTECTED("DRM Protected", "محمي بحقوق رقمية DRM"),
    EXTRACTOR_OUTDATED("Extractor Outdated", "المستخرج يحتاج تحديث"),
    TEMPORARILY_UNAVAILABLE("Temporarily Unavailable", "غير متاح مؤقتاً")
}

enum class ResolutionStrategy(val label: String, val arabicLabel: String) {
    DEDICATED_EXTRACTOR("Dedicated Extractor", "مستخرج مخصص"),
    CANONICAL_EXPANSION("Canonical Public URL", "توسيع الرابط الأساسي"),
    GENERIC_FALLBACK("Universal Web Resolver", "المستخرج الشامل"),
    PUBLIC_HTML_OPENGRAPH("Public OpenGraph / HTML5", "وسائط HTML5 / OpenGraph العامة"),
    PUBLIC_MEDIA_MANIFEST("Public Media Manifest", "مانيفست الوسائط العام"),
    DIRECT_STREAM_RESOLVE("Direct Stream Resolution", "فحص البث المباشر")
}

enum class ResolverErrorType(val defaultMessage: String, val defaultArabic: String) {
    INVALID_URL("Please enter a valid media URL.", "يرجى إدخال رابط وسائط صالح."),
    UNSUPPORTED_SOURCE("This media source or platform is not supported.", "هذا المصدر أو المنصة غير مدعومة حالياً."),
    PROVIDER_DISABLED("This provider is currently disabled in Settings.", "هذا المزود معطل حالياً في الإعدادات."),
    PROVIDER_UNAVAILABLE("This provider is currently unavailable.", "هذا المزود غير متاح حالياً."),
    MEDIA_NOT_FOUND("The requested media was not found or was removed.", "الوسائط المطلوبة غير موجودة أو تم حذفها."),
    PUBLIC_MEDIA_UNAVAILABLE("The requested public media is no longer available on this page.", "الوسائط العامة المطلوبة لم تعد متوفرة في هذه الصفحة."),
    PRIVATE_MEDIA("This media is private or requires authentication.", "هذا المحتوى خاص أو يتطلب تسجيل الدخول."),
    LOGIN_REQUIRED("Authentication is required to view this media.", "يتطلب هذا المحتوى تسجيل الدخول أو المصادقة."),
    GEO_RESTRICTED("This media is not available in your geographical region.", "هذا المحتوى غير متاح في منطقتك الجغرافية."),
    RATE_LIMITED("Request rate limit exceeded. Please wait a moment and try again.", "تم تجاوز حد الطلبات مؤقتاً. يرجى الانتظار والمحاولة لاحقاً."),
    DRM_PROTECTED("This media is protected by Digital Rights Management (DRM) and cannot be downloaded.", "هذا المحتوى محمي بنظام إدارة الحقوق الرقمية (DRM) ولا يمكن تنزيله."),
    EXTRACTOR_OUTDATED("The platform parser requires an update to handle changes on this page.", "مستخرج المنصة بحاجة إلى تحديث لمواكبة تغييرات الصفحة."),
    TEMPORARILY_UNAVAILABLE("The media source is temporarily unavailable.", "المصدر غير متاح مؤقتاً. يرجى المحاولة لاحقاً."),
    NO_VARIANTS("No downloadable media streams found.", "لم يتم العثور على صيغ قابلة للتنزيل."),
    FORMAT_UNAVAILABLE("The requested format is not available for this media.", "الصيغة المطلوبة غير متوفرة لهذه الوسائط."),
    QUALITY_UNAVAILABLE("The selected quality is no longer available. Please re-analyze the link.", "الجودة المحددة غير متوفرة حالياً. يرجى إعادة فحص الرابط."),
    STREAM_EXPIRED("The media stream link expired. Please analyze the link again.", "انتهت صلاحية رابط الوسائط. يرجى إعادة فحص الرابط."),
    NETWORK_ERROR("Network connection failed. Please check your connection.", "تعذر الاتصال بالشبكة. يرجى التحقق من اتصالك."),
    PROVIDER_CHANGED("This platform format may have changed. Please try again later.", "قد يكون هيكل المنصة تغير. يرجى المحاولة لاحقاً."),
    PROCESSING_FAILED("Failed to process media metadata.", "فشلت معالجة بيانات الوسائط."),
    DOWNLOAD_ERROR("Failed to download media file.", "فشل تنزيل ملف الوسائط."),
    MERGE_ERROR("Failed to combine video and audio streams.", "فشل في دمج مساري الفيديو والصوت."),
    INSUFFICIENT_STORAGE("Insufficient device storage to process this media.", "مساحة التخزين غير كافية لمعالجة هذه الوسائط."),
    CANCELLED("Operation was cancelled by user.", "تم إلغاء العملية بواسطة المستخدم."),
    ANTI_BOT_CHALLENGE("The source temporarily blocked automated access. Try again later.", "المصدر أوقف الوصول التلقائي مؤقتاً. حاول مرة أخرى لاحقاً."),
    TIMEOUT("Connection to the media source timed out.", "انتهت مهلة الاتصال بالمصدر."),
    PARSER_FAILURE("Failed to parse media formats from source page.", "فشل تحليل صيغ الوسائط من صفحة المصدر."),
    LIVE_MEDIA_UNSUPPORTED("Live streams and upcoming premieres are not downloadable.", "البث المباشر والعروض الأولى القادمة غير قابلة للتنزيل."),
    PLAYLIST_UNSUPPORTED("This playlist cannot be extracted.", "لا يمكن استخراج قائمة التشغيل هذه."),
    CONVERSION_FAILED("Failed to convert or process media format.", "فشل تحويل صيغة الوسائط."),
    UNKNOWN("An unexpected error occurred while analyzing the link.", "حدث خطأ غير متوقع أثناء فحص الرابط.");

    companion object {
        val PRIVATE_CONTENT = PRIVATE_MEDIA
        val STORAGE_ERROR = INSUFFICIENT_STORAGE
        val UNSUPPORTED_MEDIA = NO_VARIANTS
        val EXTRACTOR_FAILURE = EXTRACTOR_OUTDATED
        val PROVIDER_TEMPORARILY_UNAVAILABLE = TEMPORARILY_UNAVAILABLE
    }
}

fun ResolverErrorType.toClassification(): ResolutionClassification = when (this) {
    ResolverErrorType.LOGIN_REQUIRED -> ResolutionClassification.LOGIN_REQUIRED
    ResolverErrorType.PRIVATE_MEDIA -> ResolutionClassification.PRIVATE_MEDIA
    ResolverErrorType.PUBLIC_MEDIA_UNAVAILABLE, ResolverErrorType.MEDIA_NOT_FOUND, ResolverErrorType.NO_VARIANTS, ResolverErrorType.LIVE_MEDIA_UNSUPPORTED, ResolverErrorType.PLAYLIST_UNSUPPORTED -> ResolutionClassification.PUBLIC_MEDIA_UNAVAILABLE
    ResolverErrorType.GEO_RESTRICTED -> ResolutionClassification.GEO_RESTRICTED
    ResolverErrorType.RATE_LIMITED, ResolverErrorType.ANTI_BOT_CHALLENGE -> ResolutionClassification.RATE_LIMITED
    ResolverErrorType.DRM_PROTECTED -> ResolutionClassification.DRM_PROTECTED
    ResolverErrorType.EXTRACTOR_OUTDATED, ResolverErrorType.PROVIDER_CHANGED, ResolverErrorType.PARSER_FAILURE -> ResolutionClassification.EXTRACTOR_OUTDATED
    ResolverErrorType.TEMPORARILY_UNAVAILABLE, ResolverErrorType.PROVIDER_UNAVAILABLE, ResolverErrorType.NETWORK_ERROR, ResolverErrorType.TIMEOUT -> ResolutionClassification.TEMPORARILY_UNAVAILABLE
    else -> ResolutionClassification.TEMPORARILY_UNAVAILABLE
}

data class ResolutionAttempt(
    val strategy: ResolutionStrategy,
    val providerId: String,
    val success: Boolean,
    val errorType: ResolverErrorType? = null,
    val durationMs: Long = 0L
)

data class ResolutionTrace(
    val attempts: List<ResolutionAttempt> = emptyList(),
    val finalStrategy: ResolutionStrategy? = null,
    val finalClassification: ResolutionClassification
)

data class ResolverError(
    val type: ResolverErrorType,
    val detail: String? = null,
    val classification: ResolutionClassification = type.toClassification(),
    val trace: ResolutionTrace? = null
) {
    fun userMessage(arabic: Boolean): String {
        return if (arabic) type.defaultArabic else type.defaultMessage
    }
}

data class MediaVariant(
    val id: String,
    val quality: String, // e.g. "1080p", "720p", "320kbps"
    val width: Int = 0,
    val height: Int = 0,
    val fps: Int = 0,
    val format: String, // "mp4", "webm", "mkv", "mp3", "m4a"
    val codec: String? = null,
    val audioCodec: String? = null,
    val bitrate: Int = 0,
    val fileSize: Long? = null,
    val videoUrl: String? = null,
    val audioUrl: String? = null,
    val requiresMerge: Boolean = false,
    val isAudioOnly: Boolean = false
) {
    val formattedSize: String? get() {
        val bytes = fileSize ?: return null
        if (bytes <= 0) return null
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1000) String.format(java.util.Locale.US, "%.1f GB", mb / 1024.0)
        else String.format(java.util.Locale.US, "%.1f MB", mb)
    }
}

data class MediaSubtitle(
    val languageCode: String,
    val languageName: String,
    val format: String, // "srt", "vtt", "txt"
    val url: String,
    val isAutoGenerated: Boolean = false
)

data class MediaItem(
    val id: String,
    val title: String,
    val mediaType: MediaType,
    val url: String? = null,
    val thumbnailUrl: String? = null,
    val duration: Double? = null,
    val variants: List<MediaVariant> = emptyList(),
    val subtitles: List<MediaSubtitle> = emptyList()
)

data class NormalizedMetadata(
    val id: String,
    val canonicalUrl: String,
    val platform: String,
    val platformIcon: String,
    val title: String,
    val author: String? = null,
    val thumbnail: String? = null,
    val duration: Double? = null,
    val mediaType: MediaType = MediaType.VIDEO,
    val variants: List<MediaVariant> = emptyList(),
    val subtitles: List<MediaSubtitle> = emptyList(),
    val items: List<MediaItem> = emptyList(),
    val rawInfo: MediaInfo? = null,
    val resolutionStrategy: ResolutionStrategy = ResolutionStrategy.DEDICATED_EXTRACTOR,
    val classification: ResolutionClassification = ResolutionClassification.PUBLIC_RESOLVED,
    val resolvedViaSmartPublicResolution: Boolean = false,
    val trace: ResolutionTrace? = null
) {
    val heights: List<Int> get() = variants
        .filter { !it.isAudioOnly && it.height in 1..4320 }
        .map { it.height }
        .distinct()
        .sortedDescending()

    val defaultHeight: Int get() = heights.firstOrNull { it <= 1080 } ?: heights.minOrNull() ?: 0

    val audioVariants: List<MediaVariant> get() = variants.filter { it.isAudioOnly }

    val videoVariants: List<MediaVariant> get() = variants.filter { !it.isAudioOnly }

    fun getBestAvailable(): MediaVariant? {
        return videoVariants.maxByOrNull { it.height } ?: videoVariants.firstOrNull()
    }

    fun getBalanced(): MediaVariant? {
        val fhdOrHd = videoVariants.filter { it.height in 720..1080 }
        return fhdOrHd.maxByOrNull { it.height } ?: getBestAvailable()
    }

    fun getSmallestFile(): MediaVariant? {
        val nonZero = videoVariants.filter { it.height > 0 }
        return nonZero.minByOrNull { it.fileSize ?: (it.height.toLong() * 1000L) } ?: videoVariants.minByOrNull { it.height }
    }

    fun getBestAudio(): MediaVariant? {
        return audioVariants.maxByOrNull { it.bitrate } ?: audioVariants.firstOrNull()
    }
}

sealed class MediaResolverResult {
    data class Success(val metadata: NormalizedMetadata) : MediaResolverResult()
    data class Failure(val error: ResolverError) : MediaResolverResult()

    val classification: ResolutionClassification
        get() = when (this) {
            is Success -> metadata.classification
            is Failure -> error.classification
        }

    val trace: ResolutionTrace?
        get() = when (this) {
            is Success -> metadata.trace
            is Failure -> error.trace
        }
}
