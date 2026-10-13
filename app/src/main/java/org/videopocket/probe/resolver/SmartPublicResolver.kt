package org.videopocket.probe.resolver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.videopocket.probe.core.*
import org.videopocket.probe.engine.ProbeEngine
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.regex.Pattern

/**
 * SmartPublicResolver
 *
 * Maximizes successful downloads of legitimately PUBLIC media without
 * requiring the user to sign in whenever the source makes that media
 * publicly accessible.
 *
 * Core Ethical & Compliance Boundaries:
 * - Does NOT bypass authentication, DRM, paywalls, or private-account controls.
 * - NEVER obtains or manufactures account cookies, session tokens,
 *   authentication headers, or private API credentials.
 * - 100% local-first and introduces no paid APIs.
 */
object SmartPublicResolver {

    private val SHORT_DOMAINS = setOf(
        "vm.tiktok.com", "vt.tiktok.com",
        "t.co",
        "bit.ly", "tinyurl.com", "is.gd", "ow.ly", "buff.ly",
        "fb.watch",
        "pin.it",
        "dai.ly",
        "redd.it",
        "b23.tv",
        "instagr.am",
        "youtu.be"
    )

    fun isShortUrl(url: String): Boolean {
        return try {
            val uri = URI(url.trim())
            val host = uri.host?.lowercase().orEmpty()
            SHORT_DOMAINS.any { host == it || host.endsWith(".$it") } ||
                    (host.contains("threads.net") && uri.path?.contains("/t/") == true)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Safely normalizes and expands short URLs to their canonical destination.
     * Enforces strict redirect limits (up to maxRedirects) and NEVER passes cookies or auth tokens.
     */
    suspend fun expandCanonicalUrl(url: String, maxRedirects: Int = 5): String = expandShortUrl(url, maxRedirects)

    suspend fun expandShortUrl(url: String, maxRedirects: Int = 5): String = withContext(Dispatchers.IO) {
        var currentUrl = url.trim()
        var redirects = 0

        while (redirects < maxRedirects) {
            var conn: HttpURLConnection? = null
            try {
                val parsedUri = URI(currentUrl)
                if (parsedUri.scheme !in setOf("http", "https")) break

                val parsedUrl = URL(currentUrl)
                conn = (parsedUrl.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "HEAD"
                    connectTimeout = 7000
                    readTimeout = 7000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                    setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                }

                var code = conn.responseCode
                if (code == HttpURLConnection.HTTP_BAD_METHOD) {
                    conn.disconnect()
                    conn = (parsedUrl.openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = false
                        requestMethod = "GET"
                        connectTimeout = 7000
                        readTimeout = 7000
                        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                    }
                    code = conn.responseCode
                }

                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                    if (location.isNullOrBlank()) break
                    val nextUri = parsedUri.resolve(location)
                    currentUrl = nextUri.toString()
                    redirects++
                } else {
                    break
                }
            } catch (_: Exception) {
                break
            } finally {
                try { conn?.disconnect() } catch (_: Exception) {}
            }
        }
        currentUrl
    }

    sealed class PublicPageInspection {
        data class Accessible(val html: String, val finalUrl: String) : PublicPageInspection()
        object NotFound : PublicPageInspection()
        object RateLimited : PublicPageInspection()
        object AntiBotChallenge : PublicPageInspection()
        object GeoRestricted : PublicPageInspection()
        object DrmProtected : PublicPageInspection()
        data class AccessDenied(val reason: String? = null) : PublicPageInspection()
        data class LoginRequired(val isTrulyPrivate: Boolean, val reason: String? = null) : PublicPageInspection()
        data class Error(val message: String) : PublicPageInspection()
    }

    /**
     * Determines whether the original page is publicly accessible without authentication.
     * Uses zero credentials or session tokens.
     */
    suspend fun inspectPublicPage(url: String): PublicPageInspection = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val parsedUrl = URL(url.trim())
            conn = (parsedUrl.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                requestMethod = "GET"
                connectTimeout = 9000
                readTimeout = 9000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,video/*,*/*;q=0.8")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9,ar;q=0.8")
            }

            val code = conn.responseCode
            val finalUrl = conn.url.toString()

