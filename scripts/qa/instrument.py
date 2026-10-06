#!/usr/bin/env python3
"""Run selected Android checks on the dedicated synthetic AVD and preserve evidence."""
import argparse
import datetime as dt
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import tarfile

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "com.dosecerta"
SUITES = {
    "data": ["data.OccurrencePersistenceTest", "data.SyntheticScaleTest"],
    "pdf": ["ui.history.PdfReportInstrumentedTest"],
    "ui": ["ui.UiFlowInstrumentedTest"],
    "compact": ["ui.CompactUiInstrumentedTest"],
    "alarm": ["alarm.AlarmPipelineInstrumentedTest"],
    "system": ["ui.SystemUiInstrumentedTest"],
    "interactions": ["alarm.AlarmInteractionInstrumentedTest"],
    "provider": ["ui.history.ReportProviderInstrumentedTest"],
    "integrity": ["ui.history.ReportIntegrityInstrumentedTest"],
    "export-state": ["ui.history.ReportExportStateInstrumentedTest"],
    "safety": ["alarm.AlarmSafetyInstrumentedTest"],
    "surfaces": ["ui.SurfaceUiInstrumentedTest"],
    "history-flow": ["data.LargeHistoryFlowInstrumentedTest"],
    "capabilities": ["alarm.CapabilityUiInstrumentedTest"],
}
EXPECTED = {"data": 13, "pdf": 6, "ui": 1, "compact": 1, "alarm": 2, "system": 2, "interactions": 4, "provider": 2, "integrity": 1, "export-state": 2, "safety": 2, "surfaces": 3}
EXPECTED["history-flow"] = 1
EXPECTED["capabilities"] = 2


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--suite", choices=SUITES, required=True)
    parser.add_argument("--timeout-seconds", type=int, default=900)
    parser.add_argument("--ui-matrix", action="store_true", help="All locales/themes/font scales in the compact UI suite")
    parser.add_argument("--clean", action="store_true", help="Reset only DoseCerta packages after the dedicated synthetic AVD guard")
    parser.add_argument("--apk-dir", type=Path, help="Use a frozen pair of app-debug.apk and app-debug-androidTest.apk")
    args = parser.parse_args()
    if args.ui_matrix and args.suite != "compact":
        parser.error("ui-matrix is only valid with the compact UI suite")
    adb = ["adb", "-s", args.serial]

    def run(parts, **kwargs):
        return subprocess.run(adb + parts, capture_output=True, timeout=90, check=True, **kwargs)

    def prop(key):
        return run(["shell", "getprop", key], text=True).stdout.strip()

    avd = prop("ro.boot.qemu.avd_name") or prop("ro.kernel.qemu.avd_name")
    if avd != "DoseCerta_QA" or prop("sys.boot_completed") != "1":
        parser.error("A fully booted dedicated DoseCerta_QA AVD is required")
    if subprocess.run(adb + ["shell", "pidof", "screenrecord"], capture_output=True, text=True, timeout=30).stdout.strip():
        parser.error("Finish the active recording before running another instrumentation driver")
    run_id = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ") + "-" + args.suite
    output = ROOT / "docs/qa/runs" / run_id
    output.mkdir(parents=True, exist_ok=False)
    apks = ([args.apk_dir.resolve() / name for name in ("app-debug.apk", "app-debug-androidTest.apk")]
            if args.apk_dir else [ROOT / "app/build/outputs/apk/debug/app-debug.apk",
            ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"])
    if not all(path.is_file() for path in apks):
        parser.error("Build or supply both debug APKs first")
    manifest = {"runId": run_id, "serial": args.serial, "avd": avd, "suite": args.suite,
                "api": prop("ro.build.version.sdk"), "fingerprint": prop("ro.build.fingerprint"),
                "timezone": prop("persist.sys.timezone"), "syntheticOnly": True,
                "cryptoState": prop("ro.crypto.state"), "cryptoType": prop("ro.crypto.type"),
                "startedAt": dt.datetime.now(dt.timezone.utc).isoformat(), "passed": False,
                "cleanSyntheticInstall": args.clean,
                "apkSha256": {p.name: digest(p) for p in apks}, "collectionErrors": []}
    sizes = set(re.findall(r"^KernelPageSize:\s+(\d+) kB", run(["shell", "cat", "/proc/self/smaps"], text=True).stdout, re.M))
    manifest["kernelPageSizesBytes"] = sorted(int(size) * 1024 for size in sizes)
    manifest["pageSizeSource"] = "KernelPageSize of /proc/self/smaps (getconf is absent on API 26)"
    for apk in apks:
        if args.clean:
            subprocess.run(adb + ["uninstall", PACKAGE + (".test" if "androidTest" in apk.name else "")], capture_output=True, check=False, timeout=90)
        run(["install", "-r", str(apk)], text=True)
    parameters = ["-e", "class", ",".join(PACKAGE + "." + name for name in SUITES[args.suite])]
    if args.suite == "alarm":
        parameters += ["-e", "alarmPipeline", "1"]
    if args.ui_matrix:
        parameters += ["-e", "fullUiMatrix", "true"]
    command = adb + ["shell", "am", "instrument", "-w", "-r"] + parameters + [PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"]
    manifest["command"] = command
    try:
        with (output / "instrumentation.txt").open("w") as stream:
            result = subprocess.run(command, stdout=stream, stderr=subprocess.STDOUT, timeout=args.timeout_seconds)
        text = (output / "instrumentation.txt").read_text()
        manifest["returnCode"] = result.returncode
        manifest["skipped"] = len(re.findall(r"^INSTRUMENTATION_STATUS_CODE: -[34]\r?$", text, re.M))
        summary = re.search(r"OK \((\d+) tests?\)", text)
        manifest["tests"] = int(summary[1]) if summary else len(re.findall(
            r"^INSTRUMENTATION_STATUS_CODE: (?:0|-2|-3|-4)\r?$", text, re.M))
        manifest["passed"] = result.returncode == 0 and manifest["tests"] == EXPECTED[args.suite] and manifest["skipped"] == 0 and "FAILURES!!!" not in text
    except subprocess.TimeoutExpired:
        manifest["failure"] = "Instrumentation observation timed out"
        # The local adb process timing out does not stop instrumentation on Android.
        try:
            run(["shell", "am", "force-stop", PACKAGE], text=True)
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
            manifest["collectionErrors"].append(str(error))
    finally:
        for file, parts in [("logcat.txt", ["logcat", "-d"]), ("alarms.txt", ["shell", "dumpsys", "alarm"]),
                            ("notifications.txt", ["shell", "dumpsys", "notification"])]:
            try:
                result = subprocess.run(adb + parts, capture_output=True, text=True, timeout=30)
                (output / file).write_text(result.stdout + result.stderr)
                if result.returncode:
                    manifest["collectionErrors"].append(f"{file}: adb exited {result.returncode}")
            except subprocess.TimeoutExpired as error:
                manifest["collectionErrors"].append(f"{file}: {error}")
        try:
            if args.suite == "data":
                result = run(["exec-out", "run-as", PACKAGE, "cat", "files/qa-scale-measurement.json"], text=True)
                (output / "scale.json").write_text(result.stdout)
            if args.suite in ("pdf", "ui", "compact", "system", "interactions", "integrity", "safety", "surfaces", "history-flow", "capabilities"):
                folder = {"pdf": "cache/qa-reports", "ui": "files/qa-ui", "compact": "files/qa-compact", "system": "files/qa-system", "interactions": "files/qa-alarm-interactions", "integrity": "cache/qa-reports", "safety": "files/qa-alarm-safety", "surfaces": "files/qa-surfaces", "history-flow": "files/qa-history-flow", "capabilities": "files/qa-capabilities"}[args.suite]
                result = run(["exec-out", "run-as", PACKAGE, "tar", "-cf", "-", folder])
                (output / "artifacts.tar").write_bytes(result.stdout)
                try:
                    with tarfile.open(fileobj=io.BytesIO(result.stdout)) as archive:
                        members = archive.getmembers()
                        if not any(member.isfile() for member in members):
                            raise ValueError("Artifact archive has no files")
                except (tarfile.TarError, ValueError) as error:
                    manifest["collectionErrors"].append(f"artifacts.tar: {error}")
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
            manifest["collectionErrors"].append(str(error))
        manifest["finishedAt"] = dt.datetime.now(dt.timezone.utc).isoformat()
        manifest["instrumentationPassed"] = manifest["passed"]
        manifest["passed"] = manifest["passed"] and not manifest["collectionErrors"]
        (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(json.dumps({"evidence": str(output), "passed": manifest["passed"], "tests": manifest.get("tests"), "skipped": manifest.get("skipped")}))
    return 0 if manifest["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
