package org.videopocket.probe.core

import org.junit.Assert.*
import org.junit.Test

class MediaIdentityTest {
    private val saved = PocketLibraryItem("library-id", "Repeated title", "content://media/1", false,
        duration = "00:10", resolution = "720p", format = "MP4", fileSizeBytes = 10,
        sourceMediaId = "provider-id", sourceUrl = "https://example.com/one", platform = "Example")

    @Test fun titlesDoNotCreateFalseDuplicatesAndRenamesDoNotHideTheSource() {
        assertFalse(MediaIdentity.sameSource(saved, "https://example.com/two", "different-id", "Example"))
        assertTrue(MediaIdentity.sameSource(saved.copy(title = "Renamed"), "https://example.com/one", null, null))
    }
    @Test fun sourceIdsAreScopedToTheirProviderAndUnknownIdentityDoesNotMatch() {
        assertTrue(MediaIdentity.sameSource(saved, "https://example.com/canonical", "provider-id", "example"))
        assertFalse(MediaIdentity.sameSource(saved, "https://other.example.com/one", "provider-id", "Other"))
        assertFalse(MediaIdentity.sameSource(saved, null, null, null))
    }
}
