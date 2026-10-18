#!/usr/bin/env bash
set -uo pipefail

# Emit a failure as a GitHub Actions annotation. Annotations can be read through the
# checks API without downloading the (login-protected) job log.
#   $1 = title, $2 = message (newlines flattened, truncated)
annotate() {
  local msg
  msg=$(printf '%s' "$2" | tr '\r\n' '  ' | sed 's/%/%25/g' | cut -c1-700)
  echo "::error title=$1::$msg"
}

if [[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" != "1" ]]; then
  echo "Use a disposable emulator: connected tests replace the app and its private data." >&2
  exit 2
fi

# Keep evidence even when a test fails. Timings describe this emulator, not a phone.
bash gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.runPublicSmoke=${VIDEOPOCKET_PUBLIC_SMOKE:-false}" --stacktrace --console=plain
test_status=$?
if [[ "$test_status" -ne 0 ]]; then
  annotate "connectedDebugAndroidTest failed" "Gradle exit code $test_status"
fi

# Summarise the instrumented test results (JUnit XML written by AGP) as annotations.
mapfile -t result_xml < <(find app/build/outputs/androidTest-results -name 'TEST-*.xml' 2>/dev/null)
if [[ "${#result_xml[@]}" -gt 0 ]]; then
  python3 - "${result_xml[@]}" <<'PY'
import sys
import xml.etree.ElementTree as ET

def clean(text, limit=600):
    return " ".join((text or "").split()).replace("%", "%25")[:limit]

total = failed = 0
for path in sys.argv[1:]:
    try:
        root = ET.parse(path).getroot()
    except Exception as exc:  # unreadable report: say so instead of hiding it
        print("::error title=Test report unreadable::%s: %s" % (path, clean(str(exc))))
        continue
    for case in root.iter("testcase"):
        total += 1
        problem = case.find("failure")
        if problem is None:
            problem = case.find("error")
        if problem is not None:
            failed += 1
            name = "%s.%s" % (case.get("classname", "?").split(".")[-1], case.get("name", "?"))
            detail = problem.get("message") or problem.text
            print("::error title=Instrumented test failed::%s: %s" % (name, clean(detail)))
print("::notice title=Instrumented tests::%d run, %d failed" % (total, failed))
PY
else
  annotate "No instrumented test report" "No TEST-*.xml found under app/build/outputs/androidTest-results"
fi

evidence=app/build/reports/device
mkdir -p "$evidence"
adb logcat -d -v threadtime > "$evidence/logcat.txt"
# AGP copies additionalTestOutputDir before uninstalling the test application.
# Reinstall the exact verified debug APK for a separate cold-launch sample.
profiling_status=0
if adb install -r -t app/build/outputs/apk/debug/app-debug.apk; then
  # NEW_TASK | CLEAR_TASK; am has no --activity-new-task option.
  adb shell am start -S -W -f 0x10008000 -n org.videopocket.probe/.MainActivity > "$evidence/cold-start.txt" 2>&1
  if ! grep -Eq '^Status: ok' "$evidence/cold-start.txt"; then
    profiling_status=1
    annotate "Cold start check failed" "$(head -c 500 "$evidence/cold-start.txt")"
  fi
  adb shell dumpsys meminfo org.videopocket.probe > "$evidence/memory.txt"
  if grep -Eq 'No process found' "$evidence/memory.txt"; then
    profiling_status=1
    annotate "Memory sample failed" "App process not found after cold start"
  fi
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
    if ! grep -Eq 'OK \(1 test\)' "$evidence/tablet-ui.txt"; then
      profiling_status=1
      annotate "Tablet UI test failed" "$(grep -E 'stack=|Error|Exception|FAILURES|Tests run|shortMsg' "$evidence/tablet-ui.txt" | head -6)"
    fi
    mkdir -p "$evidence/tablet"
    if ! adb pull /sdcard/Android/data/org.videopocket.probe/files/verification/. "$evidence/tablet/"; then
      profiling_status=1
      annotate "Tablet evidence pull failed" "adb pull of the verification directory failed"
    fi
  else
    profiling_status=1
    annotate "Test APK install failed" "adb install of app-debug-androidTest.apk failed"
  fi
else
  profiling_status=1
  annotate "Debug APK install failed" "adb install of app-debug.apk failed"
fi
adb shell getprop ro.build.version.sdk > "$evidence/api.txt"
adb shell getconf PAGE_SIZE > "$evidence/page-size.txt"
adb logcat -d -v threadtime > "$evidence/logcat-after-launch.txt"
if [[ "$test_status" -ne 0 ]]; then exit "$test_status"; fi
exit "$profiling_status"
