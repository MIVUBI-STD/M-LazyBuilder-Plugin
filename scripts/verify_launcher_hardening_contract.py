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
    workspace_commands = RUST / "commands" / "workspace.rs"
    adoption = RUST / "engine" / "adoption.rs"
    registry = RUST / "engine" / "workspace_registry.rs"
    restore = RUST / "engine" / "server_restore.rs"
    backups = RUST / "engine" / "server_backups.rs"
    backup_recovery = RUST / "engine" / "backup_recovery.rs"
    backup_commands = RUST / "commands" / "server_backups.rs"
    storage = RUST / "engine" / "storage_health.rs"
    health = RUST / "engine" / "server_health.rs"
    plugin_ingress = RUST / "engine" / "plugin_ingress.rs"
    plugin_commands = RUST / "commands" / "plugin_manager.rs"
    server_commands = RUST / "commands" / "server_manager.rs"
    java_runtime = RUST / "engine" / "java_runtime.rs"
    world_manager = RUST / "engine" / "world_manager" / "mod.rs"
    privacy_redaction = RUST / "engine" / "privacy_redaction.rs"
    support_bundle = RUST / "engine" / "support_bundle.rs"
    app = LAUNCHER / "src" / "App.svelte"
    close_guard = LAUNCHER / "src" / "app" / "closeGuard.ts"
    modal_accessibility = LAUNCHER / "src" / "app" / "modalAccessibility.ts"
    frontend_bootstrap = LAUNCHER / "src" / "main.ts"
    activity = LAUNCHER / "src" / "pages" / "Activity.svelte"
    dashboard = LAUNCHER / "src" / "pages" / "Dashboard.svelte"
    worlds = LAUNCHER / "src" / "pages" / "Worlds.svelte"
    backup_panel = LAUNCHER / "src" / "pages" / "BackupPanel.svelte"
    app_css = LAUNCHER / "src" / "styles" / "app.css"

    require(
        engine_mod,
        "pub mod workspace_creation;",
        "pub mod app_instance;",
        "pub mod app_data_migrations;",
        "pub mod backup_recovery;",
        "pub mod privacy_redaction;",
        "pub mod storage_health;",
        "pub mod plugin_ingress;",
    )
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
        "backup_recovery::recover_pending()",
        "backup_recovery::legacy_sweep_required()",
        "server_backups::recover_staging()",
        "backup_recovery::mark_legacy_sweep_complete()",
        "server_process_guard::reconcile_registered_process_markers()",
    )

    require(
        operations,
        "MAX_OPERATION_HISTORY: usize = 100",
        "OPERATION_JOURNAL_SCHEMA_VERSION",
        'WORKSPACE_LIBRARY_RESOURCE: &str = "workspace-library"',
        'WORKSPACE_RESOURCE_PREFIX: &str = "workspace:"',
        "resources_conflict(resource, &entry.resource)",
        "fn resources_conflict(",
        "workspace_library_conflicts_with_workspace_resources",
        "library_and_workspace_exclusive_operations_are_serialized",
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
    require(
        workspace_commands,
        'begin_exclusive("delete-server"',
        '"deleting"',
        '"DELETE_FAILED"',
        '"DELETE_RECOVERY_REQUIRED"',
        "operations.require_recovery",
        "workspace_registry::delete(&id, &typed_display_name)",
    )

    require(
        adoption,
        "pending-adoptions.json",
        "ADOPTION_RECOVERY_REQUIRED",
        "recover_pending_adoptions",
        "reject_adoption_source_trees(&intent)?",
        "fn reject_tree_links(",
        "is_reparse_point(&metadata)",
    )
    require(registry, "pending-duplicates.json", "DUPLICATE_RECOVERY_REQUIRED", "recover_pending_duplicates", "pending-deletions.json", "recover_pending_deletions")
    require(restore, "pending-restores.json", "recover_pending_restores")

    require(
        backups,
        "BACKUP_SCHEMA_VERSION: u32 = 2",
        "sha256",
        "BackupIntegrityStatus { Verified, LegacyUnverified }",
        "pub fn verify(",
        "verify_snapshot_integrity",
    )
    require(
        backup_recovery,
        "pending-backups.json",
        "backup-recovery-index-v1.json",
        "pub fn begin(",
        "pub fn recover_workspace(",
        "pub fn recover_pending(",
        "legacy_sweep_required",
        "mark_legacy_sweep_complete",
        "read_pending_file",
        "existing_regular_file",
        "prefer the previous committed copy",
        "STAGING_PREFIX",
    )
    require(
        backup_commands,
        "backup_recovery::begin(&workspace_id)",
        "backup_recovery::recover_workspace(&workspace_id)",
        "MIN_PROGRESS_JOURNAL_STEP_BYTES",
        "DurableProgressReporter",
        "should_persist_progress",
        "progress_journal_updates_are_bounded_for_large_copies",
    )

    require(
        storage,
        "CRITICAL_AVAILABLE_BYTES",
        "WARNING_MIN_AVAILABLE_BYTES",
        "StoragePressure { Normal, Warning, Critical, Unknown }",
        "inspect_workspace",
    )
    require(health, '"storage-capacity"', "StoragePressure::Critical", "ServerHealthState::NeedsAttention")

    require(
        plugin_ingress,
        "MAX_PLUGIN_JAR_BYTES",
        "MAX_PLUGIN_ARCHIVE_ENTRIES",
        "MAX_PLUGIN_METADATA_BYTES",
        "stage_selected_jar",
        "recover_stale_ingress",
        "PluginIngressLease",
        "is_reparse_point(&metadata)",
    )
    require(
        plugin_commands,
        "plugin_ingress::stage_selected_jar(Path::new(&jar_path))?;",
        "plugins.install(&staged_path)",
        "plugins.update(&plugin_id, &staged_path)",
    )

    require(
        server_commands,
        "ensure_world_control_port_available()?;",
        "TcpListener::bind((\"127.0.0.1\", port))",
        "occupied_world_control_port_is_rejected_before_paper_start",
    )

    require(java_runtime, ".enclosed_name()", "failed SHA-256 verification")
    require(world_manager, '"X-LazyBuilder-Sha256"', "sha256_file(&path)?", '"Content-Length"')
    require(
        privacy_redaction,
        "pub struct RedactionPolicy",
        "for_support_bundle",
        "add_path_variants",
        "serde_json::to_string(value)",
        "json_escaped_windows_paths_are_redacted",
    )
    require(
        support_bundle,
        "privacy_redaction::RedactionPolicy",
        "RedactionPolicy::for_support_bundle",
        "redaction.redact(&text)",
        '"diagnostics.json"',
        '"operations.json"',
        '"startup.json"',
        '"README.txt"',
        "MAX_LOG_FILE_BYTES",
        "sync_all()",
    )
    forbid(support_bundle, "fn redact(", "fn replace_case_insensitive(")

    require(
        close_guard,
        "ACTIVE_OPERATION_STATES",
        "runtimeProduct.operations.list()",
        "Closing now will interrupt the operation and may require recovery",
        "RUNNING_SERVER_STATES",
    )
    require(app, "installLauncherCloseGuard", "closeGuardUnlisten = await installLauncherCloseGuard()")
    forbid(app, "getCurrentWindow")

    require(
        modal_accessibility,
        'role="dialog"',
        "MutationObserver",
        "event.key === 'Escape'",
        "event.key !== 'Tab'",
        "Close dialog",
        "restoreFocus",
    )
    require(frontend_bootstrap, "installModalAccessibility", "installModalAccessibility();")

    require(
        activity,
        "ACTIVE_POLL_MS",
        "IDLE_POLL_MS",
        "refreshInFlight",
        "document.hidden",
        "HISTORY_PAGE_SIZE",
        "visibleHistory",
    )
    forbid(activity, "setInterval(")
    require(
        dashboard,
        "ACTIVE_RUNTIME_POLL_MS",
        "IDLE_RUNTIME_POLL_MS",
        "runtimePollInFlight",
        "document.hidden",
    )
    forbid(dashboard, "setInterval(")
    require(
        worlds,
        "TASK_POLL_VISIBLE_MS",
        "TASK_POLL_HIDDEN_MS",
        "World Manager owns task lifetime",
        "while (pageActive)",
        "runtimeProduct.worlds.tasks()",
        "recoverActiveTask",
    )
    forbid(worlds, "TASK_TIMEOUT_MS", "taking unusually long")
    require(
        backup_panel,
        "BACKUP_PAGE_SIZE",
        "calculateEstimate",
        "Storage sizing scans the full server and runs only when requested.",
        "visibleBackups",
    )
    forbid(backup_panel, "runtimeProduct.backups.estimate(workspace.id),")
    require(app_css, "content-visibility: auto", "contain-intrinsic-size: auto 76px")

    print("Launcher production-hardening source contract OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
