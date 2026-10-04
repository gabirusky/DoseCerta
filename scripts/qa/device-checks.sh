#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
serial="${1:?usage: device-checks.sh SERIAL}"
report_dir="docs/qa/device-$serial"
mkdir -p "$report_dir"
adb -s "$serial" get-state
adb -s "$serial" shell getprop > "$report_dir/properties.txt"
adb -s "$serial" shell getconf PAGE_SIZE > "$report_dir/page-size.txt"
adb -s "$serial" shell dumpsys package com.dosecerta > "$report_dir/package.txt"
ANDROID_SERIAL="$serial" ./gradlew --console=plain connectedDebugAndroidTest 2>&1 | tee "$report_dir/instrumentation.log"
adb -s "$serial" shell dumpsys alarm > "$report_dir/alarms.txt"
adb -s "$serial" shell dumpsys notification --noredact > "$report_dir/notifications-synthetic.txt"
