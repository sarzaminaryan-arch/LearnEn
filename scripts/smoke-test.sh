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
PKG=$(unzip -p "$APK" AndroidManifest.xml 2>/dev/null | tr -d '\0' | grep -ao 'ir\.laxipatem\.lexiflash[a-z.]*' | head -n 1)
if [ -z "$PKG" ]; then
  PKG=$(aapt2 dump packagename "$APK" 2>/dev/null | head -n 1)
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
adb shell am start -W -n "$PKG/.MainActivity"
echo "am start exit: $?"

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
