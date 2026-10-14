# Video Pocket implementation evidence — 2026-10-14

Baseline: `74538de5770ca7ea5cb4c9e3b8cf29925d502e39`. Work continues exclusively on `main`, using expected-head fast-forwards. No force-push, reset, branch replacement or deletion of prior user work was used. Every milestone's GitHub head and local Git tree were checked after synchronization.

## Milestones

| Commit | Implemented change |
| --- | --- |
| `58fbde7a438b9c3244b9d60810ed285c8492f282` | Android test/build/lint CI |
| `96e488278349f0301ffec54f17a06f85cfcbf382` | Per-transfer engines, cancellation/publication boundaries, playlist metadata, atomic index, workspace protection |
| `980d9b09e9159424f8332c9571b270cfd1c15be4` | Smart compression, local trimming/audio extraction/conversion and Studio |
| `d05604bc9a881475877790a24f4fa2d10f38a366` | Persistent queue, responsive bilingual UI, searchable/filterable library, format routing |
| `4ed5ef1992dcf35ace98079f40764b07618a301c` | Persistence/corruption recovery unit tests and native media instrumentation |
| `b508d527cca3ddf46524a65a9dd8d126b7dfbb84` | Unified Home/download queue, remove guessed clip size/resolution/duration, actual runtime-version API, device-transfer rules, emulator disk fix |
| `99fcd2e5a58c9bbf4399d94d3e68c61641ce6fba` | Resource watchdogs, MIME/rollback fixes, native page-size gate, subtitle conversion and fetch hardening, version 0.2.0-rc1, release/UI/performance gates |

## Phase 4 — security, downloader, playlists and storage

- Each active task owns its engine/process and notification. Cancellation keeps the concurrency slot until cleanup. FIFO starts tasks up to the configured concurrency. Waiting tasks can pause; interrupted active tasks require explicit restart.
- Home, clips and batch actions use one queue. Queue snapshots preserve clip parameters and output URIs through AtomicFile. A corrupt queue is reported and retained, not overwritten or silently resumed.
- Playlist audio takes the playlist-audio path. Every completed URI is independently queried for its actual name, bytes, MIME, duration and dimensions, including rotation. Requested quality and aggregate playlist bytes are not copied to individual library entries.
- Native download/clip/playlist output is validated before publication. Pending MediaStore rows are finalized only after writing. Publication/index failure rolls back only newly created rows. Once publication begins, cancellation is rejected to avoid partial saved state.
- AtomicFile retains the existing JSON schema. Failed reads do not become empty libraries. Failed media deletion retains its index entry. Deduplication uses saved URI/source identity, not display title.
- Active UUID scratch directories are excluded from cleanup. Cleanup measures deleted bytes. Storage work runs off the UI thread; the UI labels usage as indexed media plus temporary files, not total device/app storage.
- Public URL validation, bounded/revalidated page redirects, bounded subtitle reads, ignored external native configuration/plugins and no remote code components. No account cookies, credential scraping or DRM bypass.
- Native downloads have a free-space watchdog, bounded retry/socket settings and one-hour execution timeout. Real per-transfer progress is displayed; playlist aggregate progress remains unknown. No speed/ETA is fabricated.

## Phases 5–6 — local media processing

- Document picker and library entry point for compression, trimming, audio extraction and conversion. Source content URIs are read-only and copied to private scratch space. FFmpeg input protocols are limited to local file/pipe.
- Encoders/muxers are enumerated and each candidate encoder must encode a local sample before it is offered. H.264/HEVC, VP9, AAC, Opus, MP3 and PCM are conditional, not universal claims. Software encoding only.
- High-quality, balanced and small-file presets preserve aspect ratio/even dimensions and avoid upscaling. Compact preset prefers verified HEVC with H.264 fallback. Optional size cap uses a bitrate budget and at most two attempts; the actual output must meet the cap. Non-shrinking compression results are not published.
- FFmpeg `out_time_us` drives progress; copy/validation/save stages are indeterminate. Completion reaches 100 only after validation/publication. Cancellation terminates the owned Studio process and removes its scratch work.
- Validate duration and required tracks, reject missing tracks/protected/HDR inputs, retain originals, bound logging/threads/time and watch free space. Output indexing failure rolls back the newly published file.
- Real SRT/VTT timed-cue conversion preserves Unicode and timing; JSON/HTML/unsupported subtitle input is not renamed to SRT. Subtitle publication uses safe names and pending-row rollback.
- Device testing exposed missing `libc++_shared.so` when Studio starts independently. The corrective implementation extracts that existing bundled library from the wrapper's Python archive without initializing the network downloader or fetching code. Its rerun is recorded below when available.

