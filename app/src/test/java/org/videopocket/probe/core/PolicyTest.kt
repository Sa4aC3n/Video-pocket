package org.videopocket.probe.core

import org.junit.Assert.*
import org.junit.Test

class PolicyTest {
    @Test fun rejectsNonWebSchemesCredentialsAndCommandText() {
        listOf("file:///sdcard/a.mp4", "javascript:alert(1)", "--exec=bad", "https://user:pass@example.org/v", "https://example.org/v\n--exec=bad", "https:///missing-host", "https://example.org/a b").forEach { input ->
            assertThrows(ProbeFailure::class.java) { LinkPolicy.validate(input) }
        }
    }
    @Test fun signedQueryIsPreservedWithoutShellInterpretation() {
        val url = "https://example.org/video.mp4?token=a%2Bb&expires=123"
        assertEquals(url, LinkPolicy.validate("  $url  "))
    }
    private val info = MediaInfo("test", 5.0, listOf(
        MediaFormat("a", "m4a", 0, false, true, 100),
        MediaFormat("v", "mp4", 1080, true, false, 1000),
        MediaFormat("low", "mp4", 360, true, true, 500),
        MediaFormat("4k", "webm", 2160, true, false, 2000)
    ))
    @Test fun defaultsTo1080AndExposes4k() {
        assertEquals(1080, info.defaultHeight)
        assertEquals(listOf(2160,1080,360), info.heights)
    }
    @Test fun mergeRequiresActualSeparateTracks() {
        assertEquals("v+a", FormatPolicy.select(info, ProbeAction.MERGE, 1080))
        assertThrows(ProbeFailure::class.java) { FormatPolicy.select(info, ProbeAction.MERGE, 360) }
    }
    @Test fun missingResolutionDoesNotSilentlyDowngrade() {
        assertThrows(ProbeFailure::class.java) { FormatPolicy.select(info, ProbeAction.VIDEO, 720) }
    }
    @Test fun videoAndAudioSelections() {
        assertEquals("low", FormatPolicy.select(info, ProbeAction.VIDEO, 360))
        assertEquals("v+a", FormatPolicy.select(info, ProbeAction.VIDEO, 1080))
        assertEquals("a", FormatPolicy.select(info, ProbeAction.MP3, 1080))
    }
    @Test fun rejectsUntrustedFormatExpressions() {
        val bad = info.copy(formats = listOf(MediaFormat("best/evil", "mp4", 360, true, true, 1)))
        assertThrows(ProbeFailure::class.java) { FormatPolicy.select(bad, ProbeAction.VIDEO, 360) }
    }
    @Test fun playlistSelections() {
        val playlistInfo = MediaInfo("Playlist", null, emptyList(), isPlaylist = true, playlistCount = 5)
        assertEquals("bestvideo[height<=720]+bestaudio/best[height<=720]/best", FormatPolicy.select(playlistInfo, ProbeAction.PLAYLIST_VIDEO, 720))
        assertEquals("bestaudio/best", FormatPolicy.select(playlistInfo, ProbeAction.PLAYLIST_MP3, 720))
    }

    @Test
    fun extractsUrlFromSoundCloudShareText() {
        val soundCloudShare = "Listen to Amr Diab - Tamally Maak by Amr Diab on #SoundCloud https://on.soundcloud.com/xyz123"
        assertEquals("https://on.soundcloud.com/xyz123", LinkPolicy.extractUrl(soundCloudShare))

        val soundCloudFull = "Listen to Track by Artist on #SoundCloud\nhttps://soundcloud.com/artist/track?si=abcdef123456"
        assertEquals("https://soundcloud.com/artist/track?si=abcdef123456", LinkPolicy.extractUrl(soundCloudFull))

        val trailingText = "Check this track: https://soundcloud.com/user/track #music #trending"
        assertEquals("https://soundcloud.com/user/track", LinkPolicy.extractUrl(trailingText))

        val withPunctuation = "Listen here (https://on.soundcloud.com/abc)!"
        assertEquals("https://on.soundcloud.com/abc", LinkPolicy.extractUrl(withPunctuation))
    }

    @Test
    fun extractsMagnetFromShareText() {
        val magnetShare = "Download this torrent: magnet:?xt=urn:btih:0123456789ABCDEF0123456789ABCDEF01234567&dn=Test"
        assertEquals("magnet:?xt=urn:btih:0123456789ABCDEF0123456789ABCDEF01234567&dn=Test", LinkPolicy.extractUrl(magnetShare))
    }

    @Test
    fun pureUrlRemainsUnchanged() {
        val url = "https://soundcloud.com/artist/song"
        assertEquals(url, LinkPolicy.extractUrl(url))
        assertEquals("", LinkPolicy.extractUrl(null))
        assertEquals("", LinkPolicy.extractUrl("   "))
    }
}
