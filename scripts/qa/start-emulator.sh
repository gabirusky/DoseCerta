#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."

# Only the separate synthetic AVD may be launched by this helper. No wiping,
# snapshots, personal AVD edits or installation of packages in the user's SDK.
avd_home="${DOSECERTA_QA_AVD_HOME:-/tmp/dosecerta-qa-avd}"
sdk="${DOSECERTA_QA_SDK:-/home/gabirusky/Android/Sdk}"
emulator="${DOSECERTA_QA_EMULATOR:-$PWD/.cache/qa-sdk/emulator/emulator}"
gpu="${1:-software}"
case "$gpu" in auto|host|software|lavapipe|swiftshader|swangle) ;; *) echo 'Unsupported GPU mode' >&2; exit 2 ;; esac
if test -n "${2:-}"; then
    [[ "$2" =~ ^[a-z][a-z0-9-]{0,32}$ ]] || { echo 'Invalid synthetic profile' >&2; exit 2; }
    avd_home="$PWD/.cache/qa-avds/$2"
    sdk="$PWD/.cache/qa-sdk"
    test -f "$avd_home/synthetic-profile.json"
fi
case "$(realpath "$avd_home")" in /tmp/dosecerta-qa-avd|"$PWD"/.cache/qa-avds/*) ;; *) echo 'Refusing a personal AVD directory' >&2; exit 2 ;; esac
config="$avd_home/DoseCerta_QA.avd/config.ini"
test -f "$config"
rg -q '^AvdId\s*=\s*DoseCerta_QA\s*$' "$config"
test -x "$emulator"
if adb -s emulator-5580 emu avd name >/dev/null 2>&1; then
    echo 'Port 5580 already has a device; inspect it before launching another instance' >&2
    exit 2
fi
mkdir -p docs/qa/checks
case "${3:-headless}" in
    headless) window_args=(-no-window) ;;
    window) window_args=() ;;
    *) echo 'Use headless or window for the third argument' >&2; exit 2 ;;
esac
log="docs/qa/checks/emulator-$gpu-${2:-legacy}-${3:-headless}-$(date -u +%Y%m%dT%H%M%SZ).log"
printf 'Synthetic AVD: DoseCerta_QA; GPU: %s; log: %s\n' "$gpu" "$log"
export ANDROID_AVD_HOME="$avd_home"
export ANDROID_HOME="$sdk"
export ANDROID_SDK_ROOT="$sdk"
# The host driver fails Vulkan external-memory imports on the newer QA images.
# OpenGL still exercises the app; Vulkan rendering is not part of its contract.
exec "$emulator" -avd DoseCerta_QA "${window_args[@]}" -no-audio -no-snapshot \
    -gpu "$gpu" -feature -Vulkan -port 5580 -timezone America/Sao_Paulo -show-kernel >"$log" 2>&1
