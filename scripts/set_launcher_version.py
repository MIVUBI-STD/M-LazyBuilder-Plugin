#!/usr/bin/env python3
"""Set the LazyBuilder Launcher version in every canonical source/lockfile owner."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LAUNCHER = ROOT / "apps" / "launcher"
VERSION_RE = re.compile(r"^\d+\.\d+\.\d+$")


def validate_version(value: str) -> str:
    value = value.strip().lstrip("v")
    if not VERSION_RE.fullmatch(value):
        raise argparse.ArgumentTypeError("version must be MAJOR.MINOR.PATCH")
    return value


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def replace_package_version(text: str, package_name: str, version: str) -> str:
    pattern = re.compile(
        rf'(?ms)(\[\[package\]\]\s*\nname\s*=\s*"{re.escape(package_name)}"\s*\nversion\s*=\s*")([^"]+)(")'
    )
    updated, count = pattern.subn(rf"\g<1>{version}\g<3>", text, count=1)
    if count != 1:
        raise SystemExit(f"Could not find {package_name} package version in Cargo.lock")
    return updated


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("version", type=validate_version)
    args = parser.parse_args()
    version = args.version

    package_path = LAUNCHER / "package.json"
    package = json.loads(package_path.read_text(encoding="utf-8"))
    package["version"] = version
    write_json(package_path, package)

    lock_path = LAUNCHER / "package-lock.json"
    lock = json.loads(lock_path.read_text(encoding="utf-8"))
    lock["version"] = version
    root_package = lock.get("packages", {}).get("")
    if not isinstance(root_package, dict):
        raise SystemExit("package-lock.json is missing the root package entry")
    root_package["version"] = version
    write_json(lock_path, lock)

    cargo_path = LAUNCHER / "src-tauri" / "Cargo.toml"
    cargo = cargo_path.read_text(encoding="utf-8")
    package_section = re.search(r"(?ms)^\[package\]\s*(.*?)(?=^\[|\Z)", cargo)
    if not package_section:
        raise SystemExit("Cargo.toml has no [package] section")
    section = package_section.group(0)
    updated_section, count = re.subn(
        r'(?m)^version\s*=\s*"[^"]+"', f'version = "{version}"', section, count=1
    )
    if count != 1:
        raise SystemExit("Cargo.toml package version is missing")
    cargo = cargo[: package_section.start()] + updated_section + cargo[package_section.end() :]
    cargo_path.write_text(cargo, encoding="utf-8")

    cargo_lock_path = LAUNCHER / "src-tauri" / "Cargo.lock"
    cargo_lock = cargo_lock_path.read_text(encoding="utf-8")
    cargo_lock_path.write_text(replace_package_version(cargo_lock, "lazybuilder", version), encoding="utf-8")

    tauri_path = LAUNCHER / "src-tauri" / "tauri.conf.json"
    tauri = json.loads(tauri_path.read_text(encoding="utf-8"))
    tauri["version"] = version
    write_json(tauri_path, tauri)

    print(f"LazyBuilder Launcher version set to {version}")


if __name__ == "__main__":
    main()
