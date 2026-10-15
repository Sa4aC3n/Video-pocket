package org.videopocket.probe.resolver

import android.net.Uri
import org.videopocket.probe.core.*
import org.videopocket.probe.engine.ProbeEngine

abstract class BaseMediaProvider : MediaProvider {

    override val capabilities: ProviderCapabilities = ProviderCapabilities()

    override fun canHandle(url: String): Boolean {
        val host = try {
            val trimmed = url.trim()
            val uri = java.net.URI(trimmed)
            uri.host?.lowercase() ?: ""
        } catch (_: Exception) {
            try { Uri.parse(url).host?.lowercase() ?: "" } catch (_: Exception) { "" }
        }
        return supportedDomains.any { domain -> host == domain || host.endsWith(".$domain") }
    }

    override fun normalizeUrl(url: String): String {
        return try {
            val trimmed = url.trim()
            val uri = java.net.URI(trimmed)
            val query = uri.rawQuery
            if (query.isNullOrBlank()) return trimmed
            val cleanQuery = query.split("&").filter { pair ->
                val key = pair.substringBefore("=").lowercase()
                key in setOf("v", "list", "p", "t", "start", "end", "index") ||
                        (!key.startsWith("utm_") && key != "si" && key != "igsh" &&
                                key != "feature" && key != "fbclid" && key != "is_from_webapp")
            }.joinToString("&")
            val newUri = java.net.URI(
                uri.scheme,
                uri.authority,
                uri.path,
                if (cleanQuery.isBlank()) null else cleanQuery,
                uri.fragment
            )
            newUri.toString()
        } catch (_: Exception) {
            try {
                val trimmed = url.trim()
                val uri = Uri.parse(trimmed)
                val builder = uri.buildUpon()
                val allowedQuery = uri.queryParameterNames.filter { param ->
                    param in setOf("v", "list", "p", "t", "start", "end", "index") ||
                            (!param.startsWith("utm_") && param != "si" && param != "igsh" &&
                                    param != "feature" && param != "fbclid" && param != "is_from_webapp")
                }
                builder.clearQuery()
                for (q in allowedQuery) {
                    uri.getQueryParameter(q)?.let { builder.appendQueryParameter(q, it) }
                }
                builder.build().toString()
            } catch (_: Exception) {
                url.trim()
            }
        }
    }

    override suspend fun resolve(url: String, engine: ProbeEngine): MediaResolverResult {
        val normalized = normalizeUrl(url)
        return try {
            val isPlaylist = normalized.contains("list=") || normalized.contains("/playlist") || normalized.contains("/sets/")
            val info = engine.inspect(normalized, isPlaylist)
            val metadata = mapToMetadata(normalized, info)
            MediaResolverResult.Success(metadata)
        } catch (e: Exception) {
            val problem = ProbeEngine.classify(e)
            val msg = e.message.orEmpty().lowercase()
            val errType = when (problem) {
                Problem.INVALID_URL -> ResolverErrorType.INVALID_URL
                Problem.UNSUPPORTED -> {
                    if ("drm" in msg || "widevine" in msg) ResolverErrorType.DRM_PROTECTED
                    else ResolverErrorType.UNSUPPORTED_SOURCE
                }
                Problem.LOGIN_REQUIRED -> ResolverErrorType.LOGIN_REQUIRED
                Problem.REMOVED -> ResolverErrorType.MEDIA_NOT_FOUND
                Problem.RESTRICTED -> {
                    if ("geo" in msg || "country" in msg || "region" in msg) ResolverErrorType.GEO_RESTRICTED
                    else ResolverErrorType.RATE_LIMITED
                }
                Problem.NETWORK -> ResolverErrorType.NETWORK_ERROR
                Problem.SPACE -> ResolverErrorType.INSUFFICIENT_STORAGE
                Problem.ENGINE -> {
                    if ("sign in" in msg || "login" in msg || "log in" in msg || "private" in msg) ResolverErrorType.LOGIN_REQUIRED
                    else if ("429" in msg || "rate limit" in msg || "too many requests" in msg) ResolverErrorType.RATE_LIMITED
                    else if ("format" in msg || "extractor" in msg) ResolverErrorType.EXTRACTOR_OUTDATED
                    else ResolverErrorType.PROCESSING_FAILED
                }
                else -> ResolverErrorType.UNKNOWN
            }
            MediaResolverResult.Failure(ResolverError(errType, e.message))
        }
    }

    protected open fun detectMediaType(url: String, info: MediaInfo): MediaType {
        val u = url.lowercase()
        return when {
            info.isPlaylist -> MediaType.PLAYLIST
            u.contains("/reel") -> MediaType.REEL
            u.contains("/stories/") || u.contains("/story/") -> MediaType.STORY
            u.contains("/shorts/") || u.contains("tiktok.com") -> MediaType.SHORT_VIDEO
            info.formats.all { it.audio && !it.video && it.videoKnown } -> MediaType.AUDIO
            else -> MediaType.VIDEO
        }
    }

