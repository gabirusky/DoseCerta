#!/usr/bin/env python3
"""Inventory a resolved classpath, exact cached POM licenses and archive notices."""
import argparse
import hashlib
import io
import json
import re
import xml.etree.ElementTree as ET
import zipfile
from datetime import datetime, timezone
from pathlib import Path

NS = {"p": "http://maven.apache.org/POM/4.0.0"}
TREE_NODE = re.compile(r"^[| \t]*(?:\+---|\\---)\s+(\S+)(?:\s+->\s+(\S+))?")


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def metadata_filename(coordinate):
    return coordinate.replace(":", "_") + ".pom"


def resolved_coordinates(path, input_format):
    if input_format == "lint-package":
        root = ET.parse(path).getroot()
        return sorted({n.attrib["name"].split("@")[0]
                       for n in root.findall("./package/dependency")})
    result = set()
    text = path.read_text(encoding="utf-8")
    if "FAILED" in text:
        raise ValueError("dependency report contains unresolved/FAILED dependencies")
    for line in text.splitlines():
        if "(c)" in line:  # constraints are not artifacts
            continue
        match = TREE_NODE.match(line)
        if not match:
            continue
        requested, selected = match.groups()
        if requested.count(":") != 2:
            raise ValueError("unsupported dependency coordinate: " + requested)
        coordinate = requested
        if selected:
            coordinate = selected if selected.count(":") == 2 else requested.rsplit(":", 1)[0] + ":" + selected
        if any(c in coordinate for c in "{}[]"):
            raise ValueError("unresolved/ranged version in report: " + coordinate)
        result.add(coordinate)
    if not result:
        raise ValueError("no resolved dependencies found in input")
    return sorted(result)


def pom_metadata(coordinate, cache, metadata_dir, visited=None):
    visited = set() if visited is None else visited
    if coordinate in visited:
        return None, [], "cyclic POM parent"
    visited.add(coordinate)
    group, module, version = coordinate.split(":")
    saved = metadata_dir / metadata_filename(coordinate)
    source = saved if saved.exists() else next(iter(sorted((cache / group / module / version).glob("*/*.pom"))), None)
    if source is None:
        return None, [], "exact POM absent"
    data = source.read_bytes()
    if source != saved:
        saved.write_bytes(data)
    root = ET.fromstring(data)
    licenses = [{"name": n.findtext("p:name", default="", namespaces=NS),
                 "url": n.findtext("p:url", default="", namespaces=NS)}
                for n in root.findall("p:licenses/p:license", NS)]
    evidence = {"file": str(saved), "sha256": sha256(data), "coordinate": coordinate,
                "upstream_url": ("https://dl.google.com/dl/android/maven2/" if group.startswith("androidx") or group == "com.google.android.material" else "https://repo.maven.apache.org/maven2/")
                + group.replace(".", "/") + "/" + module + "/" + version + "/" + module + "-" + version + ".pom"}
    if licenses:
        return evidence, licenses, None
    parent = root.find("p:parent", NS)
    if parent is not None:
        parent_coordinate = ":".join(parent.findtext("p:" + tag, default="", namespaces=NS)
                                     for tag in ("groupId", "artifactId", "version"))
        parent_evidence, inherited, issue = pom_metadata(parent_coordinate, cache, metadata_dir, visited)
        evidence["parent_license_evidence"] = parent_evidence
        return evidence, inherited, issue
    return evidence, [], "no license declaration in exact POM or parent"


def archive_inventory(path, notice_dir):
    notices, natives = [], []

    def visit(data, prefix="", depth=0):
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            for info in archive.infolist():
                name = prefix + info.filename
                if info.filename.endswith(".so"):
                    contents = archive.read(info)
                    natives.append({"entry": name, "size": info.file_size, "sha256": sha256(contents)})
                basename = Path(info.filename).name.lower()
                is_notice = re.match(r"(?:license|notice|copying|copyright)(?:[._-].*)?$", basename)
                if is_notice and not info.is_dir() and not basename.endswith((".class", ".jar")):
                    contents = archive.read(info)
                    saved = notice_dir / (sha256(contents) + ".txt")
                    saved.write_bytes(contents)  # retain bytes and attribution as shipped
                    notices.append({"entry": name, "size": info.file_size,
                                    "sha256": sha256(contents), "retained_file": str(saved)})
                if info.filename.endswith(".jar") and depth == 0:
                    visit(archive.read(info), name + "!/", depth + 1)

    data = path.read_bytes()
    visit(data)
    return {"file": str(path), "sha256": sha256(data), "size": len(data),
            "bundled_notices": notices, "native_libraries": natives}


