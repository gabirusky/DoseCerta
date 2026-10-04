# Baseline — 2026-09-29

Base commit: `cc2ca144e10c720345ed6924c7b7e7ef27c2cfbe`. Initial tracked modifications preserved in `initial-local-changes.patch`; tracked/untracked list in `initial-status.txt`. Existing untracked requirements and documentation retained. No reset or cleanup performed.

Execution phases: screen root/toolchain and preserve initial state; discover data/alarm/report/UI dependency boundaries; trace and implement each assigned stream; integrate and verify actual artifacts. Three independent agents own data/recurrence, forms/layouts and alarms respectively; root owns build, reports and release checks.

Toolchain observed: Gradle wrapper 8.14-all, AGP 8.13.2, Kotlin 1.9.20, KSP 1.9.20-1.0.14, local JetBrains JDK 21.0.9 (system JDK25 is not the build JDK). Initial module compile/target34, min26. Installed SDK platforms34/35/36/36.1 and build tools34–36.1. API36.1 Google Play x86_64 image installed; no connected adb device at initial probe.

Static findings: destructive Room fallback; historical details joined against mutable medication; status corrections substitute current timestamp; PDF exports cached `logs.value`; storage permission/file URI legacy path; dual exact-alarm permissions, overlay/battery requests; backup XML uses FileProvider syntax; competing Main/setup permission prompts. These are source findings, not claims of observed clinical/device failures.

## Preserved artifacts and observed results

The pre-existing APK was preserved at `baseline-artifacts/app-debug.apk`, SHA-256 `acc07dae118b1a91c263f1cd8c9fb1c643a6132f43fc4ae40864527b4f65c061`. AAPT confirmed compile/target 34, min 26 and versionCode 1. Exact reproducibility from the base commit has not been established. `baseline-merged-manifest.xml` preserves the available baseline manifest; no historical build success is inferred from an existing file.

Integration on 2026-09-30 generated debug and instrumented APKs with min 26 / target 36 and exported Room schema 4. Nineteen domain JVM tests passed at 02:52:50 UTC. Lint failed with 43 errors / 228 warnings; source fixes followed and are not covered by those old APKs. See `checks-integration.log`, test XMLs in `app/build/test-results/testDebugUnitTest`, and the workstream notes. The audited debug ZIP contained no `.so` files; that does not establish final AAB or 16 KB runtime compatibility.

The user's Android Studio screenshot and previous ADB probe established a personal API 36.1 emulator; it reported encrypted/file storage and 4096-byte pages. Observations are scoped to that emulator/session. No Xiaomi/Samsung alarm defect was reproduced. Caller-reported failure requires scheduled real-event reproduction; manual broadcasts are not accepted evidence.

Only a separate synthetic AVD was reset during preparation. Its host/software-renderer attempts failed with exit 139 before app tests; details in [emulator-validation.md](emulator-validation.md). At this checkpoint no emulator is connected. Personal AVD/user data were preserved. The user requested emulator-only work, then **skip tests for now**. Device/runtime tests, rerun lint and the new calendar test remain pending.
