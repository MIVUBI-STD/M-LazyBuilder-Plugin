#!/usr/bin/env python3
"""Verify crash-safe publication of Launcher-owned metadata files."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUST = ROOT / "apps" / "launcher" / "src-tauri" / "src" / "engine"


def require(path: Path, *markers: str) -> None:
    text = path.read_text(encoding="utf-8")
    missing = [marker for marker in markers if marker not in text]
    if missing:
        raise SystemExit(f"{path.relative_to(ROOT)} is missing metadata durability markers: {missing}")


def main() -> int:
    server_config = RUST / "server_config.rs"
    require(
        server_config,
        "recover_atomic_file(&path)?",
        "write_staging_file",
        "create_new(true)",
        "file.sync_all()",
        'with_extension("json.previous")',
        'with_extension("json.tmp")',
        "ensure_regular_metadata_file",
        "FILE_ATTRIBUTE_REPARSE_POINT",
        "interrupted_publish_prefers_previous_committed_config",
        "staging_config_recovers_when_no_committed_copy_exists",
    )

    print("Launcher metadata durability contract OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
