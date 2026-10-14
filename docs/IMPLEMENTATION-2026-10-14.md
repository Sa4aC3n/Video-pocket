# Video Pocket implementation evidence — 2026-10-14

Baseline: `74538de5770ca7ea5cb4c9e3b8cf29925d502e39`, verified on GitHub main.
All updates are incremental fast-forwards on main. Existing binary resources and history are preserved.

## Milestone log

| Commit | Change | Evidence |
| --- | --- | --- |
| `58fbde7a438b9c3244b9d60810ed285c8492f282` | Add Android CI | GitHub main and local tree synchronized. Initial run 38508436877 failed at SDK setup before Gradle; setup corrected in the next milestone. |

## Phase 4 changes in this milestone

- Queue gives each transfer its own engine/process and notification. Cancellation retains the concurrency slot until cleanup finishes. FIFO scheduling fills all configured slots; active processing counts toward the limit.
- Queue playlist audio uses the playlist audio action. Every output is indexed using its actual MediaStore name, bytes, MIME, duration and video dimensions. Unknown values remain unknown.
- AtomicFile protects the existing JSON library format. Read/write failures surface instead of overwriting unreadable data with an empty index. Batch indexing deduplicates by saved URI. Failed media deletion retains the entry.
- Active scratch directories are excluded from cleanup. Freed-byte reporting measures actual deletions. Recent cache entries are retained.
- Download/clip/playlist outputs are validated before publication; playlist publication rolls back newly created rows on failure. Cancellation is rejected once atomic publication has started.
- Remove invented clip progress milestones and inferred queue stages. Playlist total progress stays indeterminate because the backend reports individual items.
- Unknown resolution cannot satisfy a specific requested resolution. Real 8K formats remain visible. Reject non-finite and malformed clip timestamps; reject clips outside known duration.

## Verification and environment

- Local `bash gradlew testDebugUnitTest assembleDebug lintDebug --console=plain`: attempted, blocked before Gradle by `UnknownHostException: services.gradle.org`.
- Local JDK: OpenJDK 17.0.20. No local Android SDK/emulator/device discovered.
- GitHub Actions is available. Build/test/lint results will be recorded after fixes and reruns, not inferred from source review.
- Source files were retrieved through the authorized GitHub connection and validated against Git blob hashes. Two large binary resources cannot be downloaded through the connector; they remain unchanged in the remote tree. CI checks out the full repository.
- `git diff --check`: passed for this milestone.

## Remaining work

Phases 5–8 implementation, final Gradle/lint/regression results, device media tests, release signing and release readiness remain open. No production-readiness claim is made.
