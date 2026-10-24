package org.videopocket.probe.resolver

import org.videopocket.probe.core.MediaInfo
import org.videopocket.probe.engine.ProbeEngine

data class ProviderCapabilities(
    val video: Boolean = true,
    val audio: Boolean = true,
    val image: Boolean = false,
    val gallery: Boolean = false,
    val gif: Boolean = false,
    val shortVideo: Boolean = false,
    val reel: Boolean = false,
    val story: Boolean = false,
    val playlist: Boolean = false,
    val subtitles: Boolean = false,
    val clip: Boolean = true,
    val separateVideoAudioStreams: Boolean = false,
    val multipleQualities: Boolean = true,
    val multipleFormats: Boolean = true
)

interface MediaProvider {
    val id: String
    val name: String
    val iconName: String
    val supportedMediaTypes: Set<MediaType>
    val supportedDomains: List<String>
    val capabilities: ProviderCapabilities

    fun canHandle(url: String): Boolean
    fun normalizeUrl(url: String): String = url.trim()
    suspend fun resolve(url: String, engine: ProbeEngine): MediaResolverResult
}
