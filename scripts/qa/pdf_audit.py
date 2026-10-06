#!/usr/bin/env python3
"""Audit preserved PDF text, row identities, page bounds and database expectations."""
import argparse
from collections import Counter
import json
from pathlib import Path
import re
import subprocess
import tarfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]


def audit(run: Path):
    manifest = json.loads((run / "manifest.json").read_text())
    if not manifest.get("passed") or manifest.get("suite") not in ("pdf", "integrity"):
        raise ValueError(f"Not an approved PDF/integrity run: {run}")
    with tarfile.open(run / "artifacts.tar") as archive:
        for member in archive.getmembers():
            relative = Path(member.name)
            if not member.isfile() or not relative.is_relative_to("cache/qa-reports"):
                continue
            target = (run / relative).resolve()
            if not target.is_relative_to(run.resolve()) or member.issym() or member.islnk():
                raise ValueError("Unsafe evidence archive")
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(archive.extractfile(member).read())
    folder = run / "cache/qa-reports"
    expectations = json.loads((folder / "integrity-expected.json").read_text()) if manifest["suite"] == "integrity" else {
        name: {"rowIds": list(range(1, count + 1))} for name, count in (("empty.pdf", 0), ("many.pdf", 150), ("10000.pdf", 10000), ("long.pdf", 30))
    }
    results = []
    for name, expected in expectations.items():
        pdf = folder / name
        content = subprocess.run(["pdftotext", "-layout", str(pdf), "-"], capture_output=True, text=True, check=True).stdout
        row_ids = [int(value) for value in re.findall(r"\[(\d+)\]", content)]
        counts = Counter(row_ids)
        pages = int(re.search(r"^Pages:\s+(\d+)", subprocess.run(["pdfinfo", str(pdf)], capture_output=True, text=True, check=True).stdout, re.M)[1])
        bbox = ET.fromstring(subprocess.run(["pdftotext", "-bbox", str(pdf), "-"], capture_output=True, text=True, check=True).stdout)
        outside = []
        for word in bbox.iter("{http://www.w3.org/1999/xhtml}word"):
            if float(word.attrib["xMin"]) < 35.5 or float(word.attrib["xMax"]) > 559.5:
                outside.append(word.text)
        result = {"file": name, "pages": pages, "expectedRows": len(expected["rowIds"]), "rowCount": len(row_ids),
            "rowsOnce": set(counts) == set(expected["rowIds"]) and all(count == 1 for count in counts.values()),
            "wordsOutsideHorizontalMargins": outside[:20]}
        if not expected["rowIds"]:
            result["emptyNoData"] = "Sem dados" in content and "100%" not in content
        if "pageCount" in expected:
            result["pagesMatchRenderer"] = pages == expected["pageCount"]
            result["frozenNamePreserved"] = expected["frozenName"] in content
            result["renamedParentAbsent"] = expected["forbiddenName"] not in content
        result["passed"] = result["rowsOnce"] and not outside and all(result.get(key, True) for key in
            ("emptyNoData", "pagesMatchRenderer", "frozenNamePreserved", "renamedParentAbsent"))
        results.append(result)
    report = {"runId": manifest["runId"], "api": manifest["api"], "suite": manifest["suite"], "results": results,
              "passed": all(result["passed"] for result in results),
              "scope": "Extracted text and row IDs plus native PdfRenderer assertions in the linked run; visual review remains separate"}
    (run / "content-audit.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("runs", nargs="+", type=Path)
    args = parser.parse_args()
    reports = [audit(run.resolve()) for run in args.runs]
    print(json.dumps([{key: report[key] for key in ("runId", "api", "suite", "passed")} for report in reports], indent=2))
    return 0 if all(report["passed"] for report in reports) else 1


if __name__ == "__main__":
    raise SystemExit(main())
