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
    app_data = RUST / "app_data_migrations.rs"
    operations = RUST / "operations.rs"
    server_config = RUST / "server_config.rs"
    launcher_settings = RUST / "launcher_settings.rs"
    atomic_json = RUST / "persistence" / "atomic_json.rs"
    workspace_registry = RUST / "workspace_registry.rs"
    workspace_creation = RUST / "workspace_creation.rs"
    adoption = RUST / "adoption.rs"
    server_restore = RUST / "server_restore.rs"
    backup_recovery = RUST / "backup_recovery.rs"

    # App-data migration owns a migration transaction rather than normal runtime
    # metadata, so its dedicated recovery journal remains intentionally separate.
    require(
        app_data,
        "recover_manifest_file(&path)?",
        "cleanup_manifest_recovery_files(&path)?",
        "create_new(true)",
        "file.sync_all()",
        'with_extension("json.previous")',
        'with_extension("json.incoming")',
        "metadata_entry_exists",
        "ensure_regular_metadata_file",
        "FILE_ATTRIBUTE_REPARSE_POINT",
        "app_data_manifest_recovery_prefers_previous_committed_copy",
        "app_data_manifest_staging_recovers_when_no_committed_copy_exists",
        "existing_manifest_preserves_recovery_evidence_until_validation",
    )

    # Ordinary Launcher-owned JSON metadata has one publication/recovery owner.
    require(
        atomic_json,
        "MAX_ATOMIC_JSON_BYTES",
        "OpenOptions::new()",
        ".create_new(true)",
        "file.sync_all()",
        "safe_path::entry_exists",
        "safe_path::ensure_regular_file",
        "safe_path::remove_regular_file_if_present",
        'path.with_extension("json.tmp")',
        'path.with_extension("json.previous")',
        'path.with_extension("json.incoming")',
        "recovery is ambiguous",
        "legacy_incoming_recovers_when_no_canonical_copy_exists",
        "dual_staging_is_preserved_as_ambiguous",
        "oversized_atomic_json_is_rejected_before_parse",
    )

    delegated = (
        ("operation journal", operations, "operations.json"),
        ("server configuration", server_config, "server-manager.json"),
        ("launcher settings", launcher_settings, "settings.json"),
        ("workspace registry", workspace_registry, "workspaces.json"),
        ("creation recovery", workspace_creation, "pending-creations.json"),
        ("adoption recovery", adoption, "pending-adoptions.json"),
        ("restore recovery", server_restore, "pending-restores.json"),
        ("backup recovery", backup_recovery, "pending-backups.json"),
    )
    for label, source, filename in delegated:
        require(
            source,
            filename,
            "persistence::recover_atomic_file",
            "persistence::read_json",
            "persistence::write_json_atomically",
        )

    require(
        operations,
        "OPERATION_JOURNAL_SCHEMA_VERSION",
        "MAX_OPERATION_HISTORY: usize = 100",
        "operation_journal_recovery_prefers_previous_committed_copy",
        "operation_journal_incoming_recovers_without_committed_copy",
        "malformed_operation_journal_preserves_recovery_evidence",
        "oversized_operation_journal_is_rejected_before_parse",
    )

    require(
        server_config,
        "interrupted_publish_prefers_previous_committed_config",
        "staging_config_recovers_when_no_committed_copy_exists",
        "malformed_main_config_preserves_recovery_evidence",
    )

    require(
        launcher_settings,
        "interrupted_settings_publish_prefers_previous_committed_copy",
        "settings_staging_recovers_when_no_committed_copy_exists",
        "malformed_main_settings_preserve_recovery_evidence",
    )

    require(
        workspace_registry,
        "pending-deletions.json",
        "pending-duplicates.json",
        "pub fn manifest(root: &Path)",
        "pub fn update_paper_build(root: &Path",
        "pub fn update_core_versions(root: &Path",
        "validate_manifest(&manifest)?;",
        "workspace_metadata_recovery_prefers_previous_committed_copy",
        "workspace_metadata_recovery_supports_legacy_tmp_staging",
        "workspace_metadata_recovery_preserves_ambiguous_staging",
        "workspace_metadata_preserves_recovery_evidence_until_validation",
        "workspace_manifest_semantic_validation_preserves_recovery_evidence",
        "oversized_workspace_metadata_is_rejected_before_parse",
    )

    require(
        workspace_creation,
        "creation_intent_recovery_prefers_previous_committed_copy",
        "creation_intent_incoming_recovers_without_committed_copy",
        "malformed_creation_intent_preserves_recovery_evidence",
    )
    require(
        adoption,
        "adoption_intent_recovery_prefers_previous_committed_copy",
        "adoption_intent_incoming_recovers_without_committed_copy",
        "malformed_adoption_intent_preserves_recovery_evidence",
    )
    require(
        server_restore,
        "restore_intent_recovery_prefers_previous_committed_copy",
        "restore_intent_incoming_recovers_without_committed_copy",
        "malformed_restore_intent_preserves_recovery_evidence",
    )

    for label, source in (
        ("operation journal", operations),
        ("workspace registry", workspace_registry),
        ("creation recovery", workspace_creation),
        ("adoption recovery", adoption),
        ("restore recovery", server_restore),
        ("backup recovery", backup_recovery),
    ):
        text = source.read_text(encoding="utf-8")
        forbidden = (
            "fn replace_journal_file",
            "fn replace_json_file",
            "fn replace_pending_creation_file",
            "fn replace_pending_adoption_file",
            "fn replace_pending_restore_file",
            "fn atomic_write_json",
        )
        leaked = [marker for marker in forbidden if marker in text]
        if leaked:
            raise SystemExit(
                f"{source.relative_to(ROOT)} restored duplicate JSON persistence owner(s): {leaked}"
            )

    print("Launcher metadata durability contract OK (canonical persistence ownership)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
