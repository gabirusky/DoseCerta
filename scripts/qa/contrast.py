#!/usr/bin/env python3
"""Measure declared semantic text palettes. Pixel/surface and focus audits remain separate."""
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
PAIRS = [
    ("ui_on_primary", "ui_primary"), ("ui_on_primary", "ui_secondary"), ("ui_on_primary", "ui_tertiary"),
    ("ui_on_primary_container", "ui_primary_container"), ("ui_on_secondary_container", "ui_secondary_container"),
    ("ui_on_tertiary_container", "ui_tertiary_container"), ("ui_on_surface", "ui_background"),
    ("ui_on_surface", "ui_surface"), ("ui_on_surface", "ui_surface_variant"),
    ("ui_on_surface_variant", "ui_surface"), ("ui_on_surface_variant", "ui_surface_container"),
    ("ui_on_surface_variant", "ui_surface_variant"), ("ui_primary", "ui_background"),
    ("ui_primary", "ui_surface"), ("ui_primary", "ui_surface_container"),
    ("ui_on_error", "ui_error"), ("ui_error", "ui_background"), ("ui_error", "ui_error_container"),
    ("ui_on_error_container", "ui_error_container"), ("ui_success", "ui_success_container"),
    ("ui_warning", "ui_warning_container"),
] + [(text, background) for text in ("ui_on_hero", "ui_on_hero_secondary")
     for background in ("ui_hero_start", "ui_hero_center", "ui_hero_end")]


def colors(path):
    return {color.attrib["name"]: color.text for color in ET.parse(path).getroot().findall("color")}


def luminance(color):
    if len(color) != 7:
        raise ValueError("Only opaque RGB text pairs are measured")
    channels = [int(color[index:index + 2], 16) / 255 for index in (1, 3, 5)]
    channels = [value / 12.92 if value <= 0.04045 else ((value + 0.055) / 1.055) ** 2.4 for value in channels]
    return sum(weight * value for weight, value in zip((0.2126, 0.7152, 0.0722), channels))


def main():
    light = colors(ROOT / "app/src/main/res/values/design_colors.xml")
    dark = light | colors(ROOT / "app/src/main/res/values-night/design_colors.xml")
    results = []
    for mode, palette in (("light", light), ("dark", dark)):
        for foreground, background in PAIRS:
            low, high = sorted((luminance(palette[foreground]), luminance(palette[background])))
            ratio = (high + 0.05) / (low + 0.05)
            results.append({"mode": mode, "foreground": foreground, "background": background,
                            "ratio": round(ratio, 3), "normalTextPassed": ratio >= 4.5})
    report = {"scope": "Declared opaque semantic text pairs; decorative borders, images, alpha/disabled states and pixel compositing excluded",
              "results": results, "passed": all(row["normalTextPassed"] for row in results)}
    output = ROOT / "docs/qa/checks/semantic-contrast-20261004.json"
    output.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({"output": str(output), "passed": report["passed"], "pairs": len(results),
                      "failed": [row for row in results if not row["normalTextPassed"]]}, indent=2))
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
