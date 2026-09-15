#!/usr/bin/env python3
"""Validate the published LazyBuilder updater manifest contract."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from urllib.parse import urlparse

ALLOWED_HOST = "github.com"
ALLOWED_REPO_PATH = "/halokaryamedia-source/LazyBuilder-Plugin/releases/download/"
VERSION_RE = re.compile(r"^\d+\.\d+\.\d+$")


def fail(message: str) -> None:
    raise SystemExit(message)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--version", default=None)
    args = parser.parse_args()

    try:
        payload = json.loads(args.manifest.read_text(encoding="utf-8"))
    except Exception as error:
        fail(f"Could not read updater manifest: {error}")

    if not isinstance(payload, dict):
        fail("Updater manifest must be a JSON object")

    version = str(payload.get("version", "")).strip()
    if not VERSION_RE.fullmatch(version):
        fail("Updater manifest version must be MAJOR.MINOR.PATCH")
    if args.version and args.version.lstrip("v") != version:
        fail(f"Updater manifest version {version} does not match requested version {args.version}")

    url = str(payload.get("url", "")).strip()
    parsed = urlparse(url)
    if parsed.scheme != "https" or parsed.netloc.lower() != ALLOWED_HOST:
        fail("Updater URL must use HTTPS on github.com")
    if not parsed.path.startswith(ALLOWED_REPO_PATH):
        fail("Updater URL must point to the canonical LazyBuilder GitHub release path")
    expected_tag = f"/launcher-v{version}/"
    if expected_tag not in parsed.path:
        fail("Updater URL release tag does not match manifest version")
    if not parsed.path.lower().endswith(".exe"):
        fail("Windows updater URL must point to an NSIS .exe installer")

    signature = str(payload.get("signature", "")).strip()
    if len(signature) < 40 or any(ch.isspace() for ch in signature):
        fail("Updater signature is missing or malformed")

    allowed = {"version", "url", "signature", "notes", "pub_date"}
    unexpected = sorted(set(payload) - allowed)
    if unexpected:
        fail(f"Updater manifest contains unexpected keys: {', '.join(unexpected)}")

    print(f"Launcher updater manifest OK for {version}")


if __name__ == "__main__":
    main()