    protected open fun mapToMetadata(url: String, info: MediaInfo): NormalizedMetadata {
        val mediaType = detectMediaType(url, info)
        val variants = mutableListOf<MediaVariant>()

        if (info.isPlaylist) {
            variants.add(
                MediaVariant(
                    id = "best",
                    quality = "Best Available (Per Item)",
                    height = 0,
                    format = "mp4",
                    isAudioOnly = false
                )
            )
            variants.add(
                MediaVariant(
                    id = "best_audio",
                    quality = "Audio Only (Per Item)",
                    height = 0,
                    format = "mp3",
                    isAudioOnly = true
                )
            )
        } else {
            // 1. Map video formats
            val heights = info.formats.filter { it.video && it.height > 0 }.map { it.height }.distinct().sortedDescending()
            for (h in heights) {
                val tier = QualityTier.fromHeight(h)
                val videoOnly = info.formats.firstOrNull { it.video && it.height == h && !it.audio }
                val combined = info.formats.firstOrNull { it.video && it.height == h && it.audio }
                val chosen = combined ?: videoOnly
                if (chosen != null) {
                    variants.add(
                        MediaVariant(
                            id = chosen.id,
                            quality = "${h}p • ${tier.category}",
                            width = 0,
                            height = h,
                            format = chosen.extension.ifBlank { "mp4" },
                            fileSize = chosen.bytes,
                            videoUrl = chosen.url,
                            requiresMerge = chosen.video && !chosen.audio && chosen.audioKnown,
                            isAudioOnly = false
                        )
                    )
                }
            }

            if (heights.isEmpty()) {
                info.formats.firstOrNull { it.height == 0 && (it.video || !it.videoKnown) }?.let { source ->
                    variants.add(MediaVariant(id = source.id, quality = "Source media (quality unknown)",
                        height = 0, format = source.extension, fileSize = source.bytes, videoUrl = source.url))
                }
            }

            // 2. Map audio formats truthfully
            val audioFormats = info.formats.filter { it.audio && !it.video && it.videoKnown }
            if (audioFormats.isNotEmpty()) {
                val bestAudio = audioFormats.maxByOrNull { it.bytes ?: 0 }
                val realFmt = bestAudio?.extension?.ifBlank { "m4a" } ?: "m4a"
                variants.add(
                    MediaVariant(
                        id = bestAudio?.id ?: "original_audio",
                        quality = "Original Audio (${realFmt.uppercase()})",
                        height = 0,
                        format = realFmt,
                        bitrate = 0,
                        fileSize = bestAudio?.bytes,
                        videoUrl = bestAudio?.url,
                        isAudioOnly = true
                    )
                )
            }
            // Supported output conversion target (with un-faked null file size)
            variants.add(
                MediaVariant(
                    id = "mp3_converted",
                    quality = "MP3 Audio (Converted)",
                    height = 0,
                    format = "mp3",
                    bitrate = 192,
                    fileSize = null,
                    isAudioOnly = true
                )
            )
        }

        // 3. Map subtitles
        val subtitles = info.subtitles.map { sub ->
            MediaSubtitle(
                languageCode = sub.lang,
                languageName = sub.name.ifBlank { sub.lang.uppercase() },
                format = sub.ext.ifBlank { "srt" },
                url = sub.url,
                isAutoGenerated = sub.isAuto
            )
        }

        // 4. Map playlist/carousel items
        val items = info.entries.map { entry ->
            MediaItem(
                id = entry.id,
                title = entry.title,
                mediaType = MediaType.VIDEO,
                url = entry.url,
                duration = entry.duration
            )
        }

        return NormalizedMetadata(
            id = url.hashCode().toString(),
            canonicalUrl = url,
            platform = name,
            platformIcon = iconName,
            title = info.title.ifBlank { name + " Media" },
            author = info.uploader,
            thumbnail = info.thumbnailUrl,
            duration = info.duration,
            mediaType = mediaType,
            variants = variants,
            subtitles = subtitles,
            items = items,
            rawInfo = info
        )
    }
}

class TikTokProvider : BaseMediaProvider() {
    override val id = "tiktok"
    override val name = "TikTok"
    override val iconName = "tiktok"
    override val supportedMediaTypes = setOf(MediaType.SHORT_VIDEO, MediaType.VIDEO, MediaType.AUDIO, MediaType.IMAGE_GALLERY)
    override val supportedDomains = listOf("tiktok.com", "vm.tiktok.com", "vt.tiktok.com")
    override val capabilities = ProviderCapabilities(
        video = true, audio = true, image = true, gallery = true, shortVideo = true, clip = true,
        separateVideoAudioStreams = false, multipleQualities = false, multipleFormats = false
    )

