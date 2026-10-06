#!/usr/bin/env python3
"""Measure repeatable cold launches with 10,000 synthetic records on DoseCerta_QA."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "com.dosecerta"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--prepare-fixture", action="store_true",
                        help="Install APKs and seed an empty synthetic database; does not remove existing data")
    parser.add_argument("--clean", action="store_true", help="Reinstall only DoseCerta packages after the dedicated AVD guard")
    parser.add_argument("--samples", type=int, default=5)
    args = parser.parse_args()
    if not 3 <= args.samples <= 20:
        parser.error("samples must be 3..20")
    if args.clean and not args.prepare_fixture:
        parser.error("clean is only valid with prepare-fixture")
    adb = ["adb", "-s", args.serial]

    def run(parts, timeout=60):
        return subprocess.run(adb + parts, capture_output=True, text=True, check=True, timeout=timeout).stdout

    def prop(key):
        return run(["shell", "getprop", key]).strip()

    avd = prop("ro.boot.qemu.avd_name") or prop("ro.kernel.qemu.avd_name")
    if avd != "DoseCerta_QA" or prop("sys.boot_completed") != "1":
        parser.error("A fully booted dedicated DoseCerta_QA AVD is required")
    if subprocess.run(adb + ["shell", "pidof", "screenrecord"], capture_output=True, text=True, timeout=30).stdout.strip():
        parser.error("Finish the active recording before changing the performance fixture")
    run_id = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ") + "-performance"
    output = ROOT / "docs/qa/runs" / run_id
    output.mkdir(parents=True, exist_ok=False)
    apk = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
    manifest = {"runId": run_id, "serial": args.serial, "avd": avd, "api": prop("ro.build.version.sdk"),
                "fingerprint": prop("ro.build.fingerprint"), "timezone": prop("persist.sys.timezone"),
                "apkSha256": hashlib.sha256(apk.read_bytes()).hexdigest(), "syntheticOnly": True,
                "startedAt": dt.datetime.now(dt.timezone.utc).isoformat(), "passed": False,
                "coldStartScope": "am start -S -W: process cold, filesystem caches retained, first displayed frame",
                "setupScope": "Onboarding completion is fixture setup; not a visible onboarding demonstration",
                "cleanSyntheticInstall": args.clean,
                "samples": []}
    try:
        if args.prepare_fixture:
            if args.clean:
                for package in (PACKAGE + ".test", PACKAGE):
                    subprocess.run(adb + ["uninstall", package], capture_output=True, text=True, timeout=60)
            for path in (apk, ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"):
                run(["install", "-r", str(path)], timeout=180)
            seeded = run(["shell", "am", "instrument", "-w", "-r", "-e", "class",
                          PACKAGE + ".data.SyntheticScaleTest", "-e", "seedMainDatabase", "true",
                          "-e", "prepareColdStart", "true", PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"], timeout=300)
            (output / "fixture-instrumentation.txt").write_text(seeded)
            if "OK (1 test)" not in seeded or "FAILURES!!!" in seeded:
                raise RuntimeError("The 10k fixture did not pass; existing data is never overwritten")
        scale = json.loads(run(["exec-out", "run-as", PACKAGE, "cat", "files/qa-scale-measurement.json"]))
        if not scale.get("mainDatabaseSeeded") or scale["volumeBeforeAction"] != 10_000:
            raise RuntimeError("Seed the guarded main database with 10,000 synthetic rows first")
        manifest["fixture"] = scale
        run(["shell", "input", "keyevent", "KEYCODE_WAKEUP"])
        run(["shell", "input", "keyevent", "KEYCODE_MENU"])
        for sample in range(args.samples):
            launch = run(["shell", "am", "start", "-S", "-W", "-n", PACKAGE + "/.ui.MainActivity"])
            (output / f"cold-start-{sample + 1:02d}.txt").write_text(launch)
            fields = dict(re.findall(r"^(ThisTime|TotalTime|WaitTime):\s*(\d+)\s*$", launch, re.M))
            if "Status: ok" not in launch or "TotalTime" not in fields:
                raise RuntimeError("Cold launch timing unavailable")
            manifest["samples"].append({"index": sample + 1, **{name: int(value) for name, value in fields.items()}})
            # TTID above and asynchronous population are separate observations.
            # Do not assume Room emissions have completed after a fixed sleep.
            population_started = time.monotonic()
            while True:
                run(["shell", "uiautomator", "dump", "/sdcard/dosecerta-performance.xml"])
                hierarchy = run(["exec-out", "cat", "/sdcard/dosecerta-performance.xml"])
                if "Synthetic medication" in hierarchy and "bottom_navigation" in hierarchy:
                    break
                if time.monotonic() - population_started > 30:
                    raise RuntimeError("The populated Home did not appear after cold launch")
                time.sleep(0.25)
            manifest["samples"][-1]["populatedHomeObservedAfterLaunchMs"] = round((time.monotonic() - population_started) * 1000)
            (output / f"home-{sample + 1:02d}.xml").write_text(hierarchy)
        times = sorted(sample["TotalTime"] for sample in manifest["samples"])
        manifest["coldStartMedianMs"] = times[len(times) // 2]
        manifest["coldStartMaxMs"] = max(times)
        manifest["passed"] = max(times) < 2000 and all(scale[field] < 2000 for field in
            ("reportSnapshotMs", "takeCommitMs", "nextDose100SlotsMs"))
    except Exception as error:
        manifest["failure"] = f"{type(error).__name__}: {error}"
    finally:
        manifest["finishedAt"] = dt.datetime.now(dt.timezone.utc).isoformat()
        (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(json.dumps({"evidence": str(output), "passed": manifest["passed"],
                      "coldStartMaxMs": manifest.get("coldStartMaxMs"), "failure": manifest.get("failure")}))
    return 0 if manifest["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
