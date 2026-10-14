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

## Phases 5–6 implementation milestone

- Phase 4 commit: `96e488278349f0301ffec54f17a06f85cfcbf382`, verified on GitHub main and against the local Git tree.
- Added Media Studio with Android document picker and library editing entry point: compress, trim with explicit start/end, extract audio, convert.
- Existing bundled FFmpeg runs locally with argument arrays, no shell, no network input and no paid service. Initialization remains deferred until Studio is opened.
- Encoder availability is based on `-encoders`, muxer enumeration and successful sample encoding on the device. Capability results are process-local and cached. Adaptive software codec selection uses H.264 / HEVC; WebM uses VP9 + Opus when verified. No unsupported AVI/WMV options are exposed.
- Three presets preserve aspect ratio, even output dimensions, and never upscale. Optional compression size cap uses a bitrate budget with bounded second attempt and verifies final size. Larger-than-original compression output is not published.
- Actual FFmpeg output timestamps drive progress; import, validation and save phases are indeterminate. Cancellation kills the owned subprocess and removes only the new scratch output. Once atomic publication starts cancellation is disabled.
- Original URI is opened read-only. New output is validated for track type/duration, stored through pending MediaStore rows, and indexed. Index failure rolls back only the newly published output.
- Reject protected inputs and HDR conversions rather than silently altering HDR colors. Processing has a one-hour timeout, bounded threads, bounded diagnostics, and storage checks during copying/encoding.
- Added policy regression tests for capabilities, bitrate budget, trimming, dimensions and progress. Compilation/runtime results pending CI; these are implementation claims only.
- CI run 38508890686 failed before Gradle because `sdkmanager` was not on PATH. Android setup action is now restored with explicit supported packages (its obsolete default `tools` package is excluded).

## Phases 7–8 implementation milestone

- Media Studio commit: `980d9b09e9159424f8332c9571b270cfd1c15be4`, synchronized and verified.
- Adaptive navigation uses a rail on wide displays and a bottom bar on phones; content width is bounded for tablets. Arabic is the default for new installs while existing saved language choices are preserved. RTL/LTR and light/dark/system remain functional. Improved light-theme text contrast.
- Library and downloads use keyed lazy lists rather than composing every row. Library search covers titles, formats, platforms, and collections; filters/sorting and collection editing are connected to storage.
- Library I/O runs off the UI thread and changes are observed after queue or Studio processing. Failed library actions surface in the UI. Play/share resolves the output URI rather than a non-unique title. Unknown file sizes are displayed as unknown.
- Queue snapshots use AtomicFile in no-backup private storage. Interrupted tasks require explicit restart; no false byte-resume claim. A damaged queue is retained instead of overwritten. State changes are persisted through a conflated IO channel, not on every progress tick.
- Download format selections now reach the engine and request the chosen container. Unsupported codec/container combinations fail explicitly; Studio offers actual re-encoding options.
- Batch quality is resolved from the source instead of assuming 1080p. New share intents update Home correctly. Public-page redirects are bounded and validated, localhost/link-local loopback targets are rejected, and error-body reads are bounded.
- Limitation: tasks run while the app process lives; this milestone does not implement a persistent background foreground service. UI states that keeping the app open is required. Process-death recovery restarts downloads rather than resuming partial bytes.
