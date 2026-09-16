#!/usr/bin/env python3
"""Static invariants for LazyBuilder Launcher production-hardening work.

This is a source contract, not Windows/runtime proof. It prevents later edits from
silently bypassing recovery owners that already exist.
"""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LAUNCHER = ROOT / "apps" / "launcher"
RUST = LAUNCHER / "src-tauri" / "src"


def text(path: Path) -> str:
    if not path.is_file():
        raise SystemExit(f"required Launcher hardening file is missing: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def require(path: Path, *needles: str) -> None:
    content = text(path)
    missing = [needle for needle in needles if needle not in content]
    if missing:
        raise SystemExit(
            f"{path.relative_to(ROOT)} is missing Launcher hardening contract(s): {missing}"
        )


def forbid(path: Path, *needles: str) -> None:
    content = text(path)
    found = [needle for needle in needles if needle in content]
    if found:
        raise SystemExit(
            f"{path.relative_to(ROOT)} contains bypassed/deprecated hardening path(s): {found}"
        )


def main() -> int:
    engine_mod = RUST / "engine" / "mod.rs"
    commands_mod = RUST / "commands" / "mod.rs"
    bootstrap = RUST / "app_bootstrap.rs"
    startup = RUST / "engine" / "startup.rs"
    operations = RUST / "engine" / "operations.rs"
    instance = RUST / "engine" / "app_instance.rs"
    creation = RUST / "engine" / "workspace_creation.rs"
    creation_command = RUST / "commands" / "workspace_creation.rs"
    adoption = RUST / "engine" / "adoption.rs"
    registry = RUST / "engine" / "workspace_registry.rs"
    restore = RUST / "engine" / "server_restore.rs"
    backups = RUST / "engine" / "server_backups.rs"

    require(engine_mod, "pub mod workspace_creation;", "pub mod app_instance;", "pub mod app_data_migrations;")
    require(commands_mod, "pub mod workspace_creation;")

    require(
        bootstrap,
        "app_instance::acquire()",
        "app_data_migrations::initialize()",
        "OperationRegistry::initialize()",
        "commands::workspace_creation::workspace_create",
    )
    forbid(bootstrap, "commands::workspace::workspace_create,")

    require(
        startup,
        "workspace_creation::recover_pending_creations()",
        "adoption::recover_pending_adoptions()",
        "workspace_registry::recover_pending_duplicates()",
        "server_restore::recover_pending_restores()",
        "server_backups::recover_staging()",
        "server_process_guard::reconcile_registered_process_markers()",
    )

    require(
        operations,
        "OPERATION_JOURNAL_SCHEMA_VERSION",
        "operations.json",
        "persist_journal",
        "INTERRUPTED_LAUNCHER_OPERATION",
        "RecoveryRequired",
    )
    require(instance, "launcher-instance.json", "previous_session_unclean")

    require(
        creation,
        "pending-creations.json",
        "CREATE_RECOVERY_REQUIRED",
        "recover_pending_creations",
        "find_registered_workspace_id",
    )
    require(
        creation_command,
        'begin_exclusive("create-server"',
        "CREATE_RECOVERY_REQUIRED",
        "ensure_no_running_paper_except(None)",
    )

    require(adoption, "pending-adoptions.json", "ADOPTION_RECOVERY_REQUIRED", "recover_pending_adoptions")
    require(registry, "pending-duplicates.json", "DUPLICATE_RECOVERY_REQUIRED", "recover_pending_duplicates")
    require(restore, "pending-restores.json", "recover_pending_restores")

    require(
        backups,
        "BACKUP_SCHEMA_VERSION: u32 = 2",
        "sha256",
        "BackupIntegrityStatus { Verified, LegacyUnverified }",
        "pub fn verify(",
        "verify_snapshot_integrity",
    )

    print("Launcher production-hardening source contract OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
