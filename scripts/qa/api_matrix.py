#!/usr/bin/env python3
"""Run selected functional suites on named synthetic API profiles, preserving per-API results."""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profiles", nargs="+", default=["api26-oct03", "api28-goal1004", "api29-goal1004", "api31-goal1004", "api33-goal1004", "api34-goal1004", "api35-goal1004", "api36-oct03", "api37-16kb-oct04"])
    parser.add_argument("--suites", nargs="+", choices=("data", "pdf", "provider", "integrity", "export-state", "system", "alarm", "interactions", "ui", "history-flow", "safety"),
                        default=["data", "pdf", "provider", "integrity", "system", "alarm"])
    parser.add_argument("--emulator", default="/home/gabirusky/Android/Sdk/emulator/emulator")
    parser.add_argument("--serial", default="emulator-5580", help="An explicit even emulator port reserved for synthetic profiles")
    parser.add_argument("--keep-going", action="store_true", help="Collect later suites after a test failure; the final result still fails")
    args = parser.parse_args()
    serial = re.fullmatch(r"emulator-(\d{4})", args.serial)
    if not serial or not 5556 <= int(serial[1]) <= 5680 or int(serial[1]) % 2:
        parser.error("Use an even synthetic emulator port from 5556 to 5680")
    for profile in args.profiles:
        if not re.fullmatch(r"[a-z][a-z0-9-]{0,32}", profile) or not (ROOT / ".cache/qa-avds" / profile / "synthetic-profile.json").is_file():
            parser.error(f"Prepare the named synthetic profile first: {profile}")
    adb = ["adb", "-s", args.serial]

    def run(parts, timeout=30, check=True):
        return subprocess.run(adb + parts, capture_output=True, text=True, timeout=timeout, check=check).stdout.strip()

    def prop(key):
        return run(["shell", "getprop", key], check=False)

    def stop_existing():
        devices = subprocess.run(["adb", "devices"], capture_output=True, text=True, check=True).stdout
        if args.serial not in devices:
            return
        avd = prop("ro.boot.qemu.avd_name") or prop("ro.kernel.qemu.avd_name")
        if avd != "DoseCerta_QA":
            raise RuntimeError("Refusing to stop an unverified or personal emulator on the selected port")
        if run(["shell", "pidof", "screenrecord"], check=False):
            raise RuntimeError("Finish the evidence recording before switching API profiles")
        run(["emu", "kill"])
        deadline = time.monotonic() + 45
        while time.monotonic() < deadline:
            devices = subprocess.run(["adb", "devices"], capture_output=True, text=True, check=True).stdout
            if args.serial not in devices:
                return
            time.sleep(1)
        raise TimeoutError("Synthetic emulator did not stop")

    run_id = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ") + "-api-matrix"
    output = ROOT / "docs/qa/runs" / run_id
    output.mkdir(parents=True, exist_ok=False)
    frozen = output / "apks"
    frozen.mkdir()
    for source in (ROOT / "app/build/outputs/apk/debug/app-debug.apk",
                   ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"):
        shutil.copyfile(source, frozen / source.name)
    manifest = {"runId": run_id, "serial": args.serial, "startedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
                "emulator": args.emulator, "profiles": args.profiles, "suites": args.suites, "results": [], "passed": False,
                "frozenApkDirectory": str(frozen.relative_to(ROOT)),
                "scope": "Synthetic Google images, debug APK functional coverage; OEM and signed release are separate gates"}
    emulator = None
    stream = None
    try:
        stop_existing()
        for profile in args.profiles:
            stream = (output / f"{profile}-launcher.txt").open("w")
            env = dict(os.environ, DOSECERTA_QA_EMULATOR=args.emulator, DOSECERTA_QA_PORT=serial[1])
            emulator = subprocess.Popen(["bash", "scripts/qa/start-emulator.sh", "host", profile], cwd=ROOT, env=env,
                                        stdout=stream, stderr=subprocess.STDOUT)
            deadline = time.monotonic() + 300
            while time.monotonic() < deadline and emulator.poll() is None:
                if prop("sys.boot_completed") == "1":
                    break
                time.sleep(2)
            else:
                raise RuntimeError(f"Synthetic {profile} did not boot; emulator exit={emulator.poll()}")
            api = prop("ro.build.version.sdk")
            # Fresh Google images can leave their SDK setup activity as HOME,
            # preventing the real task overview from opening. Provision only
            # this verified synthetic device, without granting app access.
            avd = prop("ro.boot.qemu.avd_name") or prop("ro.kernel.qemu.avd_name")
            if avd != "DoseCerta_QA":
                raise RuntimeError("Booted profile is not the dedicated synthetic AVD")
            run(["shell", "settings", "put", "global", "device_provisioned", "1"])
            run(["shell", "settings", "put", "secure", "user_setup_complete", "1"])
            setup = run(["shell", "pm", "path", "com.google.android.googlesdksetup"], check=False)
            if setup:
                run(["shell", "pm", "disable-user", "--user", "0", "com.google.android.googlesdksetup"])
            run(["shell", "input", "keyevent", "KEYCODE_WAKEUP"])
            run(["shell", "input", "keyevent", "KEYCODE_MENU"])
            run(["shell", "input", "keyevent", "KEYCODE_HOME"])
            time.sleep(15)  # Let initial SDK setup and boot services settle before instrumenting.
            manifest.setdefault("environmentSetup", {})[profile] = {
                "deviceProvisioned": True, "sdkSetupDisabled": bool(setup),
                "scope": "Synthetic OS setup only; app permissions remain under each suite's explicit control"}
            manifest["currentProfile"] = profile
            for suite in args.suites:
                before = set((ROOT / "docs/qa/runs").glob(f"*-{suite}"))
                with (output / f"{profile}-{suite}.txt").open("w") as log:
                    result = subprocess.run(["python3", "scripts/qa/instrument.py", "--serial", args.serial, "--suite", suite,
                        "--apk-dir", str(frozen), "--timeout-seconds", "900"], cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, timeout=1050)
                evidence = sorted(set((ROOT / "docs/qa/runs").glob(f"*-{suite}")) - before)
                manifest["results"].append({"profile": profile, "api": api, "suite": suite, "returnCode": result.returncode,
                    "evidence": [str(path.relative_to(ROOT)) for path in evidence]})
                (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
                if result.returncode and not args.keep_going:
                    raise RuntimeError(f"API {api} {suite} failed; inspect the preserved evidence")
            stop_existing()
            emulator.wait(timeout=30)
            stream.close(); stream = None; emulator = None
        manifest["passed"] = all(item["returnCode"] == 0 for item in manifest["results"])
    except Exception as error:
        manifest["failure"] = f"{type(error).__name__}: {error}"
    finally:
        if emulator is not None:
            # Retain a live failed profile for inspection; never kill an unidentified device.
            manifest["emulatorStillRunning"] = emulator.poll() is None
        if stream is not None:
            stream.close()
        manifest["finishedAt"] = dt.datetime.now(dt.timezone.utc).isoformat()
        (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(json.dumps({"evidence": str(output), "passed": manifest["passed"], "failure": manifest.get("failure")}))
    return 0 if manifest["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
