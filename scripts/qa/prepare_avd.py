#!/usr/bin/env python3
"""Create a fresh named synthetic profile without touching any personal AVD."""
import argparse
import json
import re
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", required=True)
    parser.add_argument("--image", required=True, help="SDK-relative system-images path")
    parser.add_argument("--sdk-root", type=Path, help="Read an image from this SDK without changing its files")
    args = parser.parse_args()
    if not re.fullmatch(r"[a-z][a-z0-9-]{0,32}", args.profile):
        parser.error("profile must be a simple synthetic name")
    project = Path(__file__).resolve().parents[2]
    sdk = (args.sdk_root or project / ".cache/qa-sdk").resolve()
    image = (sdk / args.image).resolve()
    if not image.is_relative_to(sdk / "system-images") or not (image / "system.img").is_file():
        parser.error("install the image under the selected SDK system-images directory first")
    if image.name != "x86_64":
        parser.error("this Linux QA profile requires an x86_64 image")
    profile = project / ".cache/qa-avds" / args.profile
    if profile.exists():
        parser.error("profile already exists; refusing to overwrite its disks or configuration")
    avd = profile / "DoseCerta_QA.avd"
    avd.mkdir(parents=True)
    target = image.parent.parent.name
    settings = {
        "AvdId": "DoseCerta_QA", "avd.ini.displayname": "DoseCerta synthetic " + args.profile,
        "avd.ini.encoding": "UTF-8", "abi.type": "x86_64", "hw.cpu.arch": "x86_64",
        "hw.cpu.ncore": "2", "hw.ramSize": "1536", "vm.heapSize": "256",
        "hw.lcd.width": "720", "hw.lcd.height": "1280", "hw.lcd.density": "320",
        "hw.gpu.enabled": "yes", "hw.gpu.mode": "software", "hw.keyboard": "yes",
        "hw.battery": "yes", "hw.audioInput": "no", "hw.camera.back": "none",
        "hw.camera.front": "none", "hw.sdCard": "no", "hw.mainKeys": "no",
        "disk.dataPartition.size": "2G", "image.sysdir.1": str(image) + "/",
        "tag.id": image.parent.name, "tag.display": image.parent.name,
        "target": target, "showDeviceFrame": "no", "skin.dynamic": "yes",
        "fastboot.forceColdBoot": "yes", "fastboot.forceFastBoot": "no",
        "PlayStore.enabled": str("playstore" in image.parent.name).lower(),
    }
    (avd / "config.ini").write_text("".join(f"{key}={value}\n" for key, value in settings.items()))
    (profile / "DoseCerta_QA.ini").write_text(f"avd.ini.encoding=UTF-8\npath={avd}\ntarget={target}\n")
    (profile / "synthetic-profile.json").write_text(json.dumps({"syntheticOnly": True,
        "profile": args.profile, "sdk": str(sdk), "image": str(image), "avd": "DoseCerta_QA"}, indent=2) + "\n")
    print(f"Prepared {args.profile}: bash scripts/qa/start-emulator.sh software {args.profile}")


if __name__ == "__main__":
    main()