## Phases 7–8 — UI, library and queue

- Material 3 phone bottom navigation and wide-screen navigation rail, bounded content width, light/dark/system themes, Arabic RTL/English LTR. New installs default to Arabic; prior language preferences are retained.
- Keyed lazy lists for library/downloads, search across titles/formats/platforms/collections, media/favorite/clip filters, collection filtering, date/title/size sorting, rename/collection editing and confirmed deletion.
- Play/share uses the actual URI and resolver MIME, including WAV; failures are visible. Unknown sizes/resolutions remain unknown. Preview reports unavailable streams and releases its player on disposal.
- Errors are bilingual; Studio progress uses polite accessibility announcements. Material components provide standard semantics and touch targets. Automated UI tests exercise actual language/theme/navigation controls and inspect RTL/LTR order.
- No persistent foreground service: keep the app open. Process-death recovery restarts rather than resumes partial bytes. No automatic grouping/AI tagging or duplicate-content hash scanner is claimed.

## Phase 9 — verification evidence

| Run / exact source | Observed result |
| --- | --- |
| 38508436877 / `58fbde7` | Failed before Gradle: obsolete SDK `tools` package; corrected |
| 38508890686 / `96e4882` | Failed before Gradle: sdkmanager PATH; corrected with explicit setup action packages |
| 38509393540 / `980d9b0` | **PASS** `testDebugUnitTest assembleDebug lintDebug`, 6m 8s; artifact reports **58 unit tests, 0 failures/errors/skips; lint 0 errors** |
| 38509927868 / `d05604b` | **PASS** test/build/lint workflow |
| 38510090296 / `4ed5ef1` | Unit/build/lint job **PASS**; emulator failed before tests because requested userdata exceeded free disk |
| 38540458431 / `b508d52` | Unit/build/test-APK/lint job **PASS**. Emulator booted; **2 tests, 1 pass, 1 failure**. Lazy startup passed. FFmpeg failed to load `libc++_shared.so` before capability discovery. This is an application integration failure, not a passed media test. |
| 38541882373 / `99fcd2e` | Running at this checkpoint; includes release build/lint and expanded UI/native regressions |

Local Gradle was attempted but blocked before execution by `UnknownHostException: services.gradle.org`; JDK 17 is present, but no local Android SDK/device is available. Actual builds/tests run in GitHub Actions with full checkouts. `git diff --check` and shell syntax validation passed before each relevant milestone.

Instrumentation uses real generated video/audio, not mocked processing: compression reduction, trim duration, audio extraction, optional available WebM conversion, real progress/cancellation and SHA-256 preservation of originals. Downloader execution is checked offline with `--version`; no all-provider live compatibility claim follows from this check.

Native packaging audit of the verified `980d9b0` APK: **512 ELF files; 10 WebP-related dependencies have 4 KB alignment**. See the [release audit](RELEASE-READINESS.md). Lint has warnings (dependency/SDK updates, legacy icon/resources and style); it is not warning-free. Fixed-rate native scheduling and missing extraction-rule warnings were addressed.

Startup timings, memory and UI screenshots are collected by `scripts/verify-device.sh` on the emulator. These are measurements of that runner only, not physical-device performance promises. Final values will be added after the corresponding test run.

## Phase 10 — release audit and limits

Version is **0.2.0-rc1 / 2**; application ID and user-data schema are unchanged. CI now requires debug/test/release APK compilation, unit tests, debug/release lint and connected emulator tests. The release APK is unsigned. README now distinguishes real behavior, conditional support and open limits. See [source security review](SECURITY-REVIEW-2026-10-14.md).

Open release gates:

1. Rebuild/audit the native dependency bundle for 16 KB devices. Current native features explicitly reject unsupported page sizes. An upstream issue documents the same problem; no compatible release was found in the checked upstream listings.
2. Original signing identity, signed upgrade test with existing data, corresponding native source/licenses and current store requirements before public distribution.
3. ARM64 physical-device playback, audio/video sync listening, portrait/variable-frame-rate/long-file processing, low-space/power-loss and TalkBack/large-font/tablet evaluation. Hardware is unavailable here.
4. Live provider regression matrix; sources may require login, apply geographic/rate restrictions or change formats. No bypass is attempted.
5. Foreground-process lifetime, no byte-resume guarantee, no HDR processing, no hardware encoding claim, no complete DNS-rebinding defense or complete native CVE scan.

The app must not be described as production-ready solely because Gradle passes. Final regression and synchronization evidence follow in the next checkpoint.
