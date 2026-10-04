#!/usr/bin/env python3
"""Inspect actual APK/AAB contents and native alignment; runtime remains separate."""
import argparse
import hashlib
import json
import struct
import zipfile
from datetime import datetime, timezone
from pathlib import Path

PAGE_SIZE = 16384


def elf_alignment(data):
    if len(data) < 52 or data[:4] != b"\x7fELF":
        return {"error": "not a complete ELF header", "compatible_16kb": False}
    elf_class, encoding = data[4], data[5]
    if elf_class not in (1, 2) or encoding not in (1, 2):
        return {"error": "unsupported ELF class/encoding", "compatible_16kb": False}
    endian = "<" if encoding == 1 else ">"
    try:
        if elf_class == 1:
            phoff = struct.unpack_from(endian + "I", data, 28)[0]
            phsize, phcount = struct.unpack_from(endian + "HH", data, 42)
            expected_size = 32
        else:
            phoff = struct.unpack_from(endian + "Q", data, 32)[0]
            phsize, phcount = struct.unpack_from(endian + "HH", data, 54)
            expected_size = 56
        if phsize < expected_size or phcount == 65535 or phoff + phsize * phcount > len(data):
            raise ValueError("invalid or extended ELF program-header table")
        segments = []
        for i in range(phcount):
            pos = phoff + i * phsize
            if struct.unpack_from(endian + "I", data, pos)[0] != 1:  # PT_LOAD
                continue
            if elf_class == 1:
                offset, vaddr = struct.unpack_from(endian + "II", data, pos + 4)
                alignment = struct.unpack_from(endian + "I", data, pos + 28)[0]
            else:
                offset, vaddr = struct.unpack_from(endian + "QQ", data, pos + 8)
                alignment = struct.unpack_from(endian + "Q", data, pos + 48)[0]
            compatible = (
                alignment >= PAGE_SIZE
                and alignment & (alignment - 1) == 0
                and (vaddr - offset) % PAGE_SIZE == 0
            )
            segments.append({"offset": offset, "virtual_address": vaddr,
                             "alignment": alignment, "compatible_16kb": compatible})
        return {"class_bits": 32 if elf_class == 1 else 64,
                "load_segments": segments,
                "compatible_16kb": bool(segments) and all(s["compatible_16kb"] for s in segments)}
    except (struct.error, ValueError) as error:
        return {"error": str(error), "compatible_16kb": False}


def zip_data_offset(raw, info):
    raw.seek(info.header_offset)
    header = raw.read(30)
    if len(header) != 30 or header[:4] != b"PK\x03\x04":
        raise ValueError("invalid local ZIP header")
    filename_size, extra_size = struct.unpack_from("<HH", header, 26)
    return info.header_offset + 30 + filename_size + extra_size


def audit(artifact):
    is_bundle = artifact.suffix.lower() == ".aab"
    natives = []
    with zipfile.ZipFile(artifact) as archive, artifact.open("rb") as raw:
        for info in archive.infolist():
            if not info.filename.endswith(".so"):
                continue
            data = archive.read(info)
            offset = zip_data_offset(raw, info)
            direct_map = not is_bundle and info.compress_type == zipfile.ZIP_STORED
            natives.append({
                "name": info.filename, "size": info.file_size,
                "sha256": hashlib.sha256(data).hexdigest(),
                "zip_compression": info.compress_type, "zip_data_offset": offset,
                "zip_16kb_alignment_required": direct_map,
                "zip_16kb_aligned": offset % PAGE_SIZE == 0 if direct_map else None,
                "elf": elf_alignment(data),
            })
        license_names = [n for n in archive.namelist()
                         if any(word in Path(n).name.lower()
                                for word in ("license", "notice", "copying", "copyright"))]
        metadata_name = "META-INF/com/android/build/gradle/app-metadata.properties"
        metadata = archive.read(metadata_name).decode("utf-8", errors="replace") if metadata_name in archive.namelist() else None
    alignment_ok = all(n["elf"]["compatible_16kb"] and n["zip_16kb_aligned"] is not False for n in natives)
    return {
        "checked_at_utc": datetime.now(timezone.utc).isoformat(),
        "artifact": str(artifact), "artifact_kind": "aab" if is_bundle else "apk",
        "size_bytes": artifact.stat().st_size,
        "sha256": hashlib.sha256(artifact.read_bytes()).hexdigest(),
        "native_libraries": natives, "bundled_license_notice_entries": license_names,
        "agp_app_metadata": metadata,
        "native_alignment_verification": "not applicable: no bundled .so" if not natives else "passed" if alignment_ok else "failed",
        "native_alignment_pass": alignment_ok,
        "generated_apk_zip_alignment": "pending: inspect APKs generated from this AAB" if is_bundle else "checked for uncompressed .so",
        "runtime_16kb": "pending: getconf PAGE_SIZE must return 16384, then install this artifact and run smoke/journey/PDF evidence",
        "scope": "Packaging/ELF checks only. No proof of runtime, signatures, manifest policy, or Play acceptance.",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("artifact", type=Path)
    parser.add_argument("--require-native-alignment", action="store_true")
    args = parser.parse_args()
    result = audit(args.artifact)
    print(json.dumps(result, indent=2))
    return 1 if args.require_native_alignment and not result["native_alignment_pass"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
