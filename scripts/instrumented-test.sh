#!/usr/bin/env bash
#
# Runs the instrumented tests on a booted emulator and publishes the result.
#
# Called as `bash scripts/instrumented-test.sh` from the emulator action, which
# invokes its `script` input with `sh -c`. /bin/sh is dash on Ubuntu and dash has
# no `set -o pipefail`, so nothing here may rely on a shebang being honoured.
#
# Publishing rule of this repository: the job log is not reachable from where
# these results have to be read, and the artifact API redirects to an Azure blob
# host that is blocked. Annotations are reachable, so every failure is repeated
# as a `::error::`/`::notice::` line.

set -x

VARIANT="${VARIANT:-debug}"
FLAVOR="${FLAVOR:-offline}"

REPORT="${GITHUB_WORKSPACE:-$PWD}/instrumented-report.txt"

{
  echo "flavour=$FLAVOR variant=$VARIANT"
  echo "started=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
} > "$REPORT"

# ------------------------------------------------------------------ environment
adb wait-for-device
echo "sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')" >> "$REPORT"
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true

# ---------------------------------------------------------------------- the run
# `:app:connectedDebugAndroidTest` is a shorthand for every connected variant.
# Naming the task explicitly keeps the output tied to the flavour under test.
TASK=":app:connected${FLAVOR^}${VARIANT^}AndroidTest"
echo "task=$TASK" >> "$REPORT"

# `gradle`, not `./gradlew`: this repository intentionally has no wrapper script
# (the wrapper jar is a binary in the tree and the CI image already provides a
# matching Gradle through gradle/actions/setup-gradle). Calling ./gradlew here
# failed with exit code 127, "command not found", before a single test ran.
#
# --console=plain keeps the output readable when it is folded into annotations;
# --info adds the test events, which is where a crash shows its trace.
set +e
gradle "$TASK" --console=plain --info --stacktrace > /tmp/gradle-test.log 2>&1
GRADLE_EXIT=$?
set -e
echo "gradle_exit=$GRADLE_EXIT" >> "$REPORT"

# ------------------------------------------------------- collect the evidence
# The test runner writes one HTML/XML report per class; the crash text lives in
# the XML. Concatenating them is what makes a single readable excerpt.
find app/build/outputs/androidTest-results -name "*.xml" -print 2>/dev/null \
  | tee -a "$REPORT" \
  | while read -r xml; do
      echo "=========== $xml ===========" >> "$REPORT"
      cat "$xml" >> "$REPORT"
    done

# logcat holds the trace for a process that died before a test could report it.
adb logcat -d -b crash > /tmp/crash.txt 2>&1 || true
adb logcat -d 2>&1 \
  | grep -E "AndroidRuntime|FATAL|am_crash|am_proc_died|TestRunner|Instrumentation" \
  | head -n 120 > /tmp/instrumented-excerpt.txt || true

{
  echo
  echo "=========== CRASH BUFFER ==========="
  cat /tmp/crash.txt 2>/dev/null
  echo
  echo "=========== TEST + RUNTIME EXCERPT ==========="
  cat /tmp/instrumented-excerpt.txt 2>/dev/null
  echo
  echo "=========== GRADLE TAIL ==========="
  tail -n 120 /tmp/gradle-test.log 2>/dev/null
} >> "$REPORT"

echo "::notice::report written to $REPORT"

# ------------------------------------------------------------- publish inline
publish() {
  # $1 = file, $2 = label, $3 = character budget
  local body
  body=$(tr -d '\r' < "$1" 2>/dev/null | head -c "$3" | sed -e 's/%/%25/g' -e 's/$/ | /' | tr -d '\n')
  [ -z "$body" ] && body="(empty)"
  printf '::notice::[%s] %s\n' "$2" "$body"
}

# The failure list first: it names the class and the assertion in one line each.
grep -E "^[0-9]+\) |^FAILED|tests completed" /tmp/gradle-test.log 2>/dev/null \
  | head -c 2000 > /tmp/failures.txt || true
publish /tmp/failures.txt "failures" 2000

# Then the top of any stack trace, which names the exception.
grep -A 12 "FATAL EXCEPTION" /tmp/crash.txt 2>/dev/null | head -c 2000 > /tmp/fatal.txt || true
publish /tmp/fatal.txt "fatal" 2000

publish /tmp/instrumented-excerpt.txt "runtime excerpt" 2400

if [ "$GRADLE_EXIT" -ne 0 ]; then
  echo "::error::the instrumented tests failed with exit code $GRADLE_EXIT"
  exit "$GRADLE_EXIT"
fi

echo "::notice::the instrumented tests passed"
exit 0