def markdown(report, output):
    def relative(file):
        import os
        return os.path.relpath(file, output.parent)

    lines = ["# Third-party dependency notices", "",
             "Generated from the resolved configuration **" + report["configuration"] + "**.", "",
             "This inventory records declarations and bundled notices from exact versions. It does not assign new copyrights or change third-party licenses.", "",
             "Full Apache 2.0 license: [Apache-2.0.txt](licenses/Apache-2.0.txt). Project license: [GPL v3](../../LICENSE).", "",
             "| Resolved component | License declared in POM/parent | License evidence | Bundled notices |",
             "|---|---|---|---|"]
    for component in report["components"]:
        if component["classification"] != "runtime-binary":
            continue
        licenses = ", ".join("[" + l["name"].replace("|", "\\|") + "](" + l["url"] + ")" for l in component["licenses"]) or "**UNRESOLVED**"
        pom = component["pom"]
        evidence = "[exact POM](" + relative(pom["file"]) + ")" if pom else "missing"
        if pom and pom.get("parent_license_evidence"):
            evidence += ", [parent](" + relative(pom["parent_license_evidence"]["file"]) + ")"
        notices = [notice for artifact in component["artifacts"] for notice in artifact["bundled_notices"]]
        retained = ", ".join("[" + n["entry"].replace("|", "\\|") + "](" + relative(n["retained_file"]) + ")" for n in notices) or "none in inspected binary archives"
        lines.append("| `" + component["coordinate"] + "` | " + licenses + " | " + evidence + " | " + retained + " |")
    lines += ["", "Metadata/platform nodes are listed separately in the JSON inventory and do not imply bundled executable code.", "",
              "Archive scans include AAR files and nested JARs. Missing notice files do not prove that upstream source has no copyright notices; preserve upstream source notices when distributing source.", "",
              "Candidate status: regenerate against the final release classpath and inspect the final AAB plus generated APKs before submission.", ""]
    output.write_text("\n".join(lines), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--format", choices=("gradle", "lint-package"), default="gradle")
    parser.add_argument("--configuration", required=True)
    parser.add_argument("--cache", type=Path, default=Path.home() / ".gradle/caches/modules-2/files-2.1")
    parser.add_argument("--metadata-dir", type=Path, default=Path("docs/release/dependency-metadata"))
    parser.add_argument("--notice-dir", type=Path, default=Path("docs/release/licenses/bundled"))
    parser.add_argument("--output", type=Path, default=Path("docs/qa/checks/dependency-inventory.json"))
    parser.add_argument("--notices", type=Path, default=Path("docs/release/THIRD_PARTY_NOTICES.md"))
    args = parser.parse_args()
    for directory in (args.metadata_dir, args.notice_dir, args.output.parent, args.notices.parent):
        directory.mkdir(parents=True, exist_ok=True)
    components, issues = [], []
    for coordinate in resolved_coordinates(args.input, args.format):
        group, module, version = coordinate.split(":")
        files = sorted(p for p in (args.cache / group / module / version).glob("*/*")
                       if p.suffix in (".jar", ".aar") and not p.name.endswith(("-sources.jar", "-javadoc.jar")))
        pom, licenses, issue = pom_metadata(coordinate, args.cache, args.metadata_dir)
        if issue:
            issues.append({"coordinate": coordinate, "issue": issue})
        components.append({"coordinate": coordinate, "classification": "runtime-binary" if files else "metadata-only",
                           "pom": pom, "licenses": licenses,
                           "artifacts": [archive_inventory(p, args.notice_dir) for p in files]})
    report = {"generated_at_utc": datetime.now(timezone.utc).isoformat(),
              "configuration": args.configuration, "input": str(args.input), "input_sha256": sha256(args.input.read_bytes()),
              "components": components, "issues": issues,
              "scope": "Resolved dependency binaries and exact POM/parent declarations. Final APK/AAB contents and runtime are separate evidence."}
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    markdown(report, args.notices)
    print(json.dumps({"components": len(components), "runtime_binary_components": sum(c["classification"] == "runtime-binary" for c in components),
                      "issues": issues, "output": str(args.output), "notices": str(args.notices)}, indent=2))
    return 1 if issues else 0


if __name__ == "__main__":
    raise SystemExit(main())
