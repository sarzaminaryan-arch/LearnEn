#!/usr/bin/env bash
#
# Launches the built app on the emulator and reports what happened.
#
# This runs under bash on purpose. The emulator action invokes its `script`
# input with `sh -c`, and /bin/sh is dash on Ubuntu, where `set -o pipefail`
# fails with exit code 2 before anything is printed. Every earlier attempt to
# debug from this workflow died on that line.
#
# The script prints everything it does (`set -x`), and on failure it dumps the
# crash buffer instead of exiting quietly.

set -x

PACKAGE_HINT="ir.laxipatem.lexiflash"
VARIANT="${VARIANT:-release}"
FLAVOR="${FLAVOR:-offline}"

echo "::notice::variant=$VARIANT flavor=$FLAVOR"

# ---------------------------------------------------------------- locate APK
APK=$(find app/build/outputs/apk -path "*${FLAVOR}/${VARIANT}*" -name "*.apk" | head -n 1)
if [ -z "$APK" ]; then
  APK=$(find app/build/outputs/apk -path "*${VARIANT}*" -name "*.apk" | head -n 1)
fi
if [ -z "$APK" ]; then
  echo "::error::no APK found for $FLAVOR/$VARIANT"
  find app/build/outputs -name "*.apk" || true
  exit 1
fi
echo "::notice::apk=$APK"
ls -lh "$APK"

