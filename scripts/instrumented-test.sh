#!/usr/bin/env bash
# Runs the androidTest suite on a booted emulator and always preserves evidence.
# Called through `bash scripts/instrumented-test.sh` by android-emulator-runner.

set -Eeuo pipefail

VARIANT="${VARIANT:-debug}"
FLAVOR="${FLAVOR:-offline}"
WORKSPACE="${GITHUB_WORKSPACE:-$PWD}"
REPORT="$WORKSPACE/instrumented-report.txt"
FULL_LOGCAT="$WORKSPACE/instrumented-logcat.txt"
CRASH_LOG="$WORKSPACE/instrumented-crash.txt"
GRADLE_LOG="$WORKSPACE/gradle-test.log"

: > "$REPORT"
printf 'flavor=%s\nvariant=%s\nstarted=%s\n' \
  "$FLAVOR" "$VARIANT" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "$REPORT"

collect_evidence() {
  local result=$?
  set +e

  # Capture both buffers even after Gradle, test-runner, or shell failure.
  if command -v adb >/dev/null 2>&1 && adb get-state >/dev/null 2>&1; then
    adb logcat -d -v threadtime > "$FULL_LOGCAT" 2>&1
    adb logcat -d -b crash > "$CRASH_LOG" 2>&1
    echo "sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')" >> "$REPORT"
  else
    printf 'adb unavailable or emulator disconnected\n' > "$FULL_LOGCAT"
    printf 'adb unavailable or emulator disconnected\n' > "$CRASH_LOG"
  fi

  if [ -f "$GRADLE_LOG" ]; then
    echo "gradle_log=$GRADLE_LOG" >> "$REPORT"
  else
    : > "$GRADLE_LOG"
    echo 'Gradle did not start.' >> "$REPORT"
  fi

  echo "script_exit=$result" >> "$REPORT"
  find "$WORKSPACE/app/build/outputs/androidTest-results" -type f -name '*.xml' -print 2>/dev/null \
    | while IFS= read -r xml; do
        echo "=========== $xml ===========" >> "$REPORT"
        cat "$xml" >> "$REPORT"
      done

  if [ "$result" -ne 0 ] && [ -f "$WORKSPACE/instrumented-failure.txt" ]; then
    echo '::error::Last Gradle output and failing test details follow; full files are attached as artifacts.'
    while IFS= read -r line; do
      escaped=$(printf '%s' "$line" | sed -e 's/%/%25/g' -e 's/\r/%0D/g')
      echo "::error::[failure detail] $escaped"
    done < "$WORKSPACE/instrumented-failure.txt"
    if [ -s "$CRASH_LOG" ]; then
      grep -E -A 50 -B 8 'FATAL EXCEPTION|No ActivityResultRegistryOwner|AndroidRuntime|Exception' "$CRASH_LOG" \
        | head -n 80 \
        | while IFS= read -r line; do echo "::error::[crash log] $line"; done
    fi
  fi

  {
    echo
    echo '=========== Gradle failure excerpt ==========='
    grep -E -A 12 -B 4 'FAILED|tests completed|FATAL EXCEPTION|Execution failed for task|What went wrong|Caused by:|Exception|Error:' "$GRADLE_LOG" \
      | tail -n 240
    echo
    echo '=========== Crash buffer excerpt ==========='
    tail -n 160 "$CRASH_LOG"
  } >> "$REPORT"
}
trap collect_evidence EXIT

# ------------------------------------------------------------------ environment
adb wait-for-device
echo "sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')" >> "$REPORT"
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true
adb logcat -c || true

# ---------------------------------------------------------------------- the run
TASK=":app:connected${FLAVOR^}${VARIANT^}AndroidTest"
echo "task=$TASK" >> "$REPORT"
if gradle "$TASK" --console=plain --info --stacktrace > "$GRADLE_LOG" 2>&1; then
  echo '::notice::instrumented tests passed'
else
  status=$?
  echo "::error::instrumented tests failed (Gradle exit $status); full report and logcat are uploaded as artifacts"
  {
    echo '--- Gradle final output ---'
    tail -n 100 "$GRADLE_LOG"
    echo '--- Parsed JUnit failure details ---'
    python3 - "$WORKSPACE/app/build/outputs/androidTest-results" <<'PY'
import os
import sys
import xml.etree.ElementTree as ET
root = sys.argv[1]
if os.path.isdir(root):
    for base, _, files in os.walk(root):
        for filename in files:
            if not filename.endswith('.xml'):
                continue
            path = os.path.join(base, filename)
            try:
                tree = ET.parse(path)
            except (ET.ParseError, OSError):
                continue
            for case in tree.iter('testcase'):
                for failure in list(case):
                    if failure.tag not in ('failure', 'error'):
                        continue
                    print(f"{path}: {case.get('classname')}.{case.get('name')}")
                    print(f"{failure.tag}: {failure.get('type', '')}: {failure.get('message', '')}")
                    print((failure.text or '')[:12000])
PY
  } > "$WORKSPACE/instrumented-failure.txt"
  while IFS= read -r line; do
    escaped=$(printf '%s' "$line" | sed -e 's/%/%25/g' -e 's/\r/%0D/g')
    echo "::error::[instrumented failure detail] $escaped"
  done < "$WORKSPACE/instrumented-failure.txt"
  # The EXIT trap gathers the complete logs before this status reaches Actions.
  exit "$status"
fi
