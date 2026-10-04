#!/usr/bin/env python3
"""Record a guarded synthetic UI journey with video and authoritative device evidence."""
from __future__ import annotations
import argparse
import datetime as dt
import hashlib
import json
import pathlib
import re
import subprocess
import tarfile
import time

ROOT = pathlib.Path(__file__).resolve().parents[2]
PACKAGE = "com.dosecerta"
RUNNER = PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"
JOURNEY = PACKAGE + ".ui.FullJourneyInstrumentedTest"


def sha256(path: pathlib.Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def utc_now() -> str:
    return dt.datetime.now(dt.timezone.utc).isoformat()


def write_json(path: pathlib.Path, value: dict) -> None:
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")
    temporary.replace(path)


def stop_process(process: subprocess.Popen | None) -> None:
    if process is None or process.poll() is not None:
        return
    process.terminate()
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=5)


def extract_archive(archive: pathlib.Path, output: pathlib.Path) -> None:
    with tarfile.open(archive) as tar:
        for member in tar.getmembers():
            target = (output / member.name).resolve()
            if not target.is_relative_to(output) or member.issym() or member.islnk():
                raise RuntimeError("Unsafe evidence archive member")
            if member.isdir():
                target.mkdir(parents=True, exist_ok=True)
            elif member.isfile():
                target.parent.mkdir(parents=True, exist_ok=True)
                with tar.extractfile(member) as source, target.open("wb") as destination:
                    destination.write(source.read())


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="Explicit dedicated synthetic AVD serial")
    parser.add_argument("--apk", type=pathlib.Path, default=ROOT / "app/build/outputs/apk/debug/app-debug.apk")
    parser.add_argument("--test-apk", type=pathlib.Path, default=ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")
    parser.add_argument("--scenario", choices=("main", "skip", "snooze", "silence", "denied", "nominal", "pdf-fixtures"), default="main")
    parser.add_argument("--clean", action="store_true", help="Uninstall/reinstall only DoseCerta packages, after the dedicated-AVD guard")
    parser.add_argument("--output", type=pathlib.Path, help="A new evidence directory; existing directories are rejected")
    parser.add_argument("--timezone", default="America/Sao_Paulo")
    parser.add_argument("--timeout-seconds", type=int, default=1200)
    parser.add_argument("--print-plan", action="store_true", help="Print commands without running or changing a device")
    args = parser.parse_args()
    if not 30 <= args.timeout_seconds <= 7200:
        parser.error("timeout-seconds must be 30..7200")
    adb = ["adb", "-s", args.serial]
    run_id = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ") + "-" + args.scenario
    if args.scenario == "pdf-fixtures":
        parameters = ["-e", "class", PACKAGE + ".ui.history.PdfReportInstrumentedTest"]
    elif args.scenario == "denied":
        parameters = ["-e", "class", JOURNEY + "#deniedNotificationIsVisibleAndDoesNotClaimAlarmAudio", "-e", "deniedJourney", "1"]
    else:
        action = args.scenario if args.scenario in ("skip", "snooze", "silence") else "take"
        parameters = ["-e", "class", JOURNEY + "#firstUseFutureDoseRealAlarmAndPersistedOutcome", "-e", "fullJourney", "1", "-e", "journeyAction", action, "-e", "journeyCount", "10" if args.scenario == "nominal" else "1"]
    parameters += ["-e", "evidenceRunId", run_id]
    instrument = adb + ["shell", "am", "instrument", "-w"] + parameters + [RUNNER]
    if args.print_plan:
        print(json.dumps({"guard": "ro.boot.qemu.avd_name or ro.kernel.qemu.avd_name=DoseCerta_QA; boot=1; exact timezone; no other screenrecord",
                          "serial": args.serial, "clean": args.clean, "instrumentation": instrument,
                          "permission_grants": "Visible UI only in the main journey", "recording": "Screenrecord starts before instrumentation; segments up to 175 seconds; no internal audio",
                          "scope": "pdf-fixtures is renderer/provider evidence, not a UI export demonstration"}, indent=2))
        return 0
    for path in (args.apk, args.test_apk):
        if not path.is_file():
            parser.error(f"Build artifact missing: {path}")

    def run(parts: list[str], *, timeout: int = 30, check: bool = True, binary: bool = False):
        return subprocess.run(adb + parts, capture_output=True, text=not binary, timeout=timeout, check=check)

    avd = run(["shell", "getprop", "ro.boot.qemu.avd_name"]).stdout.strip()
    if not avd:
        avd = run(["shell", "getprop", "ro.kernel.qemu.avd_name"]).stdout.strip()
    if avd != "DoseCerta_QA":
        parser.error(f"Refusing {args.serial}: AVD {avd!r} is not dedicated synthetic DoseCerta_QA")
    if run(["shell", "getprop", "sys.boot_completed"]).stdout.strip() != "1":
        parser.error("Dedicated AVD is not fully booted")
    timezone = run(["shell", "getprop", "persist.sys.timezone"]).stdout.strip()
    if timezone != args.timezone:
        parser.error(f"Device timezone {timezone!r} differs from explicit {args.timezone!r}")
    if run(["shell", "pidof", "screenrecord"], check=False).stdout.strip():
        parser.error("Another screenrecord is active; use one evidence driver per serial")
    output = (args.output or ROOT / "docs/evidence" / run_id).resolve()
    output.mkdir(parents=True, exist_ok=False)
    manifest_path = output / "manifest.json"
    git = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, capture_output=True, text=True, check=True).stdout.strip()
    status = subprocess.run(["git", "status", "--porcelain"], cwd=ROOT, capture_output=True, text=True, check=True).stdout
    page_size = run(["shell", "getconf", "PAGE_SIZE"], check=False).stdout.strip()
    page_size_source = "getconf PAGE_SIZE"
    if not page_size.isdigit():
        sizes = set(re.findall(r"^KernelPageSize:\s+(\d+) kB", run(["shell", "cat", "/proc/self/smaps"]).stdout, re.M))
        if len(sizes) != 1:
            parser.error("Cannot determine a single kernel page size for this device")
        page_size = str(int(sizes.pop()) * 1024)
        page_size_source = "KernelPageSize from /proc/self/smaps"
    manifest = {"schemaVersion": 1, "runId": run_id, "scenario": args.scenario, "startedAt": utc_now(), "serial": args.serial,
                "avd": avd, "timezone": timezone, "api": run(["shell", "getprop", "ro.build.version.sdk"]).stdout.strip(),
                "fingerprint": run(["shell", "getprop", "ro.build.fingerprint"]).stdout.strip(),
                "pageSize": page_size, "pageSizeSource": page_size_source, "gitCommit": git, "workingTree": status,
                "apk": {"path": str(args.apk), "sha256": sha256(args.apk)}, "testApk": {"path": str(args.test_apk), "sha256": sha256(args.test_apk)},
                "command": instrument, "cleanInstall": args.clean, "videoSegments": [], "collectionErrors": [], "controlActions": [],
                "audio": "Android screenrecord does not capture internal alarm audio. Observe audio separately; this video cannot prove sound.",
                "videoReviewed": False, "syntheticDataOnly": True, "instrumentationPassed": False,
                "scope": "PDF fixture rendering/provider tests only" if args.scenario == "pdf-fixtures" else "Visible UI onboarding, grants, future medicine creation, real scheduled delivery and outcome"}
    write_json(manifest_path, manifest)
    process = recorder = recorder_log = log_stream = active = None
    started = time.monotonic()
    failure = None

    def collection_error(stage: str, error: Exception) -> None:
        manifest["collectionErrors"].append({"stage": stage, "error": type(error).__name__, "message": str(error)})

    def finish_segment() -> None:
        nonlocal recorder, recorder_log, active
        if active is None:
            return
        try:
            if recorder.poll() is None:
                pid = active.get("remotePid")
                if pid:
                    run(["shell", "kill", "-2", pid], check=False)
                try:
                    recorder.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    stop_process(recorder)
            recorder_log.close()
            active["completedAt"] = utc_now()
            active["returnCode"] = recorder.returncode
            pulled = run(["pull", active["remote"], str(output / active["file"])], timeout=90, check=False)
            local = output / active["file"]
            active["available"] = pulled.returncode == 0 and local.is_file() and local.stat().st_size > 0
            if active["available"]:
                active["bytes"] = local.stat().st_size
                active["sha256"] = sha256(local)
            else:
                active["pullError"] = pulled.stderr.strip()
        except Exception as error:
            active["available"] = False
            collection_error("video", error)
            stop_process(recorder)
            if recorder_log:
                recorder_log.close()
        finally:
            manifest["videoSegments"].append(active)
            active = recorder = recorder_log = None
            write_json(manifest_path, manifest)

    def start_segment(number: int) -> None:
        nonlocal recorder, recorder_log, active
        remote = f"/sdcard/Download/dosecerta-evidence-{run_id}-{number:03d}.mp4"
        active = {"file": f"screen-{number:03d}.mp4", "remote": remote, "startedAt": utc_now(), "available": False}
        recorder_log = (output / f"screen-{number:03d}.log").open("w")
        recorder = subprocess.Popen(adb + ["shell", "screenrecord", "--time-limit", "175", "--bit-rate", "4000000", remote], stdout=recorder_log, stderr=subprocess.STDOUT)
        deadline = time.monotonic() + 5
        while time.monotonic() < deadline and recorder.poll() is None:
            pids = run(["shell", "pidof", "screenrecord"], check=False).stdout.split()
            if len(pids) == 1 and pids[0].isdigit():
                active["remotePid"] = pids[0]
                write_json(manifest_path, manifest)
                return
            time.sleep(0.2)
        raise RuntimeError("Screenrecord did not start; inspect segment log")

    try:
        if args.clean:
            run(["uninstall", PACKAGE + ".test"], check=False)
            run(["uninstall", PACKAGE], check=False)
        run(["install", "-r", str(args.apk.resolve())], timeout=180)
        run(["install", "-r", str(args.test_apk.resolve())], timeout=180)
        run(["shell", "settings", "put", "system", "time_12_24", "24"])
        run(["shell", "settings", "put", "system", "screen_off_timeout", "600000"])
        # A sleeping display has no layer stack on newer Android releases;
        # screenrecord exits before the test can launch the app. These visible
        # key events wake/unlock only the synthetic device, without granting access.
        run(["shell", "input", "keyevent", "KEYCODE_WAKEUP"])
        run(["shell", "input", "keyevent", "KEYCODE_MENU"])
        run(["logcat", "-c"])
        (output / "alarm-before.txt").write_text(run(["shell", "dumpsys", "alarm"]).stdout)
        start_segment(1)
        log_stream = (output / "instrumentation.txt").open("w")
        manifest["instrumentationStartedAt"] = utc_now()
        write_json(manifest_path, manifest)
        process = subprocess.Popen(instrument, stdout=log_stream, stderr=subprocess.STDOUT)
        instrumentation_started = time.monotonic()
        number = 1
        while process.poll() is None:
            if time.monotonic() - instrumentation_started > args.timeout_seconds:
                raise TimeoutError("Instrumentation observation timeout")
            if recorder.poll() is not None:
                finish_segment()
                if not manifest["videoSegments"][-1]["available"]:
                    raise RuntimeError("Screenrecord segment missing; preserving the failed run")
                number += 1
                start_segment(number)
            time.sleep(0.5)
    except (Exception, KeyboardInterrupt) as error:
        failure = f"{type(error).__name__}: {error}"
    finally:
        if process is not None and process.poll() is None:
            stop_process(process)
            try:
                run(["shell", "am", "force-stop", PACKAGE], check=False)
                manifest["controlActions"].append("Stopped the synthetic target package after incomplete instrumentation")
            except Exception as error:
                collection_error("stop_instrumentation", error)
        finish_segment()
        if log_stream:
            log_stream.close()
        for name, command in (("alarm-after.txt", ["shell", "dumpsys", "alarm"]),
                              ("notification-after.txt", ["shell", "dumpsys", "notification"]),
                              ("activity-after.txt", ["shell", "dumpsys", "activity", "activities"]),
                              ("audio-after.txt", ["shell", "dumpsys", "audio"]),
                              ("alarm-logcat.txt", ["logcat", "-d", "-s", "DoseCertaAlarm"]),
                              ("logcat.txt", ["logcat", "-d"])):
            try:
                result = run(command, timeout=90, check=False)
                (output / name).write_text(result.stdout + result.stderr)
                if result.returncode:
                    collection_error(name, RuntimeError(f"ADB returned {result.returncode}"))
            except Exception as error:
                collection_error(name, error)
        try:
            base = "cache" if args.scenario == "pdf-fixtures" else "files"
            folder = "qa-reports" if args.scenario == "pdf-fixtures" else f"qa-journey/{run_id}"
            exported = run(["exec-out", "run-as", PACKAGE, "tar", "-C", base, "-cf", "-", folder], binary=True, timeout=90, check=False)
            if exported.returncode:
                (output / "journey-export-error.txt").write_bytes(exported.stderr)
                raise RuntimeError(f"Evidence export returned {exported.returncode}")
            archive = output / "journey.tar"
            archive.write_bytes(exported.stdout)
            extract_archive(archive, output)
        except Exception as error:
            collection_error("app_artifacts", error)
        instrumentation_path = output / "instrumentation.txt"
        text = instrumentation_path.read_text() if instrumentation_path.is_file() else ""
        manifest["instrumentationReturnCode"] = process.returncode if process else None
        manifest["instrumentationPassed"] = process is not None and process.returncode == 0 and "OK (" in text and "FAILURES!!!" not in text and "INSTRUMENTATION_FAILED" not in text
        if args.scenario != "pdf-fixtures":
            try:
                result = json.loads((output / f"qa-journey/{run_id}/result.json").read_text())
                manifest["journeyResult"] = result
                manifest["instrumentationPassed"] = manifest["instrumentationPassed"] and result.get("runId") == run_id and result.get("passed", False)
            except Exception as error:
                manifest["instrumentationPassed"] = False
                collection_error("journey_result", error)
        manifest["completedAt"] = utc_now()
        manifest["elapsedSeconds"] = round(time.monotonic() - started, 2)
        manifest["failure"] = failure
        manifest["videoAvailable"] = bool(manifest["videoSegments"]) and all(item["available"] for item in manifest["videoSegments"])
        manifest["evidenceCollected"] = manifest["instrumentationPassed"] and manifest["videoAvailable"] and not failure and not manifest["collectionErrors"]
        write_json(manifest_path, manifest)
        index_path = ROOT / "docs/evidence/index.json"
        index_path.parent.mkdir(parents=True, exist_ok=True)
        index = json.loads(index_path.read_text()) if index_path.exists() else {"schemaVersion": 1, "runs": []}
        index["runs"].append({"runId": run_id, "manifest": str(manifest_path.relative_to(ROOT)) if manifest_path.is_relative_to(ROOT) else str(manifest_path),
                              "scenario": args.scenario, "instrumentationPassed": manifest["instrumentationPassed"], "videoAvailable": manifest["videoAvailable"], "evidenceCollected": manifest["evidenceCollected"], "videoReviewed": False})
        write_json(index_path, index)
    print(f"Evidence saved: {manifest_path}")
    print("Video review is pending; screenrecord does not establish alarm audio.")
    return 0 if manifest["evidenceCollected"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
