package org.videopocket.probe.resolver

import org.videopocket.probe.engine.ProbeEngine

object ProviderRegistry {
    private val genericProvider = GenericProvider()

    private val providers: List<MediaProvider> = listOf(
        TikTokProvider(),
        InstagramProvider(),
        FacebookProvider(),
        XProvider(),
        RedditProvider(),
        VimeoProvider(),
        DailymotionProvider(),
        BilibiliProvider(),
        SoundCloudProvider(),
        TumblrProvider(),
        SnapchatProvider(),
        PinterestProvider(),
        TedProvider(),
        TwitchProvider(),
        genericProvider
    )

    fun getSupportedProviders(): List<MediaProvider> = providers

    fun getGenericProvider(): GenericProvider = genericProvider

    fun findProvider(url: String): MediaProvider {
        val cleanUrl = url.trim()
        val specific = providers.filter { it.id != "generic" }.firstOrNull { it.canHandle(cleanUrl) }
        return specific ?: genericProvider
    }

    suspend fun resolve(url: String, engine: ProbeEngine): MediaResolverResult {
        val cleanUrl = url.trim()
        val provider = findProvider(cleanUrl)

        if (!FeatureFlagsManager.isProviderEnabled(provider.id)) {
            return MediaResolverResult.Failure(
                ResolverError(
                    ResolverErrorType.PROVIDER_DISABLED,
                    "Provider '${provider.name}' is currently disabled in Settings."
                )
            )
        }

        val attempts = mutableListOf<ResolutionAttempt>()
        val overallStart = System.currentTimeMillis()

        // -------------------------------------------------------------
        // Strategy 1: Dedicated Provider Extractor
        // -------------------------------------------------------------
        val s1Start = System.currentTimeMillis()
        val s1Result = try {
            provider.resolve(cleanUrl, engine)
        } catch (t: Throwable) {
            val msg = t.message.orEmpty().lowercase()
            val errType = when {
                "captcha" in msg || "challenge" in msg || "cf-ray" in msg || "robot" in msg -> ResolverErrorType.ANTI_BOT_CHALLENGE
                "rate limit" in msg || "429" in msg -> ResolverErrorType.RATE_LIMITED
                "geo" in msg || "region" in msg || "country" in msg -> ResolverErrorType.GEO_RESTRICTED
                "drm" in msg || "widevine" in msg -> ResolverErrorType.DRM_PROTECTED
                "404" in msg || "not found" in msg || "deleted" in msg -> ResolverErrorType.MEDIA_NOT_FOUND
                "private" in msg && "account" in msg -> ResolverErrorType.PRIVATE_MEDIA
                else -> ResolverErrorType.EXTRACTOR_OUTDATED
            }
            MediaResolverResult.Failure(
                ResolverError(errType, "Dedicated extractor error: ${t.message ?: t.javaClass.simpleName}")
            )
        }
        val s1Duration = System.currentTimeMillis() - s1Start

        when (s1Result) {
            is MediaResolverResult.Success -> {
                attempts.add(ResolutionAttempt(ResolutionStrategy.DEDICATED_EXTRACTOR, provider.id, success = true, durationMs = s1Duration))
                val trace = ResolutionTrace(attempts, ResolutionStrategy.DEDICATED_EXTRACTOR, ResolutionClassification.PUBLIC_RESOLVED)
                ProviderHealthManager.recordSuccess(
                    providerId = provider.id,
                    latencyMs = s1Duration,
                    strategy = s1Result.metadata.resolutionStrategy
                )
                return MediaResolverResult.Success(s1Result.metadata.copy(trace = trace))
            }
            is MediaResolverResult.Failure -> {
                attempts.add(ResolutionAttempt(ResolutionStrategy.DEDICATED_EXTRACTOR, provider.id, success = false, errorType = s1Result.error.type, durationMs = s1Duration))
            }
        }

        // -------------------------------------------------------------
        // Strategy 2: Canonical URL Expansion
        // -------------------------------------------------------------
        val s2Start = System.currentTimeMillis()
        val isShortOrRedirect = cleanUrl.contains("youtu.be") || cleanUrl.contains("vm.tiktok.com") ||
                cleanUrl.contains("vt.tiktok.com") || cleanUrl.contains("t.co") ||
                cleanUrl.contains("fb.watch") || cleanUrl.contains("pin.it") ||
                cleanUrl.contains("/share/") || cleanUrl.contains("/s/")

        var canonicalUrl = cleanUrl
        if (isShortOrRedirect) {
            val expanded = try {
                SmartPublicResolver.expandCanonicalUrl(cleanUrl)
            } catch (_: Exception) {
                cleanUrl
            }
            if (expanded != cleanUrl) {
                canonicalUrl = expanded
                val canonicalProvider = findProvider(canonicalUrl)
                val s2Result = try {
                    canonicalProvider.resolve(canonicalUrl, engine)
                } catch (t: Throwable) {
                    null
                }
                val s2Duration = System.currentTimeMillis() - s2Start
                if (s2Result is MediaResolverResult.Success) {
                    attempts.add(ResolutionAttempt(ResolutionStrategy.CANONICAL_EXPANSION, canonicalProvider.id, success = true, durationMs = s2Duration))
                    val trace = ResolutionTrace(attempts, ResolutionStrategy.CANONICAL_EXPANSION, ResolutionClassification.PUBLIC_RESOLVED)
                    ProviderHealthManager.recordFallbackSuccess(
                        providerId = provider.id,
                        latencyMs = System.currentTimeMillis() - overallStart,
                        strategy = ResolutionStrategy.CANONICAL_EXPANSION
                    )
                    return MediaResolverResult.Success(
                        s2Result.metadata.copy(
                            trace = trace,
                            canonicalUrl = canonicalUrl,
                            resolutionStrategy = ResolutionStrategy.CANONICAL_EXPANSION,
                            resolvedViaSmartPublicResolution = true
                        )
                    )
                } else if (s2Result is MediaResolverResult.Failure) {
                    attempts.add(ResolutionAttempt(ResolutionStrategy.CANONICAL_EXPANSION, canonicalProvider.id, success = false, errorType = s2Result.error.type, durationMs = s2Duration))
                }
            }
        }

        // -------------------------------------------------------------
        // Strategy 3: Safe Public Metadata Fallback (OpenGraph / HTML5 / Manifest)
        // -------------------------------------------------------------
        val s3Start = System.currentTimeMillis()
        val s3Result = try {
            SmartPublicResolver.resolvePublicFallback(canonicalUrl, provider, engine)
        } catch (_: Exception) {
            null
        }
        val s3Duration = System.currentTimeMillis() - s3Start

        if (s3Result is MediaResolverResult.Success) {
            attempts.add(ResolutionAttempt(s3Result.metadata.resolutionStrategy, provider.id, success = true, durationMs = s3Duration))
            val trace = ResolutionTrace(attempts, s3Result.metadata.resolutionStrategy, ResolutionClassification.PUBLIC_RESOLVED)
            ProviderHealthManager.recordFallbackSuccess(
                providerId = provider.id,
                latencyMs = System.currentTimeMillis() - overallStart,
                strategy = s3Result.metadata.resolutionStrategy
            )
            return MediaResolverResult.Success(s3Result.metadata.copy(trace = trace))
        } else if (s3Result is MediaResolverResult.Failure) {
            attempts.add(ResolutionAttempt(ResolutionStrategy.PUBLIC_HTML_OPENGRAPH, provider.id, success = false, errorType = s3Result.error.type, durationMs = s3Duration))
        }

        // -------------------------------------------------------------
        // Strategy 4: Generic Provider Fallback
        // -------------------------------------------------------------
        if (provider.id != "generic" && FeatureFlagsManager.isProviderEnabled("generic")) {
            val s4Start = System.currentTimeMillis()
            val s4Result = try {
                genericProvider.resolve(canonicalUrl, engine)
            } catch (t: Throwable) {
                MediaResolverResult.Failure(
                    ResolverError(ResolverErrorType.EXTRACTOR_OUTDATED, t.message)
                )
            }
            val s4Duration = System.currentTimeMillis() - s4Start

            if (s4Result is MediaResolverResult.Success) {
                attempts.add(ResolutionAttempt(ResolutionStrategy.GENERIC_FALLBACK, "generic", success = true, durationMs = s4Duration))
                val trace = ResolutionTrace(attempts, ResolutionStrategy.GENERIC_FALLBACK, ResolutionClassification.PUBLIC_RESOLVED)
                ProviderHealthManager.recordFallbackSuccess(
                    providerId = provider.id,
                    latencyMs = System.currentTimeMillis() - overallStart,
                    strategy = ResolutionStrategy.GENERIC_FALLBACK
                )
                return MediaResolverResult.Success(
                    s4Result.metadata.copy(
                        trace = trace,
                        resolutionStrategy = ResolutionStrategy.GENERIC_FALLBACK,
                        classification = ResolutionClassification.PUBLIC_RESOLVED,
                        resolvedViaSmartPublicResolution = true
                    )
                )
            } else if (s4Result is MediaResolverResult.Failure) {
                attempts.add(ResolutionAttempt(ResolutionStrategy.GENERIC_FALLBACK, "generic", success = false, errorType = s4Result.error.type, durationMs = s4Duration))
            }
        }

        // -------------------------------------------------------------
        // Final Evidence-Based Error Synthesis
        // -------------------------------------------------------------
        val allErrors = attempts.mapNotNull { it.errorType }
        val finalErrorType: ResolverErrorType = when {
            ResolverErrorType.ANTI_BOT_CHALLENGE in allErrors -> ResolverErrorType.ANTI_BOT_CHALLENGE
            ResolverErrorType.RATE_LIMITED in allErrors -> ResolverErrorType.RATE_LIMITED
            ResolverErrorType.GEO_RESTRICTED in allErrors -> ResolverErrorType.GEO_RESTRICTED
            ResolverErrorType.DRM_PROTECTED in allErrors -> ResolverErrorType.DRM_PROTECTED
            ResolverErrorType.MEDIA_NOT_FOUND in allErrors || ResolverErrorType.PUBLIC_MEDIA_UNAVAILABLE in allErrors -> ResolverErrorType.MEDIA_NOT_FOUND
            ResolverErrorType.PRIVATE_MEDIA in allErrors -> ResolverErrorType.PRIVATE_MEDIA
            ResolverErrorType.LOGIN_REQUIRED in allErrors -> ResolverErrorType.LOGIN_REQUIRED
            ResolverErrorType.NETWORK_ERROR in allErrors -> ResolverErrorType.NETWORK_ERROR
            ResolverErrorType.PARSER_FAILURE in allErrors -> ResolverErrorType.PARSER_FAILURE
            ResolverErrorType.EXTRACTOR_OUTDATED in allErrors -> ResolverErrorType.EXTRACTOR_OUTDATED
            else -> ResolverErrorType.PROVIDER_TEMPORARILY_UNAVAILABLE
        }

        val finalTrace = ResolutionTrace(attempts, null, finalErrorType.toClassification())
        ProviderHealthManager.recordFailure(provider.id, finalErrorType)

        return MediaResolverResult.Failure(
            ResolverError(
                type = finalErrorType,
                detail = "Unable to resolve media across all active strategies.",
                trace = finalTrace
            )
        )
    }
}
