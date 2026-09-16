#!/usr/bin/env python3
"""Verify destructive/long-running Launcher flows stay visible in OperationRegistry."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUST = ROOT / "apps" / "launcher" / "src-tauri" / "src"


def require(path: Path, *markers: str) -> None:
    text = path.read_text(encoding="utf-8")
    missing = [marker for marker in markers if marker not in text]
    if missing:
        raise SystemExit(f"{path.relative_to(ROOT)} is missing tracked-operation markers: {missing}")


def main() -> int:
    workspace = RUST / "commands" / "workspace.rs"
    creation = RUST / "commands" / "workspace_creation.rs"
    backups = RUST / "commands" / "server_backups.rs"
    repair = RUST / "commands" / "server_health.rs"
    diagnostics = RUST / "commands" / "diagnostics.rs"

    require(creation, 'begin_exclusive("create-server"')
    require(
        workspace,
        'begin_exclusive("provision-server"',
        'begin_exclusive("update-paper"',
        'begin_exclusive("adopt-server"',
        'begin_exclusive("duplicate-server"',
        'begin_exclusive("delete-server"',
        '"DELETE_RECOVERY_REQUIRED"',
    )
    require(backups, 'begin_exclusive("backup-server"', 'begin_exclusive("restore-server"')
    require(repair, 'begin_exclusive("repair-server"')
    require(diagnostics, 'begin_exclusive("export-support-bundle"')

    # World operations intentionally use the World Manager backend task authority
    # rather than duplicating those tasks into Launcher OperationRegistry.
    world_commands = (RUST / "commands" / "world_manager.rs").read_text(encoding="utf-8")
    if "world_task" not in world_commands:
        raise SystemExit("World Manager command surface no longer exposes backend task snapshots")

    print("Launcher tracked-operation coverage OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
