#!/usr/bin/env python3
"""Write a machine-readable exact-commit provenance manifest for verified CI artifacts."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Iterable

ROOT = Path(__file__).resolve().parents[1]


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def collect(patterns: Iterable[str]) -> list[dict[str, object]]:
    seen: set[Path] = set()
    artifacts: list[dict[str, object]] = []
    for pattern in patterns:
        for path in sorted(ROOT.glob(pattern)):
            if not path.is_file() or path in seen:
                continue
            seen.add(path)
            artifacts.append(
                {
                    "path": path.relative_to(ROOT).as_posix(),
                    "bytes": path.stat().st_size,
                    "sha256": sha256(path),
                }
            )
    return artifacts


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--ref", required=True)
    parser.add_argument("--run-number", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--repository", required=True)
    args = parser.parse_args()

    if args.ref != "refs/heads/Local":
        raise SystemExit("exact-commit Local package provenance requires the Local ref")

    package_root = ROOT / "dist/Local"
    package_info = package_root / "build-info.json"
    if not package_info.is_file():
        raise SystemExit("canonical Local build-info.json is missing")

    # The publicly downloadable files must be byte-for-byte copies of the
    # runtime modules packaged in the same Launcher source revision.
    groups = (
        ("apps/launcher/src-tauri/resources/core", "Paper-Plugins", 2),
        ("apps/launcher/src-tauri/resources/client-mods", "Fabric-Mods", 3),
    )
    for source_dir, output_dir, count in groups:
        source = ROOT / source_dir
        target = package_root / output_dir
        source_jars = sorted(source.glob("*.jar"))
        target_jars = sorted(target.glob("*.jar"))
        if len(source_jars) != count or {p.name for p in source_jars} != {p.name for p in target_jars}:
            raise SystemExit(f"incorrect or missing standalone runtime JARs in {output_dir}")
        for jar in source_jars:
            if sha256(jar) != sha256(target / jar.name):
                raise SystemExit(f"standalone JAR differs from Launcher bundled runtime: {jar.name}")

    artifacts = collect((
        "dist/Local/Paper-Plugins/*.jar",
        "dist/Local/Fabric-Mods/*.jar",
        "dist/Local/*.exe",
    ))
    installer = package_root / "LazyBuilder-Setup-Local.exe"
    if not installer.is_file():
        raise SystemExit("canonical Local installer is missing")
    published = json.loads(package_info.read_text(encoding="utf-8"))
    if published.get("commit") != args.commit or published.get("channel") != "local":
        raise SystemExit("Local package identity does not match CI commit/channel")
    actual = {
        str(path.relative_to(package_root)).replace("\\", "/"): path
        for path in package_root.rglob("*")
        if path.is_file() and path.suffix.lower() in {".exe", ".jar"}
    }
    entries = published.get("artifacts", [])
    if not isinstance(entries, list) or len(entries) != len(actual):
        raise SystemExit("Local package manifest has the wrong artifact count")
    recorded = {}
    for entry in entries:
        if not isinstance(entry, dict) or not isinstance(entry.get("file"), str):
            raise SystemExit("Local package manifest contains an invalid artifact entry")
        name = entry["file"]
        if name in recorded or name not in actual:
            raise SystemExit(f"Local package manifest has duplicate or unknown artifact: {name}")
        path = actual[name]
        if entry.get("sha256") != sha256(path) or entry.get("bytes") != path.stat().st_size:
            raise SystemExit(f"Local package manifest checksum/size mismatch: {name}")
        recorded[name] = path
    if set(recorded) != set(actual) or len(artifacts) != len(actual):
        raise SystemExit("Local package provenance does not cover every downloadable binary")

    manifest = {
        "schema": 1,
        "repository": args.repository,
        "ref": args.ref,
        "commit": args.commit,
        "ci": {
            "run_id": args.run_id,
            "run_number": args.run_number,
        },
        "targets": {
            "minecraft": "1.21.4",
            "java": "21",
        },
        "verification": {
            "repository_contract": "PASS",
            "version_contract": "PASS",
            "launcher_static_and_tests": "PASS",
            "paper_build_and_tests": "PASS",
            "paper_runtime_lifecycle": "PASS",
            "paper_restart_persistence": "PASS",
            "fabric_build": "PASS",
            "tauri_desktop_build": "PASS",
            "installer_smoke": "PASS",
        },
        "artifacts": artifacts,
        "proof_note": (
            "This manifest records exact-commit CI evidence. It does not upgrade "
            "build/package proof beyond the verification stages listed above."
        ),
    }

    output = ROOT / args.output
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote provenance: {output.relative_to(ROOT)}")
    print(f"Artifacts recorded: {len(artifacts)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
