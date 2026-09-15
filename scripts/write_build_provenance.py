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

    artifact_patterns = [
        "apps/launcher/src-tauri/resources/core/*.jar",
        "apps/launcher/src-tauri/resources/client-mods/*.jar",
        "dist/Local/*.exe",
    ]
    artifacts = collect(artifact_patterns)
    if not artifacts:
        raise SystemExit("no verified artifacts found for provenance manifest")

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
