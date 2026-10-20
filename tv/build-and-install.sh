#!/bin/sh
set -eu
cd "$(dirname "$0")"
python3 scripts/fetch-engine.py
if [ -n "${GRADLE_BIN:-}" ]; then
 "$GRADLE_BIN" assembleDebug testDebugUnitTest --console plain
elif command -v gradle >/dev/null 2>&1; then
 gradle assembleDebug testDebugUnitTest --console plain
else
 echo 'Set GRADLE_BIN to Gradle 8.9, JAVA_HOME to JDK 17 and ANDROID_HOME to Android SDK (35).' >&2
 exit 1
fi
if [ "${1:-}" = '--install' ]; then
 adb -s "${TV:-18554284261806}" install -r app/build/outputs/apk/debug/app-debug.apk
fi
