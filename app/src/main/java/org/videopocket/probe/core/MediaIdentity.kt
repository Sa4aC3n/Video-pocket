package org.videopocket.probe.core

object MediaIdentity {
    fun sameSource(item: PocketLibraryItem, url: String?, id: String?, platform: String?): Boolean =
        !url.isNullOrBlank() && item.sourceUrl == url ||
            !id.isNullOrBlank() && !platform.isNullOrBlank() &&
            item.sourceMediaId == id && item.platform.equals(platform, ignoreCase = true)
}
