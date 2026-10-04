#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p docs/qa/checks
./gradlew --console=plain assembleDebug assembleRelease bundleRelease lintDebug lintRelease testDebugUnitTest assembleDebugAndroidTest 2>&1 | tee docs/qa/checks/gradle.log
./gradlew --console=plain :app:dependencies --configuration releaseRuntimeClasspath > docs/qa/checks/release-dependencies.txt
python3 scripts/qa/audit_dependencies.py --input docs/qa/checks/release-dependencies.txt --configuration releaseRuntimeClasspath
python3 scripts/qa/audit_artifact.py app/build/outputs/apk/debug/app-debug.apk --require-native-alignment > docs/qa/checks/apk-audit.json
python3 scripts/qa/audit_artifact.py app/build/outputs/apk/release/app-release-unsigned.apk --require-native-alignment > docs/qa/checks/release-apk-audit.json
python3 scripts/qa/audit_artifact.py app/build/outputs/bundle/release/app-release.aab --require-native-alignment > docs/qa/checks/aab-audit.json
