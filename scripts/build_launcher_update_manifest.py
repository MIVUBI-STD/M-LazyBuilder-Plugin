#!/usr/bin/env python3
"""Build the static JSON consumed by the Tauri v2 updater.

The signature is read from Tauri's generated .sig file. The private signing key
never enters this script, the repository, or release artifacts.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from urllib.parse import urlparse


def semver(value: str) -> str:
    value = value.strip()
    if value.startswith("v"):
        value = value[1:]
    parts = value.split(".")
    if len(parts) != 3 or not all(part.isdigit() for part in parts):
        raise argparse.ArgumentTypeError("version must be MAJOR.MINOR.PATCH")
    return value


def https_url(value: str) -> str:
    parsed = urlparse(value)
    if parsed.scheme != "https" or not parsed.netloc:
        raise argparse.ArgumentTypeError("update URL must be HTTPS")
    return value


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", required=True, type=semver)
    parser.add_argument("--url", required=True, type=https_url)
    parser.add_argument("--signature-file", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--notes", default="")
    parser.add_argument("--pub-date", default=None)
    args = parser.parse_args()

    signature = args.signature_file.read_text(encoding="utf-8").strip()
    if not signature:
        raise SystemExit("signature file is empty")

    payload: dict[str, str] = {
        "version": args.version,
        "url": args.url,
        "signature": signature,
    }
    if args.notes.strip():
        payload["notes"] = args.notes.strip()
    if args.pub_date:
        payload["pub_date"] = args.pub_date

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
