#!/usr/bin/env python3
"""Static contract checks for LazyBuilder Launcher release/update packaging."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LAUNCHER = ROOT / "apps" / "launcher"


def read_cargo_version(path: Path) -> str:
    text = path.read_text(encoding="utf-8")
    package = re.search(r"(?ms)^\[package\]\s*(.*?)(?=^\[|\Z)", text)
    if not package:
        raise SystemExit("Cargo.toml has no [package] section")
    match = re.search(r'^version\s*=\s*"([^"]+)"', package.group(1), re.M)
    if not match:
        raise SystemExit("Cargo.toml package version is missing")
    return match.group(1)


def read_cargo_lock_version(path: Path, package_name: str) -> str:
    text = path.read_text(encoding="utf-8")
    pattern = re.compile(
        rf'(?ms)^\[\[package\]\]\s*\nname\s*=\s*"{re.escape(package_name)}"\s*\nversion\s*=\s*"([^"]+)"'
    )
    match = pattern.search(text)
    if not match:
        raise SystemExit(f"Cargo.lock has no {package_name} package entry")
    return match.group(1)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", default=None)
    args = parser.parse_args()

    package = json.loads((LAUNCHER / "package.json").read_text(encoding="utf-8"))
    package_lock = json.loads((LAUNCHER / "package-lock.json").read_text(encoding="utf-8"))
    tauri = json.loads((LAUNCHER / "src-tauri" / "tauri.conf.json").read_text(encoding="utf-8"))
    release = json.loads((LAUNCHER / "src-tauri" / "tauri.release.conf.json").read_text(encoding="utf-8"))
    cargo_version = read_cargo_version(LAUNCHER / "src-tauri" / "Cargo.toml")
    cargo_lock_version = read_cargo_lock_version(LAUNCHER / "src-tauri" / "Cargo.lock", "lazybuilder")
    npm_root = package_lock.get("packages", {}).get("", {})

    versions = {
        "package.json": str(package.get("version", "")),
        "package-lock.json": str(package_lock.get("version", "")),
        "package-lock.json root": str(npm_root.get("version", "")),
        "tauri.conf.json": str(tauri.get("version", "")),
        "Cargo.toml": cargo_version,
        "Cargo.lock": cargo_lock_version,
    }
    if len(set(versions.values())) != 1:
        raise SystemExit(f"Launcher versions are not synchronized: {versions}")
    version = next(iter(versions.values()))
    if args.version and args.version.lstrip("v") != version:
        raise SystemExit(f"Requested release {args.version} does not match Launcher source version {version}")

    if release.get("bundle", {}).get("createUpdaterArtifacts") is not True:
        raise SystemExit("tauri.release.conf.json must enable bundle.createUpdaterArtifacts=true")

    if tauri.get("bundle", {}).get("createUpdaterArtifacts"):
        raise SystemExit("Normal tauri.conf.json must not require updater signing for developer builds")

    forbidden_names = {"private.key", "updater.key", "tauri.key", "minisign.key"}
    tracked_candidates = [path for path in ROOT.rglob("*") if path.is_file() and path.name.lower() in forbidden_names]
    if tracked_candidates:
        raise SystemExit("Private updater key material must never be stored in the repository: " + ", ".join(map(str, tracked_candidates)))

    for path in ROOT.rglob("*"):
        if not path.is_file() or path.suffix.lower() not in {".md", ".json", ".yml", ".yaml", ".toml", ".py", ".ps1", ".txt"}:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        if "TAURI_SIGNING_PRIVATE_KEY=" in text and "secrets.TAURI_SIGNING_PRIVATE_KEY" not in text:
            raise SystemExit(f"Potential literal updater private key assignment found in {path}")

    print(f"Launcher release contract OK for {version}")


if __name__ == "__main__":
    main()
