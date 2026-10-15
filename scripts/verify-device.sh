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
  # NEW_TASK | CLEAR_TASK; am has no --activity-new-task option.
  adb shell am start -S -W -f 0x10008000 -n org.videopocket.probe/.MainActivity > "$evidence/cold-start.txt" 2>&1
  rg -q '^Status: ok' "$evidence/cold-start.txt" || profiling_status=1
  adb shell dumpsys meminfo org.videopocket.probe > "$evidence/memory.txt"
  if rg -q 'No process found' "$evidence/memory.txt"; then profiling_status=1; fi
  adb exec-out screencap -p > "$evidence/launch.png"
  # Exercise the actual wide-window rail and larger text on this disposable emulator.
  trap 'adb shell wm size reset; adb shell wm density reset; adb shell settings put system font_scale 1.0' EXIT
  adb shell wm size 1920x1200
  adb shell wm density 160
  adb shell settings put system font_scale 1.5
  adb shell wm size > "$evidence/tablet-display.txt"
  adb shell wm density >> "$evidence/tablet-display.txt"
  if adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk; then
    adb shell am instrument -w -r -e class org.videopocket.probe.UiRegressionTest -e tabletUi true \
      org.videopocket.probe.test/androidx.test.runner.AndroidJUnitRunner > "$evidence/tablet-ui.txt"
    rg -q 'OK \(1 test\)' "$evidence/tablet-ui.txt" || profiling_status=1
    mkdir -p "$evidence/tablet"
    adb pull /sdcard/Android/data/org.videopocket.probe/files/verification/. "$evidence/tablet/" || profiling_status=1
  else
    profiling_status=1
  fi
else
  profiling_status=1
fi
adb shell getprop ro.build.version.sdk > "$evidence/api.txt"
adb shell getconf PAGE_SIZE > "$evidence/page-size.txt"
adb logcat -d -v threadtime > "$evidence/logcat-after-launch.txt"
if [[ "$test_status" -ne 0 ]]; then exit "$test_status"; fi
exit "$profiling_status"
