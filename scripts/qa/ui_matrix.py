#!/usr/bin/env python3
"""Run 320/360/412/600dp × PT/EN × light/dark × 100/130/200% on the dedicated AVD."""
import argparse
import datetime as dt
import json
import shutil
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--widths", nargs="+", type=int, choices=(320, 360, 412, 600), default=[320, 360, 412, 600])
    args = parser.parse_args()
    adb = ["adb", "-s", args.serial]

    def run(parts):
        return subprocess.run(adb + parts, capture_output=True, text=True, check=True, timeout=45).stdout.strip()

    avd = run(["shell", "getprop", "ro.boot.qemu.avd_name"]) or run(["shell", "getprop", "ro.kernel.qemu.avd_name"])
    if avd != "DoseCerta_QA" or run(["shell", "getprop", "sys.boot_completed"]) != "1":
        parser.error("A fully booted dedicated DoseCerta_QA AVD is required")
    original = {key: run(["shell", "wm", key]) for key in ("size", "density")}
    started = dt.datetime.now(dt.timezone.utc)
    path = ROOT / "docs/qa/runs" / (started.strftime("%Y%m%dT%H%M%S%fZ") + "-ui-matrix")
    path.mkdir(parents=True, exist_ok=False)
    frozen = path / "apks"
    frozen.mkdir()
    for source in (ROOT / "app/build/outputs/apk/debug/app-debug.apk", ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"):
        shutil.copyfile(source, frozen / source.name)
    manifest = {"serial": args.serial, "avd": avd, "api": run(["shell", "getprop", "ro.build.version.sdk"]),
                "startedAt": started.isoformat(), "originalDisplay": original, "passed": False, "runs": [],
                "configurationCount": len(args.widths) * 12, "frozenApkDirectory": str(frozen.relative_to(ROOT)),
                "scope": "Main surfaces for the specified display/locale/theme/font configurations; readers/dialogs are separate"}
    try:
        for width, height in ((320, 640), (360, 640), (412, 800), (600, 960)):
            if width not in args.widths:
                continue
            run(["shell", "wm", "size", f"{width * 2}x{height * 2}"])
            run(["shell", "wm", "density", "320"])
            before = set((ROOT / "docs/qa/runs").glob("*-compact"))
            command = ["python3", "scripts/qa/instrument.py", "--serial", args.serial, "--suite", "compact", "--clean", "--apk-dir", str(frozen), "--ui-matrix", "--timeout-seconds", "2400"]
            with (path / f"width-{width}.txt").open("w") as stream:
                result = subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, timeout=2600)
            created = set((ROOT / "docs/qa/runs").glob("*-compact")) - before
            manifest["runs"].append({"widthDp": width, "heightDp": height, "configurationCount": 12,
                "returnCode": result.returncode, "evidence": [str(item.relative_to(ROOT)) for item in sorted(created)]})
            (path / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
            if result.returncode:
                raise RuntimeError(f"UI matrix failed at {width}dp; inspect the preserved run")
        manifest["passed"] = True
    except Exception as error:
        manifest["failure"] = f"{type(error).__name__}: {error}"
    finally:
        for key, value in original.items():
            override = next((line.split(":", 1)[1].strip() for line in value.splitlines() if line.startswith("Override")), "reset")
            run(["shell", "wm", key, override])
        manifest["finishedAt"] = dt.datetime.now(dt.timezone.utc).isoformat()
        (path / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(json.dumps({"evidence": str(path), "passed": manifest["passed"], "failure": manifest.get("failure")}))
    return 0 if manifest["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