    override fun detectMediaType(url: String, info: MediaInfo): MediaType {
        return if (info.formats.all { it.audio && !it.video }) MediaType.AUDIO else MediaType.SHORT_VIDEO
    }
}

class InstagramProvider : BaseMediaProvider() {
    override val id = "instagram"
    override val name = "Instagram"
    override val iconName = "instagram"
    override val supportedMediaTypes = setOf(MediaType.REEL, MediaType.POST, MediaType.STORY, MediaType.VIDEO, MediaType.IMAGE_GALLERY)
    override val supportedDomains = listOf("instagram.com", "instagr.am")
    override val capabilities = ProviderCapabilities(
        video = true, audio = true, image = true, gallery = true, reel = true, story = true, clip = true,
        separateVideoAudioStreams = false, multipleQualities = false, multipleFormats = false
    )

    override fun detectMediaType(url: String, info: MediaInfo): MediaType {
        val u = url.lowercase()
        return when {
            "/reel" in u -> MediaType.REEL
            "/stories/" in u || "/story/" in u -> MediaType.STORY
            info.entries.size > 1 -> MediaType.IMAGE_GALLERY
            else -> MediaType.POST
        }
    }
}

class FacebookProvider : BaseMediaProvider() {
    override val id = "facebook"
    override val name = "Facebook"
    override val iconName = "facebook"
    override val supportedMediaTypes = setOf(MediaType.VIDEO, MediaType.REEL, MediaType.STORY)
    override val supportedDomains = listOf("facebook.com", "fb.watch", "fb.com", "m.facebook.com")
    override val capabilities = ProviderCapabilities(
        video = true, audio = true, reel = true, story = true, clip = true,
        separateVideoAudioStreams = true, multipleQualities = true, multipleFormats = false
    )

    override fun detectMediaType(url: String, info: MediaInfo): MediaType {
        val u = url.lowercase()
        return when {
            "/reel" in u -> MediaType.REEL
            "/stories/" in u -> MediaType.STORY
            else -> MediaType.VIDEO
        }
    }
}

class XProvider : BaseMediaProvider() {
    override val id = "twitter"
    override val name = "X / Twitter"
    override val iconName = "x"
    override val supportedMediaTypes = setOf(MediaType.VIDEO, MediaType.GIF, MediaType.SHORT_VIDEO)
    override val supportedDomains = listOf("twitter.com", "x.com", "t.co")
    override val capabilities = ProviderCapabilities(
        video = true, audio = true, gif = true, shortVideo = true, clip = true,
        separateVideoAudioStreams = false, multipleQualities = true, multipleFormats = false
    )

    override fun detectMediaType(url: String, info: MediaInfo): MediaType {
        return MediaType.VIDEO
    }
}

class RedditProvider : BaseMediaProvider() {
    override val id = "reddit"
    override val name = "Reddit"
    override val iconName = "reddit"
    override val supportedMediaTypes = setOf(MediaType.VIDEO, MediaType.GIF, MediaType.POST)
    override val supportedDomains = listOf("reddit.com", "redd.it", "v.redd.it")
    override val capabilities = ProviderCapabilities(
        video = true, audio = true, gif = true, clip = true,
        separateVideoAudioStreams = true, multipleQualities = true, multipleFormats = false
    )
}

class VimeoProvider : BaseMediaProvider() {
    override val id = "vimeo"
    override val name = "Vimeo"
    override val iconName = "vimeo"
    override val supportedMediaTypes = setOf(MediaType.VIDEO)
    override val supportedDomains = listOf("vimeo.com", "player.vimeo.com")
    override val capabilities = ProviderCapabilities(
        video = true, audio = true, clip = true,
        separateVideoAudioStreams = true, multipleQualities = true, multipleFormats = true
    )
}

class DailymotionProvider : BaseMediaProvider() {
    override val id = "dailymotion"
    override val name = "Dailymotion"
    override val iconName = "dailymotion"
    override val supportedMediaTypes = setOf(MediaType.VIDEO, MediaType.PLAYLIST)
    override val supportedDomains = listOf("dailymotion.com", "dai.ly")
    override val capabilities = ProviderCapabilities(
        video = true, audio = true, playlist = true, clip = true,
        separateVideoAudioStreams = false, multipleQualities = true, multipleFormats = true
    )
}

