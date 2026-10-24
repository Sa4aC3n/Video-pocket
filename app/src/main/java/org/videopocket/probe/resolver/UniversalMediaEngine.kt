package org.videopocket.probe.resolver

import android.content.Context
import org.videopocket.probe.core.MediaInfo
import org.videopocket.probe.core.PocketLibraryItem
import org.videopocket.probe.download.DownloadTask
import org.videopocket.probe.engine.ProbeEngine

/**
 * UniversalMediaEngine
 *
 * The unified facade for media extraction, resolution, and processing in Video Pocket.
 *
 * Local-First Architectural Hierarchy:
 * Video Pocket (UI / ViewModel)
 *       ↓
 * UniversalMediaEngine
 *       ↓
 * ProviderRegistry
 *       ↓
 * Local Providers (BaseMediaProvider)
 *       ↓
 * Download Engine (ProbeEngine with embedded yt-dlp & FFmpeg)
 */
object UniversalMediaEngine {

    /**
     * Resolves a media URL using the local-first provider hierarchy.
     */
    suspend fun resolve(url: String, engine: ProbeEngine): MediaResolverResult {
        return ProviderRegistry.resolve(url, engine)
    }

    /**
     * Finds the matching provider for a given URL without executing network calls.
     */
    fun findProvider(url: String): MediaProvider {
        return ProviderRegistry.findProvider(url)
    }

    /**
     * Returns all registered providers in the engine.
     */
    fun getSupportedProviders(): List<MediaProvider> {
        return ProviderRegistry.getSupportedProviders()
    }

    /**
     * Inspects the health of a specific provider.
     */
    fun getHealth(providerId: String): ProviderHealth {
        return ProviderHealthManager.getHealth(providerId)
    }

    /**
     * Checks if a provider is enabled via feature flags.
     */
    fun isEnabled(providerId: String): Boolean {
        return FeatureFlagsManager.isProviderEnabled(providerId)
    }

    /**
     * Toggles provider availability dynamically.
     */
    fun setEnabled(providerId: String, enabled: Boolean) {
        FeatureFlagsManager.setProviderEnabled(providerId, enabled)
    }
}

/**
 * Resolution source category for modular resolution.
 */
enum class MediaResolverSource {
    LOCAL,
    HYBRID,
    REMOTE
}

/**
 * Common interface for media resolution strategies.
 */
interface MediaResolver {
    val source: MediaResolverSource
    suspend fun resolve(url: String): MediaResolverResult
}

/**
 * Local-First resolver implementation using embedded engines.
 */
class LocalResolver(private val engine: ProbeEngine) : MediaResolver {
    override val source: MediaResolverSource = MediaResolverSource.LOCAL

    override suspend fun resolve(url: String): MediaResolverResult {
        return ProviderRegistry.resolve(url, engine)
    }
}

/**
 * Hybrid resolver for future expansion (currently operates 100% local-first).
 */
class HybridResolver(private val localResolver: LocalResolver) : MediaResolver {
    override val source: MediaResolverSource = MediaResolverSource.HYBRID

    override suspend fun resolve(url: String): MediaResolverResult {
        return localResolver.resolve(url)
    }
}

/**
 * Reusable type alias conforming to unified architecture.
 */
typealias DownloadRequest = DownloadTask
