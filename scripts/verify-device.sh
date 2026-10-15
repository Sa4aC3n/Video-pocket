#!/usr/bin/env bash
set -uo pipefail

# Keep evidence even when a test fails. Timings describe this emulator, not a phone.
bash gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.runPublicSmoke=${VIDEOPOCKET_PUBLIC_SMOKE:-false}" --stacktrace --console=plain
test_status=$?
evidence=app/build/reports/device
mkdir -p "$evidence"
adb logcat -d -v threadtime > "$evidence/logcat.txt"
adb pull /sdcard/Android/data/org.videopocket.probe/files/verification/. "$evidence/" || true
adb shell am force-stop org.videopocket.probe
adb shell am start -W -n org.videopocket.probe/.MainActivity > "$evidence/cold-start.txt"
adb shell dumpsys meminfo org.videopocket.probe > "$evidence/memory.txt"
adb shell getprop ro.build.version.sdk > "$evidence/api.txt"
adb shell getconf PAGE_SIZE > "$evidence/page-size.txt"
adb exec-out screencap -p > "$evidence/launch.png"
exit "$test_status"