            // 1. Check HTTP response codes
            when (code) {
                404, 410 -> return@withContext PublicPageInspection.NotFound
                429 -> return@withContext PublicPageInspection.RateLimited
                401 -> return@withContext PublicPageInspection.LoginRequired(isTrulyPrivate = false, reason = "HTTP 401 Unauthorized")
                403 -> {
                    val errorBody = try {
                        conn.errorStream?.bufferedReader()?.use { it.readText().take(4096).lowercase() }.orEmpty()
                    } catch (_: Exception) { "" }

                    return@withContext when {
                        "captcha" in errorBody || "challenge" in errorBody || "cloudflare" in errorBody ||
                        "cf-ray" in errorBody || "robot" in errorBody || "just a moment" in errorBody ||
                        "verify you are human" in errorBody || "ddos-guard" in errorBody ->
                            PublicPageInspection.AntiBotChallenge
                        "rate limit" in errorBody || "too many requests" in errorBody ->
                            PublicPageInspection.RateLimited
                        "geo" in errorBody || "country" in errorBody || "region" in errorBody || "not available in your" in errorBody ->
                            PublicPageInspection.GeoRestricted
                        "drm" in errorBody || "widevine" in errorBody ->
                            PublicPageInspection.DrmProtected
                        "login" in errorBody || "sign in" in errorBody || "log in" in errorBody || "auth_required" in errorBody || "authentication required" in errorBody ->
                            PublicPageInspection.LoginRequired(isTrulyPrivate = false, reason = "HTTP 403 Auth Required")
                        else ->
                            PublicPageInspection.AccessDenied(reason = "HTTP 403 Forbidden (Non-auth)")
                    }
                }
            }

            // 2. Read public HTML body (up to 512 KB to avoid excessive memory usage)
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val charBuffer = CharArray(8192)
            val htmlBuilder = StringBuilder()
            var totalRead = 0
            val maxBytes = 512 * 1024

            while (totalRead < maxBytes) {
                val read = reader.read(charBuffer)
                if (read == -1) break
                htmlBuilder.append(charBuffer, 0, read)
                totalRead += read
            }
            val html = htmlBuilder.toString()
            val lowerHtml = html.lowercase()

            // Check if page returned an anti-bot challenge
            val isBotChallenge = lowerHtml.contains("cf-ray") || lowerHtml.contains("just a moment...") ||
                    lowerHtml.contains("attention required! | cloudflare") ||
                    lowerHtml.contains("verify you are human") || lowerHtml.contains("confirm you're not a bot")
            if (isBotChallenge) {
                return@withContext PublicPageInspection.AntiBotChallenge
            }

            // Check if page redirected to a dedicated login checkpoint or explicitly states private account
            val finalLower = finalUrl.lowercase()
            val isLoginPage = finalLower.contains("/login") || finalLower.contains("/checkpoint") ||
                    finalLower.contains("/accounts/login") || finalLower.contains("/auth")

            val indicatesPrivate = lowerHtml.contains("this account is private") ||
                    lowerHtml.contains("this video is private") ||
                    lowerHtml.contains("only approved followers") ||
                    lowerHtml.contains("log in to instagram") ||
                    lowerHtml.contains("log in to tiktok") ||
                    lowerHtml.contains("<title>login • instagram</title>") ||
                    (lowerHtml.contains("login") && lowerHtml.contains("password") && lowerHtml.contains("<form"))

            if (isLoginPage || indicatesPrivate) {
                return@withContext PublicPageInspection.LoginRequired(isTrulyPrivate = true)
            }