class BilibiliProvider : BaseMediaProvider() {
    override val id = "bilibili"
    override val name = "Bilibili"
    override val iconName = "bilibili"
    override val supportedMediaTypes = setOf(MediaType.VIDEO, MediaType.AUDIO)
    override val supportedDomains = listOf("bilibili.com", "b23.tv")
    override val capabilities = ProviderCapabilities(
        video = true, audio = true, clip = true,
        separateVideoAudioStreams = true, multipleQualities = true, multipleFormats = true
    )
}

class SoundCloudProvider : BaseMediaProvider() {
    override val id = "soundcloud"
    override val name = "SoundCloud"
    override val iconName = "soundcloud"
    override val supportedMediaTypes = setOf(MediaType.AUDIO, MediaType.PLAYLIST)
    override val supportedDomains = listOf("soundcloud.com", "on.soundcloud.com")
    override val capabilities = ProviderCapabilities(
        video = false, audio = true, playlist = true, clip = false,
        separateVideoAudioStreams = false, multipleQualities = true, multipleFormats = true
    )

    override fun detectMediaType(url: String, info: MediaInfo): MediaType {
        return if (info.isPlaylist) MediaType.PLAYLIST else MediaType.AUDIO
    }
}

class TumblrProvider : BaseMediaProvider() {
    override val id = "tumblr"
    override val name = "Tumblr"
    override val iconName = "tumblr"
    override val supportedMediaTypes = setOf(MediaType.VIDEO, MediaType.AUDIO, MediaType.IMAGE, MediaType.GIF)
    override val supportedDomains = listOf("tumblr.com", "tmblr.co")
    override val capabilities = ProviderCapabilities(
        video = true, audio = true, image = true, gif = true, clip = true,
        separateVideoAudioStreams = false, multipleQualities = false, multipleFormats = true
    )
}

class SnapchatProvider : BaseMediaProvider() {
    override val id = "snapchat"
    override val name = "Snapchat"
    override val iconName = "snapchat"
    override val supportedMediaTypes = setOf(MediaType.STORY, MediaType.VIDEO)
    override val supportedDomains = listOf("snapchat.com", "story.snapchat.com")
    override val capabilities = ProviderCapabilities(
        video = true, audio = true, story = true, shortVideo = true, clip = true,
        separateVideoAudioStreams = false, multipleQualities = false, multipleFormats = false
    )

    override fun detectMediaType(url: String, info: MediaInfo): MediaType {
        return MediaType.STORY
    }
}

class PinterestProvider : BaseMediaProvider() {
    override val id = "pinterest"
    override val name = "Pinterest"
    override val iconName = "pinterest"
    override val supportedMediaTypes = setOf(MediaType.VIDEO, MediaType.IMAGE)
    override val supportedDomains = listOf("pinterest.com", "pin.it")
    override val capabilities = ProviderCapabilities(
        video = true, audio = true, image = true, gallery = false, clip = true,
        separateVideoAudioStreams = false, multipleQualities = false, multipleFormats = false
    )

    override fun detectMediaType(url: String, info: MediaInfo): MediaType {
        return if (info.formats.isEmpty()) MediaType.IMAGE else MediaType.VIDEO
    }
}

class TedProvider : BaseMediaProvider() {
    override val id = "ted"
    override val name = "TED"
    override val iconName = "school"
    override val supportedMediaTypes = setOf(MediaType.VIDEO, MediaType.AUDIO, MediaType.SUBTITLE, MediaType.PLAYLIST)
    override val supportedDomains = listOf("ted.com")
    override val capabilities = ProviderCapabilities(
        video = true, audio = true, playlist = true, subtitles = true, clip = true,
        separateVideoAudioStreams = true, multipleQualities = true, multipleFormats = true
    )
}

class TwitchProvider : BaseMediaProvider() {
    override val id = "twitch"
    override val name = "Twitch"
    override val iconName = "videogame_asset"
    override val supportedMediaTypes = setOf(MediaType.CLIP, MediaType.VIDEO)
    override val supportedDomains = listOf("twitch.tv", "clips.twitch.tv")
    override val capabilities = ProviderCapabilities(
        video = true, audio = true, clip = true,
        separateVideoAudioStreams = true, multipleQualities = true, multipleFormats = true
    )
}

class GenericProvider : BaseMediaProvider() {
    override val id = "generic"
    override val name = "Universal Web (yt-dlp)"
    override val iconName = "public"
    override val supportedMediaTypes = setOf(MediaType.VIDEO, MediaType.AUDIO, MediaType.PLAYLIST, MediaType.SUBTITLE)
    override val supportedDomains = emptyList<String>()
    override val capabilities = ProviderCapabilities(
        video = true, audio = true, playlist = true, subtitles = true, clip = true,
        separateVideoAudioStreams = true, multipleQualities = true, multipleFormats = true
    )

    override fun canHandle(url: String): Boolean {
        // Universal fallback for any valid http/https URL
        return url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
    }
}
