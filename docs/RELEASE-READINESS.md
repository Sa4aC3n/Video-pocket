# Release readiness — updated 2026-10-15

Candidate: **0.2.0-rc1**, versionCode **2**, applicationId **org.videopocket.probe**.
Compile/target SDK is **36**, meeting the currently documented phone-app target requirement. The application ID and existing library/settings schema are retained. Existing signing keys have not been changed or generated. Do not uninstall an existing installation to work around a signing mismatch: use its original signing key for an update.

## Gates

| Gate | Status |
| --- | --- |
| Incremental main-only development; no history rewrite | Verified at every implementation milestone |
| Debug build, unit tests and Android lint | Passed on multiple milestones; exact final evidence is in the implementation report |
| Unsigned release build and release lint | Passed on `ea76a0f` and `bc26467`; exact final run is in the implementation report |
| Native media processing and UI instrumentation | Four tests passed on API 29 at `bc26467`; API 29/36 and live-download evidence are recorded in the final implementation report |
| ARM64 physical-device testing, playback listening, thermal/battery profiling | Not available in this environment |
| 16 KB memory-page compatibility | **Blocked**: ten 4 KB-aligned WebP-related ELF dependencies in the pinned runtime |
| Production signing | **Not performed**: original signing identity/key is needed |
| Native corresponding source and licenses | **Open distribution gate**: wrapper provenance is not a complete source bundle |
| Live platform regression matrix | Contract tests run; no claim that all platforms have been validated live |

## Native compatibility finding

`scripts/check-native.mjs` walks the APK and nested native archives. The verified debug artifact from commit `980d9b09e9159424f8332c9571b270cfd1c15be4` contained **512 ELF files**. Ten had PT_LOAD alignments of **4096**, not 16384: `libsharpyuv.so`, `libwebpdecoder.so`, `libwebp.so`, `libwebpdemux.so`, `libwebpmux.so`, each under both ARM64 and x86_64 `libffmpeg.zip.so` archives.

This scan covers segment alignment only; it is not a runtime or full 16 KB compatibility certification. The application explicitly checks the device page size before initializing the affected native runtime. Unsupported page sizes yield an actionable error instead of pretending processing is available. No binary headers were patched and no third-party binary was silently replaced.

Upstream still lists wrapper **0.18.1**, and [issue 365](https://github.com/yausername/youtubedl-android/issues/365) describes the same nested WebP alignment problem. Correct remediation is an audited compatible build of the native dependencies and new device tests, including a 16 KB ARM64 device/emulator. Changing APK zip alignment alone does not rebuild these ELF segments. [Android guidance](https://developer.android.com/guide/practices/page-sizes) describes rebuilding prebuilt native dependencies.

## Packaging and distribution

CI builds a debug APK and an unsigned release APK. These are review/test artifacts, not a Play submission. No production key, store credentials, or release tag was created. APK shrinking is not enabled in this candidate; establish an R8 regression baseline before changing it.

Before binary publication: resolve native compatibility and source/notices gates, sign with the owner's original identity, test a non-destructive upgrade from the previous installed version with existing library/settings, verify current store requirements, and run the physical-device checklist in the implementation report.