# The package id carries a suffix for debug and for the online flavour, so it is
# read out of the APK rather than assumed.
# aapt2 is the authority: it reads the compiled manifest and prints exactly the
# package id, including the debug/online suffix. Grepping the binary string pool
# was tried first and is unreliable: it also matches the FileProvider authority,
# and NUL-stripping glues adjacent strings together.
find_aapt2() {
  for candidate in \
    "$(command -v aapt2 2>/dev/null)" \
    "$ANDROID_HOME/build-tools/"*/aapt2 \
    "$ANDROID_SDK_ROOT/build-tools/"*/aapt2 \
    /usr/local/lib/android/sdk/build-tools/*/aapt2
  do
    if [ -n "$candidate" ] && [ -x "$candidate" ]; then
      echo "$candidate"
      return 0
    fi
  done
  return 1
}

AAPT2=$(find_aapt2 || true)
echo "aapt2=${AAPT2:-not found}"

PKG=""
if [ -n "$AAPT2" ]; then
  PKG=$("$AAPT2" dump packagename "$APK" 2>/dev/null | tr -d '\r' | head -n 1)
fi
if [ -z "$PKG" ]; then
  # Fallback: the pool approach, but only accept an exact id (no trailing dot,
  # no authority suffix) and prefer the longest match that the APK itself lists.
  PKG=$(unzip -p "$APK" AndroidManifest.xml 2>/dev/null \
    | tr '\0' '\n' \
    | grep -ao '^ir\.laxipatem\.lexiflash[A-Za-z0-9_.]*$' \
    | grep -v 'fileprovider' \
    | sort -r \
    | head -n 1)
fi
if [ -z "$PKG" ]; then
  PKG="$PACKAGE_HINT"
  echo "::warning::could not read the package from the APK; falling back to $PKG"
fi
echo "::notice::package=$PKG"

# ------------------------------------------------------------------- install
adb wait-for-device
adb shell getprop ro.build.version.sdk
adb install -r "$APK"
INSTALL_STATUS=$?
if [ $INSTALL_STATUS -ne 0 ]; then
  echo "::error::adb install failed with status $INSTALL_STATUS"
  exit 1
fi

# ------------------------------------------------------------------- launch
adb logcat -c
AMOUT=$(adb shell am start -W -n "$PKG/.MainActivity" 2>&1)
AMRC=$?
echo "$AMOUT"
echo "am start exit: $AMRC"

# Compose has to lay out and the warm-up has to seed the deck. A launch crash
# happens well inside this window.
sleep 25

# ------------------------------------------------------------------- verdict
ALIVE=no
if adb shell pidof "$PKG" > /dev/null 2>&1; then
  ALIVE=yes
fi
echo "::notice::process alive: $ALIVE"

# ------------------------------------------------------- export the evidence
# The uploaded artifact is fetched from an Azure blob host that is not reachable
# from where this log has to be read, and the job log itself is not readable
# either. Annotations are, so the interesting slice of logcat is republished as
# a run annotation, in chunks. Newlines terminate a workflow command, so they
# are folded into a visible marker first.
emit_annotation() {
  # $1 = file with the text to publish
  # GitHub truncates long annotations, so each chunk is kept to 30 lines and
  # about 3 KB, well inside the limit.
  local total chunks body
  total=$(wc -c < "$1" | tr -d ' ')
  chunks=$(( (total + 2500) / 3000 ))
  [ "$chunks" -lt 1 ] && chunks=1
  [ "$chunks" -gt 24 ] && chunks=24
  local i=1
  while [ "$i" -le "$chunks" ]; do
    body=$(sed -n "$(( (i-1)*30 + 1 )),$(( i*30 ))p" "$1" \
      | tr -d '\r' \
      | head -c 3000 \
      | sed -e 's/%/%25/g' -e 's/$/ | /' \
      | tr -d '\n')
    printf '::notice title=crash log %s/%s::%s\n' "$i" "$chunks" "$body"
    i=$(( i + 1 ))
  done
}

CRASH=$(adb logcat -d -b crash 2>&1)
FATAL=$(adb logcat -d 2>&1 | grep -c "FATAL EXCEPTION" || true)

# GITHUB_WORKSPACE is stable across the emulator action's cwd handling, so the
# upload step finds the file without globbing.
LOG="${GITHUB_WORKSPACE:-$PWD}/smoke-report.txt"
{
  echo "variant=$VARIANT flavor=$FLAVOR"
  echo "apk=$APK"
  echo "package=$PKG"
  echo "alive=$ALIVE"
  echo "fatal_count=$FATAL"
  echo
  echo "=========== CRASH BUFFER ==========="
  echo "$CRASH"
  echo
  echo "=========== FULL LOGCAT ==========="
  adb logcat -d
} > "$LOG" 2>&1
echo "::notice::report written to $LOG"

# Anything the runtime, the activity manager or our own process said.
adb logcat -d 2>&1 \
  | grep -i -E "AndroidRuntime|FATAL|lexiflash|ActivityTaskManager|ActivityManager|System.err|am_crash|Process .* has died|Force finishing|Unable to start" \
  | tail -n 400 > /tmp/excerpt.txt
echo "=========== EXCERPT ($(wc -l < /tmp/excerpt.txt) lines) ==========="
cat /tmp/excerpt.txt
emit_annotation /tmp/excerpt.txt

# The launcher's own verdict, in case the activity was never started at all.
printf '%s\n' "$AMOUT" > /tmp/am.txt
emit_annotation /tmp/am.txt

echo "=========== CRASH BUFFER ==========="
echo "$CRASH"
echo "=========== FATAL COUNT: $FATAL ==========="
echo "=========== APP LOGCAT ==========="
adb logcat -d 2>&1 | grep -i -E "lexiflash|AndroidRuntime|FATAL|System.err" | tail -n 200
echo "=========== RESUMED ACTIVITY ==========="
adb shell dumpsys activity activities 2>&1 | grep -i -E "mResumedActivity|topResumedActivity" | head -n 3

if [ "$ALIVE" = "no" ]; then
  echo "::error::the app process is gone 25s after launch: it crashed"
  exit 1
fi

if [ "$FATAL" != "0" ]; then
  echo "::error::$FATAL fatal exception(s) logged during launch"
  adb logcat -d 2>&1 | grep -A 40 "FATAL EXCEPTION" | head -n 120
  exit 1
fi

echo "::notice::the app stayed up with no fatal exception"
exit 0
