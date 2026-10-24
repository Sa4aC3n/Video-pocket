package org.videopocket.probe.core

/** Wrapper 0.18.1 still bundles 4 KB-aligned WebP dependencies inside its FFmpeg archive. */
object NativeRuntimePolicy {
    fun supportsPageSize(bytes: Long): Boolean = bytes == 4096L
    const val PAGE_SIZE_MESSAGE = "This bundled media engine requires 4 KB memory pages. A compatible native runtime is needed on this device."
}