            PublicPageInspection.Accessible(html = html, finalUrl = finalUrl)
        } catch (e: Exception) {
            PublicPageInspection.Error(e.message ?: e.javaClass.simpleName)
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    data class PublicMediaDeclarations(
        val videoUrl: String? = null,
        val audioUrl: String? = null,
        val manifestUrl: String? = null,
        val title: String? = null,
        val author: String? = null,
        val thumbnail: String? = null,
        val duration: Double? = null,
        val isManifest: Boolean = manifestUrl != null
    ) {
        val hasMedia: Boolean get() = videoUrl != null || audioUrl != null || manifestUrl != null
        val bestStreamUrl: String? get() = manifestUrl ?: videoUrl ?: audioUrl
    }

    /**
     * Detects standard public media declarations from standard web standards:
     * - Open Graph media metadata (og:video, og:video:url, og:audio, etc.)
     * - HTML5 video/audio elements and sources
     * - Publicly exposed media manifests (.m3u8 / .mpd)
     * - Standard page metadata (title, thumbnails, duration)
     */
    fun extractDeclarationsFromHtml(html: String, pageUrl: String): PublicMediaDeclarations? {
        if (html.isBlank()) return null

        var videoUrl: String? = null
        var audioUrl: String? = null
        var manifestUrl: String? = null
        var title: String? = null
        var author: String? = null
        var thumbnail: String? = null
        var duration: Double? = null

        fun cleanUrl(raw: String): String {
            val unescaped = raw.replace("&amp;", "&").replace("&quot;", "\"").trim()
            return try {
                URI(pageUrl).resolve(unescaped).toString()
            } catch (_: Exception) {
                unescaped
            }
        }

        // 1. Open Graph video
        val ogVideoPattern = Pattern.compile("<meta\\s+[^>]*property=[\"'](?:og:video|og:video:url|og:video:secure_url)[\"'][^>]*content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        val ogVideoReversePattern = Pattern.compile("<meta\\s+[^>]*content=[\"']([^\"']+)[\"'][^>]*property=[\"'](?:og:video|og:video:url|og:video:secure_url)[\"']", Pattern.CASE_INSENSITIVE)

        var matcher = ogVideoPattern.matcher(html)
        if (matcher.find()) {
            videoUrl = cleanUrl(matcher.group(1).orEmpty())
        } else {
            matcher = ogVideoReversePattern.matcher(html)
            if (matcher.find()) {
                videoUrl = cleanUrl(matcher.group(1).orEmpty())
            }
        }

        // Twitter player stream
        if (videoUrl == null) {
            val twVideoPattern = Pattern.compile("<meta\\s+[^>]*name=[\"']twitter:player:stream[\"'][^>]*content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
            matcher = twVideoPattern.matcher(html)
            if (matcher.find()) {
                videoUrl = cleanUrl(matcher.group(1).orEmpty())
            } else {
                val twVideoReversePattern = Pattern.compile("<meta\\s+[^>]*content=[\"']([^\"']+)[\"'][^>]*name=[\"']twitter:player:stream[\"']", Pattern.CASE_INSENSITIVE)
                matcher = twVideoReversePattern.matcher(html)
                if (matcher.find()) {
                    videoUrl = cleanUrl(matcher.group(1).orEmpty())
                }
            }
        }

        // 2. Open Graph audio
        val ogAudioPattern = Pattern.compile("<meta\\s+[^>]*property=[\"']og:audio(?:[:\\w]+)?[\"'][^>]*content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        val ogAudioReversePattern = Pattern.compile("<meta\\s+[^>]*content=[\"']([^\"']+)[\"'][^>]*property=[\"']og:audio(?:[:\\w]+)?[\"']", Pattern.CASE_INSENSITIVE)
        matcher = ogAudioPattern.matcher(html)
        if (matcher.find()) {
            audioUrl = cleanUrl(matcher.group(1).orEmpty())
        } else {
            matcher = ogAudioReversePattern.matcher(html)
            if (matcher.find()) {
                audioUrl = cleanUrl(matcher.group(1).orEmpty())
            }
        }

        // 3. HTML5 video tag: <video src="..."> or <source src="..." type="video/...">
        if (videoUrl == null) {
            val videoTagPattern = Pattern.compile("<video\\b[^>]*\\bsrc=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
            matcher = videoTagPattern.matcher(html)
            if (matcher.find()) {
                videoUrl = cleanUrl(matcher.group(1).orEmpty())
            } else {
                val sourceTagPattern = Pattern.compile("<source\\b[^>]*\\bsrc=[\"']([^\"']+)[\"'][^>]*\\btype=[\"']video/[^\"']+[\"']", Pattern.CASE_INSENSITIVE)
                matcher = sourceTagPattern.matcher(html)
                if (matcher.find()) {
                    videoUrl = cleanUrl(matcher.group(1).orEmpty())
                }
            }
        }

        // 4. HTML5 audio tag
        if (audioUrl == null) {
            val audioTagPattern = Pattern.compile("<audio\\b[^>]*\\bsrc=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
            matcher = audioTagPattern.matcher(html)
            if (matcher.find()) {
                audioUrl = cleanUrl(matcher.group(1).orEmpty())
            } else {
                val sourceAudioPattern = Pattern.compile("<source\\b[^>]*\\bsrc=[\"']([^\"']+)[\"'][^>]*\\btype=[\"']audio/[^\"']+[\"']", Pattern.CASE_INSENSITIVE)
                matcher = sourceAudioPattern.matcher(html)
                if (matcher.find()) {
                    audioUrl = cleanUrl(matcher.group(1).orEmpty())
                }
            }
        }

        // 5. Publicly exposed manifests (.m3u8 / .mpd)
        val manifestPattern = Pattern.compile("https?://[^\"'\\s<>]+\\.(?:m3u8|mpd)(?:\\?[^\"'\\s<>]*)?", Pattern.CASE_INSENSITIVE)
        matcher = manifestPattern.matcher(html)
        if (matcher.find()) {
            manifestUrl = cleanUrl(matcher.group(0).orEmpty())
        }

        // 6. JSON-LD Structured Data (pure regex matching for standard JVM safety)
        val jsonLdPattern = Pattern.compile("<script\\s+[^>]*type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
        matcher = jsonLdPattern.matcher(html)
        while (matcher.find()) {
            val jsonContent = matcher.group(1).orEmpty().trim()
            val contentUrlPattern = Pattern.compile("[\"'](?:contentUrl|embedUrl)[\"']\\s*:\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
            val contentMatcher = contentUrlPattern.matcher(jsonContent)
            if (contentMatcher.find()) {
                val foundUrl = cleanUrl(contentMatcher.group(1).orEmpty())
                if (foundUrl.contains(".m3u8") || foundUrl.contains(".mpd")) {
                    if (manifestUrl == null) manifestUrl = foundUrl
                } else if (videoUrl == null) {
                    videoUrl = foundUrl
                }
            }
            if (title == null) {
                val namePattern = Pattern.compile("[\"'](?:name|headline)[\"']\\s*:\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
                val nameMatcher = namePattern.matcher(jsonContent)
                if (nameMatcher.find()) {
                    title = nameMatcher.group(1).orEmpty().replace("&amp;", "&").trim()
                }
            }
            if (thumbnail == null) {
                val thumbPattern = Pattern.compile("[\"']thumbnailUrl[\"']\\s*:\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
                val thumbMatcher = thumbPattern.matcher(jsonContent)
                if (thumbMatcher.find()) {
                    thumbnail = cleanUrl(thumbMatcher.group(1).orEmpty())
                }
            }
        }

        // 7. Page Title
        val ogTitlePattern = Pattern.compile("<meta\\s+[^>]*property=[\"']og:title[\"'][^>]*content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        val ogTitleReversePattern = Pattern.compile("<meta\\s+[^>]*content=[\"']([^\"']+)[\"'][^>]*property=[\"']og:title[\"']", Pattern.CASE_INSENSITIVE)
        matcher = ogTitlePattern.matcher(html)
        if (matcher.find()) {
            title = matcher.group(1).orEmpty().replace("&amp;", "&").trim()
        } else {
            matcher = ogTitleReversePattern.matcher(html)
            if (matcher.find()) {
                title = matcher.group(1).orEmpty().replace("&amp;", "&").trim()
            } else {
                val titleTagPattern = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
                matcher = titleTagPattern.matcher(html)
                if (matcher.find()) {
                    title = matcher.group(1).orEmpty().replace("&amp;", "&").trim()
                }
            }
        }

        // 8. Thumbnail
        val ogImagePattern = Pattern.compile("<meta\\s+[^>]*property=[\"']og:image[\"'][^>]*content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        val ogImageReversePattern = Pattern.compile("<meta\\s+[^>]*content=[\"']([^\"']+)[\"'][^>]*property=[\"']og:image[\"']", Pattern.CASE_INSENSITIVE)
        matcher = ogImagePattern.matcher(html)
        if (matcher.find()) {
            thumbnail = cleanUrl(matcher.group(1).orEmpty())
        } else {
            matcher = ogImageReversePattern.matcher(html)
            if (matcher.find()) {
                thumbnail = cleanUrl(matcher.group(1).orEmpty())
            }
        }

        // 9. Duration
        val durationPattern = Pattern.compile("<meta\\s+[^>]*property=[\"'](?:video:duration|og:video:duration)[\"'][^>]*content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        matcher = durationPattern.matcher(html)
        if (matcher.find()) {
            duration = matcher.group(1).orEmpty().toDoubleOrNull()
        }

        val decl = PublicMediaDeclarations(
            videoUrl = videoUrl,
            audioUrl = audioUrl,
            manifestUrl = manifestUrl,
            title = title,
            author = author,
            thumbnail = thumbnail,
            duration = duration
        )

        return if (decl.hasMedia || !title.isNullOrBlank()) decl else null
    }

    /**
     * Executes the comprehensive Smart Public Resolution pipeline:
     * 1. Safely normalizes and expands short URLs to canonical destination.
     * 2. Retries through the existing dedicated provider using the canonical public URL.
     * 3. Determines whether the page is publicly accessible without authentication.
     * 4. Attempts existing Universal/Generic resolver for standard publicly exposed media.
     * 5. Detects standard public media declarations (HTML5, Open Graph, Media Manifests).
     * 6. Classifies final outcomes accurately without marking entire providers broken.
     */
    suspend fun resolvePublicFallback(
        originalUrl: String,
        initialProvider: MediaProvider,
        engine: ProbeEngine
    ): MediaResolverResult {
        // Step 1: Expand short URLs safely & normalize
        val expandedUrl = expandShortUrl(originalUrl)
        val canonicalUrl = initialProvider.normalizeUrl(expandedUrl)

        // Step 2: Retry with dedicated provider if the URL was transformed/canonicalized
        if (canonicalUrl != originalUrl) {
            val canonicalProvider = ProviderRegistry.findProvider(canonicalUrl)
            if (FeatureFlagsManager.isProviderEnabled(canonicalProvider.id)) {
                val retryResult = try {
                    canonicalProvider.resolve(canonicalUrl, engine)
                } catch (_: Exception) {
                    null
                }
                if (retryResult is MediaResolverResult.Success) {
                    return MediaResolverResult.Success(
                        retryResult.metadata.copy(
                            resolutionStrategy = ResolutionStrategy.CANONICAL_EXPANSION,
                            classification = ResolutionClassification.PUBLIC_RESOLVED,
                            resolvedViaSmartPublicResolution = true
                        )
                    )
                }
            }
        }

        // Step 3: Determine whether the page is publicly accessible without authentication
        val inspection = inspectPublicPage(canonicalUrl)

        when (inspection) {
            is PublicPageInspection.NotFound -> {
                return MediaResolverResult.Failure(
                    ResolverError(
                        type = ResolverErrorType.PUBLIC_MEDIA_UNAVAILABLE,
                        detail = "The media item was not found or was removed from the public page.",
                        classification = ResolutionClassification.PUBLIC_MEDIA_UNAVAILABLE
                    )
                )
            }
            is PublicPageInspection.RateLimited -> {
                return MediaResolverResult.Failure(
                    ResolverError(
                        type = ResolverErrorType.RATE_LIMITED,
                        detail = "Source platform request rate limit exceeded.",
                        classification = ResolutionClassification.RATE_LIMITED
                    )
                )
            }
            is PublicPageInspection.GeoRestricted -> {
                return MediaResolverResult.Failure(
                    ResolverError(
                        type = ResolverErrorType.GEO_RESTRICTED,
                        detail = "This media is geo-restricted in your region.",
                        classification = ResolutionClassification.GEO_RESTRICTED
                    )
                )
            }
            is PublicPageInspection.DrmProtected -> {
                return MediaResolverResult.Failure(
                    ResolverError(
                        type = ResolverErrorType.DRM_PROTECTED,
                        detail = "This media is protected by Digital Rights Management (DRM).",
                        classification = ResolutionClassification.DRM_PROTECTED
                    )
                )
            }
            is PublicPageInspection.AntiBotChallenge -> {
                return MediaResolverResult.Failure(
                    ResolverError(
                        type = ResolverErrorType.ANTI_BOT_CHALLENGE,
                        detail = "Source platform anti-bot challenge detected.",
                        classification = ResolutionClassification.RATE_LIMITED
                    )
                )
            }
            is PublicPageInspection.AccessDenied -> {
                return MediaResolverResult.Failure(
                    ResolverError(
                        type = ResolverErrorType.PROVIDER_TEMPORARILY_UNAVAILABLE,
                        detail = inspection.reason ?: "HTTP 403 Forbidden without authentication requirement.",
                        classification = ResolutionClassification.TEMPORARILY_UNAVAILABLE
                    )
                )
            }
            is PublicPageInspection.LoginRequired -> {
                return MediaResolverResult.Failure(
                    ResolverError(
                        type = ResolverErrorType.PRIVATE_MEDIA,
                        detail = "This content is private or requires authentication to view.",
                        classification = ResolutionClassification.PRIVATE_MEDIA
                    )
                )
            }
            is PublicPageInspection.Error -> {
                // If public inspection hit a network error, return NETWORK_ERROR
                return MediaResolverResult.Failure(
                    ResolverError(
                        type = ResolverErrorType.NETWORK_ERROR,
                        detail = inspection.message,
                        classification = ResolutionClassification.TEMPORARILY_UNAVAILABLE
                    )
                )
            }
            is PublicPageInspection.Accessible -> {
                val publicHtml = inspection.html
                val targetUrl = inspection.finalUrl

                // Step 4: Page is legitimately public! Try the existing Universal/Generic resolver
                if (FeatureFlagsManager.isProviderEnabled("generic")) {
                    val genericProvider = ProviderRegistry.getGenericProvider()
                    val genericResult = try {
                        genericProvider.resolve(targetUrl, engine)
                    } catch (_: Exception) {
                        null
                    }
                    if (genericResult is MediaResolverResult.Success) {
                        return MediaResolverResult.Success(
                            genericResult.metadata.copy(
                                resolutionStrategy = ResolutionStrategy.GENERIC_FALLBACK,
                                classification = ResolutionClassification.PUBLIC_RESOLVED,
                                resolvedViaSmartPublicResolution = true
                            )
                        )
                    }
                }

                // Step 5: Detect standard public media declarations
                val declarations = extractDeclarationsFromHtml(publicHtml, targetUrl)
                if (declarations != null && declarations.hasMedia) {
                    val streamUrl = declarations.bestStreamUrl!!
                    val isManifest = declarations.isManifest

                    // Try inspecting the direct stream link with engine for rich format tiers
                    val directInfo = try {
                        engine.inspect(streamUrl)
                    } catch (_: Exception) {
                        null
                    }

                    val title = declarations.title ?: directInfo?.title ?: (initialProvider.name + " Public Video")
                    val thumbnail = declarations.thumbnail ?: directInfo?.thumbnailUrl
                    val duration = declarations.duration ?: directInfo?.duration

                    val variants = if (directInfo != null && directInfo.formats.isNotEmpty()) {
                        directInfo.formats.map { f ->
                            MediaVariant(
                                id = f.id,
                                quality = if (f.height > 0) "${f.height}p" else f.id,
                                height = f.height,
                                format = f.extension.ifBlank { "mp4" },
                                videoUrl = f.url ?: streamUrl,
                                isAudioOnly = f.audio && !f.video
                            )
                        }
                    } else {
                        listOf(
                            MediaVariant(
                                id = "public_best",
                                quality = "Default (Public Stream)",
                                height = 720,
                                format = if (isManifest) "m3u8" else "mp4",
                                videoUrl = streamUrl,
                                isAudioOnly = declarations.audioUrl != null && declarations.videoUrl == null
                            ),
                            MediaVariant(
                                id = "public_audio",
                                quality = "Original Audio",
                                height = 0,
                                format = if (isManifest) "m3u8" else "m4a",
                                videoUrl = declarations.audioUrl ?: streamUrl,
                                isAudioOnly = true
                            )
                        )
                    }

                    val strategy = if (isManifest) ResolutionStrategy.PUBLIC_MEDIA_MANIFEST else ResolutionStrategy.PUBLIC_HTML_OPENGRAPH
                    val rawInfo = directInfo ?: MediaInfo(
                        title = title,
                        duration = duration,
                        formats = variants.map {
                            MediaFormat(
                                id = it.id,
                                extension = it.format,
                                height = it.height,
                                video = !it.isAudioOnly,
                                audio = true,
                                bytes = null,
                                url = it.videoUrl
                            )
                        },
                        thumbnailUrl = thumbnail,
                        streamUrl = streamUrl,
                        id = directInfo?.id ?: targetUrl.hashCode().toString()
                    )

                    return MediaResolverResult.Success(
                        NormalizedMetadata(
                            id = targetUrl.hashCode().toString(),
                            canonicalUrl = targetUrl,
                            platform = initialProvider.name,
                            platformIcon = initialProvider.iconName,
                            title = title,
                            thumbnail = thumbnail,
                            duration = duration,
                            variants = variants,
                            rawInfo = rawInfo,
                            resolutionStrategy = strategy,
                            classification = ResolutionClassification.PUBLIC_RESOLVED,
                            resolvedViaSmartPublicResolution = true
                        )
                    )
                }

                // Step 6: The page is public, but no standard media stream declarations were present
                return MediaResolverResult.Failure(
                    ResolverError(
                        type = ResolverErrorType.EXTRACTOR_OUTDATED,
                        detail = "The web page is publicly accessible, but no standard media declarations were found. Extractor update may be needed.",
                        classification = ResolutionClassification.EXTRACTOR_OUTDATED
                    )
                )
            }
        }
    }
}
