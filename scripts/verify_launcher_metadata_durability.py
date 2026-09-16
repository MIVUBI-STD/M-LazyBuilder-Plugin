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
    server_config = RUST / "server_config.rs"
    launcher_settings = RUST / "launcher_settings.rs"
    workspace_registry = RUST / "workspace_registry.rs"

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

    require(
        server_config,
        "recover_atomic_file(&path)?",
        "cleanup_recovery_files(&path)?",
        "write_staging_file",
        "create_new(true)",
        "file.sync_all()",
        'with_extension("json.previous")',
        'with_extension("json.tmp")',
        "metadata_entry_exists",
        "ensure_regular_metadata_file",
        "FILE_ATTRIBUTE_REPARSE_POINT",
        "interrupted_publish_prefers_previous_committed_config",
        "staging_config_recovers_when_no_committed_copy_exists",
        "malformed_main_config_preserves_recovery_evidence",
    )

    require(
        launcher_settings,
        "recover_atomic_file(&path)?",
        "cleanup_recovery_files(&path)?",
        "write_staging_file",
        "create_new(true)",
        "file.sync_all()",
        'with_extension("json.previous")',
        'with_extension("json.tmp")',
        "metadata_entry_exists",
        "ensure_regular_metadata_file",
        "FILE_ATTRIBUTE_REPARSE_POINT",
        "interrupted_settings_publish_prefers_previous_committed_copy",
        "settings_staging_recovers_when_no_committed_copy_exists",
        "malformed_main_settings_preserve_recovery_evidence",
    )

    require(
        workspace_registry,
        "recover_json_file(&path, \"workspace registry\")?",
        "recover_json_file(&path, \"pending server deletions\")?",
        "recover_json_file(&path, \"pending server duplicates\")?",
        "recover_json_file(&path, \"workspace manifest\")?",
        "write_json_file",
        "file.sync_all()",
        'with_extension("json.previous")',
        'with_extension("json.incoming")',
        'with_extension("json.tmp")',
        "metadata_entry_exists",
        "ensure_regular_metadata_file",
        "FILE_ATTRIBUTE_REPARSE_POINT",
        "ambiguous {label} recovery staging files",
        "workspace_metadata_recovery_prefers_previous_committed_copy",
        "workspace_metadata_recovery_supports_legacy_tmp_staging",
        "workspace_metadata_recovery_preserves_ambiguous_staging",
        "let workspace_created = match read_manifest(&root)?",
    )

    print("Launcher metadata durability contract OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
