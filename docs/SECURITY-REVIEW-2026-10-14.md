# Source security review — 2026-10-14

Scope: Android manifest, public-link policy/resolver, native process execution, subtitle fetching, downloads, local processing, MediaStore publication, queue/library persistence and temporary storage. This is a source and packaging review, not a penetration-test or vulnerability-free certification.

## Controls implemented and reviewed

- Only public HTTP/HTTPS input; reject user-info credentials, malformed/oversized URLs, literal private/loopback/link-local addresses and ambiguous numeric hosts. Redirects in the app's page/subtitle fetchers are bounded and revalidated.
- No account login flow, credential scraping, cookie import, DRM bypass, dynamic plugins, or remote executable components. yt-dlp ignores external configuration and plugins. Application-supplied process arguments are passed as arrays. The upstream wrapper uses a shell internally for process cancellation with application-generated UUIDs; no user-controlled URL/title is inserted into that command.
- Local FFmpeg input is imported from a read-only content URI. Its protocol whitelist is limited to file/pipe; native work has bounded threads, diagnostics, timeout and free-space watchdogs. Original files are never replaced.
- Subtitles have network timeouts, a 2 MiB read limit and validated timed-cue conversion. Export failures remove only the newly created pending row.
- MediaStore uses pending rows and validates finalization; error rollback targets only the current operation's newly created rows. Playlist outputs are individually validated/indexed. Failed user-requested deletion retains the library record.
- Library and queue use AtomicFile. Corrupt records are reported and retained. Active scratch workspaces are excluded from cleanup. App backup/device-transfer rules exclude private records.
- The only exported component is the launcher/share-text activity. There is no exported file provider/service or broad storage permission. Share intents grant read-only access to the specific content URI; pending intents are immutable.
- Diagnostics do not include full native process output or source URLs. Runtime/version fields are measured or explicitly unknown.

## Findings corrected

Shared transfer cancellation and publication races; playlist aggregate metadata copied to individual files; lost queue history; swallowed index errors; guessed quality/size labels; incorrect audio MIME selection; unbounded subtitle reads and mislabeled SRT export; synchronous storage cleanup; native resource exhaustion; missing device-transfer exclusions; unsupported 16 KB native runtime offered without a capability gate.

## Remaining limits

- Hostname resolution is not pinned to a prevalidated public IP. This is not a complete DNS-rebinding/SSRF defense, and yt-dlp controls its own downstream network requests. No private-network safety guarantee is made.
- The upstream downloader wrapper controls its subprocess tree and cancellation; unlike local Studio cancellation, interruption of every descendant during network merge is not device-verified. Native FFmpeg/Python/yt-dlp parse untrusted data in subprocesses under the app UID. Android app isolation is not a separate privilege boundary between these children and app-private files. Only process public/trusted media; native dependency updates and a separate service sandbox need additional work.
- This review did not run a complete current-CVE scan of all 512 ELF binaries and transitive JVM components. Native provenance/source distribution and 16 KB compatibility remain release gates.
- Saved metadata/URLs are app-private but not separately encrypted. Backup is disabled. User-selected exported reports omit source URLs; shared media itself is intentionally exposed to the selected recipient application.
- Process death may leave stale private scratch files or an interrupted task. Cleanup is available and restoration requires explicit restart. Cross-process transactionality between MediaStore and the JSON index cannot be guaranteed through a hard process kill.
- A signed release and physical-device upgrade test remain necessary before production distribution.
