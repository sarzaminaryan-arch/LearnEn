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
# read out of the APK rather than assumed. aapt2 is the authority: it reads the
# compiled manifest and prints exactly the package id. Grepping the binary
# string pool was tried first and is unreliable, because it also matches the
# FileProvider authority and because NUL-stripping glues adjacent strings
# together.
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
  # no authority suffix).
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
# The package id and the activity class are not the same thing here: the debug
# and online flavours add an applicationId suffix (ir.laxipatem.lexiflash.debug),
# while a relative android:name in the manifest resolves against the manifest
# package. An earlier run asked for "$PKG/.MainActivity", which names a component
# that does not exist, and the activity was never started at all.
# The exact activity class comes from the APK itself. `badging` prints the fully
# qualified launchable-activity, which removes every guess about how a relative
# android:name was resolved against the manifest package.
LAUNCH_CLASS=""
if [ -n "$AAPT2" ]; then
  LAUNCH_CLASS=$("$AAPT2" dump badging "$APK" 2>/dev/null \
    | tr -d '\r' \
    | sed -n "s/^launchable-activity: name='\([^']*\)'.*/\1/p" \
    | head -n 1)
fi
echo "::notice::launchable_activity=${LAUNCH_CLASS:-unknown}"

COMPONENT=""
if [ -n "$LAUNCH_CLASS" ]; then
  COMPONENT="$PKG/$LAUNCH_CLASS"
else
  COMPONENT=$(adb shell cmd package resolve-activity --brief \
    -a android.intent.action.MAIN \
    -c android.intent.category.LAUNCHER \
    "$PKG" 2>&1 | tr -d '\r' | tail -n 1)
fi
echo "::notice::component=$COMPONENT"

adb logcat -c
AMOUT=$(adb shell am start -W -n "$COMPONENT" 2>&1)
AMRC=$?
echo "$AMOUT"
echo "am start exit: $AMRC"

# If the explicit component did not take, fall back to a launcher-equivalent
# start, so a bad component name cannot masquerade as an app crash.
case "$AMOUT" in
  *Error*|*"does not exist"*|*"Unable to find"*)
    echo "::warning::explicit component failed, retrying through monkey"
    MONKEYOUT=$(adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 2>&1)
    echo "$MONKEYOUT"
    AMOUT="$AMOUT
--- monkey fallback ---
$MONKEYOUT"
    ;;
esac

# Compose has to lay out and the warm-up has to seed the deck. A launch crash
# happens well inside this window.
sleep 25

# ------------------------------------------------------------------- verdict
ALIVE=no
if adb shell pidof "$PKG" > /dev/null 2>&1; then
  ALIVE=yes
fi
echo "::notice::process alive: $ALIVE"

CRASH=$(adb logcat -d -b crash 2>&1)
FATAL=$(adb logcat -d 2>&1 | grep -c "FATAL EXCEPTION" || true)

# GITHUB_WORKSPACE is stable across the emulator action's cwd handling, so the
# upload step finds the file without globbing.
LOG="${GITHUB_WORKSPACE:-$PWD}/smoke-report.txt"
{
  echo "variant=$VARIANT flavor=$FLAVOR"
  echo "apk=$APK"
  echo "package=$PKG"
  echo "component=$COMPONENT"
  echo "am_start_exit=$AMRC"
  echo "alive=$ALIVE"
  echo "fatal_count=$FATAL"
  echo
  echo "=========== AM START ==========="
  echo "$AMOUT"
  echo
  echo "=========== CRASH BUFFER ==========="
  echo "$CRASH"
  echo
  echo "=========== FULL LOGCAT ==========="
  adb logcat -d
} > "$LOG" 2>&1
echo "::notice::report written to $LOG"

# ------------------------------------------------ publish the evidence inline
# The uploaded artifact is fetched through an Azure blob host that is not
# reachable from where this log has to be read, and the job log is not readable
# either. Annotations are, so the slice that matters is republished as one. The
# emulator is still finishing boot during this window and fills logcat with
# system_server noise, so only lines about this package or the runtime's own
# crash output are kept.
# head, not tail: the first lines of a Java stack trace carry the exception type
# and message. A tail kept only the reflective frames at the bottom.
adb logcat -d 2>&1 \
  | grep -E "AndroidRuntime|FATAL|am_crash|am_proc_died|$PKG" \
  | grep -v "PACKAGE_ADDED\|FontsPackageChangeOp\|ChromeSync\|Unexpected forwarded" \
  | head -n 60 > /tmp/excerpt.txt

publish() {
  # $1 = file, $2 = label. The label becomes the first word of the message: on an
  # annotation only `::notice::` is valid, the `title=` parameter is a group
  # option and was being swallowed, which is why every annotation arrived untitled.
  local body
  body=$(tr -d '\r' < "$1" | head -c 2400 | sed -e 's/%/%25/g' -e 's/$/ | /' | tr -d '\n')
  [ -z "$body" ] && body="(empty)"
  printf '::notice::[%s] %s\n' "$2" "$body"
}

printf 'component=%s am_start_exit=%s alive=%s fatal=%s\n%s\n' \
  "$COMPONENT" "$AMRC" "$ALIVE" "$FATAL" "$AMOUT" > /tmp/launch.txt
publish /tmp/launch.txt "launch"
# Two halves: a 60-line trace does not fit in one annotation once the lines are
# folded, and the top of the trace is the half that names the exception.
head -n 25 /tmp/excerpt.txt > /tmp/excerpt-a.txt
tail -n +26 /tmp/excerpt.txt > /tmp/excerpt-b.txt
publish /tmp/excerpt-a.txt "trace top"
publish /tmp/excerpt-b.txt "trace bottom"

echo "=========== EXCERPT ==========="
cat /tmp/excerpt.txt
# The exception line itself, with its cause, verbatim and unwrapped.
adb logcat -d 2>&1 \
  | grep -A 6 "FATAL EXCEPTION" \
  | head -n 14 | tr -d '\r' > /tmp/fatal.txt
publish /tmp/fatal.txt "fatal"

echo "=========== CRASH BUFFER ==========="
echo "$CRASH"
echo "=========== FATAL COUNT: $FATAL ==========="
echo "=========== RESUMED ACTIVITY ==========="
adb shell dumpsys activity activities 2>&1 | grep -i -E "mResumedActivity|topResumedActivity" | head -n 3

if [ "$ALIVE" = "no" ]; then
  echo "::error::the app process is gone 25s after launch: it crashed"
  exit 1
fi

if [ "$FATAL" != "0" ]; then
  echo "::error::$FATAL fatal exception(s) logged during launch"
  exit 1
fi

echo "::notice::the app stayed up with no fatal exception"
exit 0
