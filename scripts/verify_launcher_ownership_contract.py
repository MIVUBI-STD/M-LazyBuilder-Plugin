#!/usr/bin/env python3
"""Verify that the documented Launcher authority map stays anchored to real source owners."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / "docs" / "launcher" / "PRODUCTION_OWNERSHIP.md"

REQUIRED_OWNERS = [
    "apps/launcher/src-tauri/src/engine/app_instance.rs",
    "apps/launcher/src-tauri/src/engine/app_data_migrations.rs",
    "apps/launcher/src-tauri/src/engine/operations.rs",
    "apps/launcher/src-tauri/src/engine/workspace_registry.rs",
    "apps/launcher/src-tauri/src/engine/workspace_creation.rs",
    "apps/launcher/src-tauri/src/engine/adoption.rs",
    "apps/launcher/src-tauri/src/engine/server_backups.rs",
    "apps/launcher/src-tauri/src/engine/backup_recovery.rs",
    "apps/launcher/src-tauri/src/engine/server_restore.rs",
    "apps/launcher/src-tauri/src/engine/server_process_guard.rs",
    "apps/launcher/src-tauri/src/engine/server_health.rs",
    "apps/launcher/src-tauri/src/engine/server_repair.rs",
    "apps/launcher/src-tauri/src/engine/launcher_settings.rs",
    "apps/launcher/src-tauri/src/engine/diagnostics.rs",
    "apps/launcher/src-tauri/src/engine/privacy_redaction.rs",
    "apps/launcher/src-tauri/src/engine/support_bundle.rs",
    "apps/launcher/src-tauri/src/engine/plugin_ingress.rs",
    "apps/launcher/src-tauri/src/engine/plugin_manager/mod.rs",
    "apps/launcher/src/app/closeGuard.ts",
    "apps/launcher/src/app/modalAccessibility.ts",
    "apps/launcher/src/pages/Worlds.svelte",
]

REQUIRED_DOC_MARKERS = [
    "one domain authority",
    "pending-backups.json",
    "one-time legacy Backup sweep",
    "privacy_redaction::RedactionPolicy",
    "World Manager backend",
    "source-implemented",
    "CI-proven",
    "packaged-Windows-proven",
    "Local-PC-proven",
]


def main() -> int:
    errors: list[str] = []
    if not DOC.is_file():
        errors.append("production ownership document is missing")
        text = ""
    else:
        text = DOC.read_text(encoding="utf-8")

    for marker in REQUIRED_DOC_MARKERS:
        if marker not in text:
            errors.append(f"ownership document is missing marker: {marker}")

    for relative in REQUIRED_OWNERS:
        path = ROOT / relative
        if not path.is_file():
            errors.append(f"documented Launcher authority owner does not exist: {relative}")
        short = relative.removeprefix("apps/launcher/src-tauri/src/").removeprefix("apps/launcher/src/")
        if short not in text:
            errors.append(f"ownership document no longer names source owner: {short}")

    forbidden = [
        "frontend operation queue/history",
        "second workspace database/index",
        "file-by-file overwrite restore",
        "process-name-only ownership checks",
        "frontend preference store",
        "automatic upload",
        "frontend durable task registry",
        "ad-hoc EXE download/update path",
    ]
    for marker in forbidden:
        if marker not in text:
            errors.append(f"ownership document lost explicit anti-duplication guard: {marker}")

    # Create Server has one command boundary only. The generic workspace command file
    # must never reintroduce the pre-transaction direct workspace_registry::create path.
    bootstrap = (ROOT / "apps/launcher/src-tauri/src/app_bootstrap.rs").read_text(encoding="utf-8")
    workspace_commands = (ROOT / "apps/launcher/src-tauri/src/commands/workspace.rs").read_text(encoding="utf-8")
    creation_commands = (ROOT / "apps/launcher/src-tauri/src/commands/workspace_creation.rs").read_text(encoding="utf-8")
    if "commands::workspace_creation::workspace_create" not in bootstrap:
        errors.append("bootstrap no longer registers the crash-safe Create Server command owner")
    if "pub fn workspace_create(" in workspace_commands:
        errors.append("legacy workspace::workspace_create command authority was reintroduced")
    if "workspace_creation::create" not in creation_commands or 'begin_exclusive("create-server"' not in creation_commands:
        errors.append("crash-safe workspace_creation command no longer owns Create Server")

    if errors:
        print("Launcher ownership contract verification failed:")
        for error in errors:
            print(f" - {error}")
        return 1

    print(f"Launcher ownership contract OK ({len(REQUIRED_OWNERS)} canonical owners)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
