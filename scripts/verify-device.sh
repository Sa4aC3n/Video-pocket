#!/usr/bin/env bash
set -uo pipefail

if [[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" != "1" ]]; then
  echo "Use a disposable emulator: connected tests replace the app and its private data." >&2
  exit 2
fi

# Keep evidence even when a test fails. Timings describe this emulator, not a phone.
bash gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.runPublicSmoke=${VIDEOPOCKET_PUBLIC_SMOKE:-false}" --stacktrace --console=plain
test_status=$?
evidence=app/build/reports/device
mkdir -p "$evidence"
adb logcat -d -v threadtime > "$evidence/logcat.txt"
# AGP copies additionalTestOutputDir before uninstalling the test application.
# Reinstall the exact verified debug APK for a separate cold-launch sample.
profiling_status=0
if adb install -r -t app/build/outputs/apk/debug/app-debug.apk; then
  adb shell am force-stop org.videopocket.probe
  adb shell am start -W -n org.videopocket.probe/.MainActivity > "$evidence/cold-start.txt"
  rg -q '^Status: ok' "$evidence/cold-start.txt" || profiling_status=1
  adb shell dumpsys meminfo org.videopocket.probe > "$evidence/memory.txt"
  if rg -q 'No process found' "$evidence/memory.txt"; then profiling_status=1; fi
  adb exec-out screencap -p > "$evidence/launch.png"
else
  profiling_status=1
fi
adb shell getprop ro.build.version.sdk > "$evidence/api.txt"
adb shell getconf PAGE_SIZE > "$evidence/page-size.txt"
if [[ "$test_status" -ne 0 ]]; then exit "$test_status"; fi
exit "$profiling_status"
