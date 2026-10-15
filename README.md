# Video Pocket

تطبيق أندرويد لحفظ الوسائط العامة ومعالجة ملفاتك محليًا، بواجهة عربية وإنجليزية.
An Android app for downloading public media and processing local files, with Arabic and English interfaces.

Current development candidate: **0.2.0-rc1 (version code 2)**. This is not a signed production release. See [implementation evidence](docs/IMPLEMENTATION-2026-10-14.md) and [release readiness](docs/RELEASE-READINESS.md).

## Features

- Public-link inspection through provider adapters and bundled yt-dlp. Only source-reported video resolutions are offered. Login requirements, restricted content and DRM are reported; credentials are never scraped.
- Video, MP3 audio, playlist and clip downloads. Playlist files are individually indexed with measured filenames, sizes and media metadata.
- Persistent queue with FIFO scheduling, bounded concurrency, cancellation, retry, and pause/resume for waiting items. Interrupted active jobs require an explicit restart. **Keep the app open during downloads and processing**; a foreground service is not implemented.
- **Media Studio:** local video compression, trimming, audio extraction and format conversion. Encoders are listed and sample-tested on the device before formats are enabled. H.264/HEVC, VP9, AAC, Opus, MP3 and PCM are conditional on these probes.
- Compression presets, aspect-ratio preservation without upscaling, optional verified size cap, real timestamp progress and cancellation. Larger-than-original compression results are rejected. Originals are opened read-only and retained.
- Library search, media-type/favorite/collection filters, sorting, title editing, collections, play/share and confirmed deletion. Playback uses compatible installed players; clip preview uses Android's player when the source provides a usable stream.
- SRT/VTT subtitle conversion from supported timed tracks, with bounded fetching and safe publication. JSON/TTML subtitle conversion is not offered.
- Material 3 layout with phone navigation bar/tablet rail, Arabic RTL and English LTR, and light/dark/system themes. Lists load lazily and expensive storage operations run off the UI thread.
- Pending MediaStore publication, atomic library/queue snapshots and isolated temporary workspaces. Cleanup excludes active jobs.

No paid APIs or cloud processing are required. Provider availability changes independently of this app; adapter tests do not prove that every platform currently permits a download.

## Device requirements and limits

- Android 10 / API 29 or later; ARM64 and x86_64 builds.
- **The pinned native runtime currently requires 4 KB memory pages.** Native features fail clearly on other page sizes. Bundled WebP libraries need an upstream rebuild before 16 KB devices can be supported. App startup and library browsing remain independent of native initialization.
- Software encoding only; no hardware acceleration claim. HDR and protected media are rejected. Final codec support depends on the bundled runtime and device, and playback may require a compatible player.
- Enough free space is required for the original import, temporary output and final saved copy. Individual downloader files retain the existing 512 MiB limit; native jobs are bounded to one hour.
- Compression cannot guarantee a requested size at acceptable quality. Unreachable limits produce an error without replacing the source.

## Build and verify

Use JDK 17, Android SDK Platform 36 and Build Tools 35.0.0. The repository pins its Gradle wrapper and dependencies.

```sh
bash gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest assembleRelease lintDebug lintRelease --continue
# With an API 29+ emulator or compatible device attached:
bash scripts/verify-device.sh
# Audit the packaged libraries, including archives within the APK:
node scripts/check-native.mjs app/build/outputs/apk/release/app-release-unsigned.apk
```

The `Android verification` workflow runs on `main` and uploads Gradle reports, APKs, device-test results, screenshots and emulator startup/memory evidence on API 29 and API 36. Release APKs are unsigned. Install debug builds only for testing; updates to an existing installation require the same signing key.

## العربية

اختر رابطًا عامًا للفحص والتنزيل، أو افتح **استوديو الوسائط** لاختيار ملف من الجهاز وضغطه أو قصه أو استخراج صوته أو تحويله. تُعرض الجودات الفعلية والتنسيقات التي اجتازت فحص المحرك. تبقى الملفات الأصلية محفوظة.

أبقِ التطبيق مفتوحًا أثناء العمل. بعد توقف التطبيق، يمكن إعادة بدء التنزيل من شاشة التنزيلات. لا يتوفر استئناف جزئي للملفات. المعالجة على أجهزة صفحات الذاكرة ١٦ كيلوبايت وملفات HDR أو DRM غير مدعومة حاليًا. تفاصيل الاختبارات والقيود في التقرير المرتبط أعلاه.

## License

Application source: GPL-3.0-only. See [LICENSE](LICENSE) and [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md). Public binary distribution still requires the exact native dependency source/notices audit described in the release report.
