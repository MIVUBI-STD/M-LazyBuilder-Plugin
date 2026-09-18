#!/usr/bin/env python3
"""Static invariants for LazyBuilder Launcher production hardening.

This is source-contract proof only. Runtime, packaging and Windows behavior require
separate verification gates.
"""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LAUNCHER = ROOT / "apps" / "launcher"
RUST = LAUNCHER / "src-tauri" / "src"
SRC = LAUNCHER / "src"


def read(path: Path) -> str:
    if not path.is_file():
        raise SystemExit(f"required Launcher hardening source is missing: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def require(errors: list[str], label: str, source: str, *markers: str) -> None:
    for marker in markers:
        if marker not in source:
            errors.append(f"{label} is missing hardening marker: {marker}")


def forbid(errors: list[str], label: str, source: str, *markers: str) -> None:
    for marker in markers:
        if marker in source:
            errors.append(f"{label} restored deprecated/bypass path: {marker}")


def main() -> int:
    errors: list[str] = []

    engine_mod = read(RUST / "engine/mod.rs")
    bootstrap = read(RUST / "app_bootstrap.rs")
    startup = read(RUST / "engine/startup.rs")
    operations = read(RUST / "engine/operations.rs")
    instance = read(RUST / "engine/app_instance.rs")
    launcher_settings = read(RUST / "engine/launcher_settings.rs")
    creation = read(RUST / "engine/workspace_creation.rs")
    creation_command = read(RUST / "commands/workspace_creation.rs")
    workspace_commands = read(RUST / "commands/workspace.rs")
    adoption = read(RUST / "engine/adoption.rs")
    registry = read(RUST / "engine/workspace_registry.rs")
    runtime_updates = read(RUST / "engine/runtime_updates.rs")
    diagnostics_command = read(RUST / "commands/diagnostics.rs")
    restore = read(RUST / "engine/server_restore.rs")
    backups = read(RUST / "engine/server_backups.rs")
    backup_recovery = read(RUST / "engine/backup_recovery.rs")
    backup_commands = read(RUST / "commands/server_backups.rs")
    storage = read(RUST / "engine/storage_health.rs")
    health = read(RUST / "engine/server_health.rs")
    plugin_ingress = read(RUST / "engine/plugin_ingress.rs")
    plugin_commands = read(RUST / "commands/plugin_manager.rs")
    server_commands = read(RUST / "commands/server_manager.rs")
    java_runtime = read(RUST / "engine/java_runtime.rs")
    paper_provider = read(RUST / "engine/paper_provider.rs")
    world_manager = read(RUST / "engine/world_manager/mod.rs")
    privacy_redaction = read(RUST / "engine/privacy_redaction.rs")
    support_bundle = read(RUST / "engine/support_bundle.rs")
    installer_smoke = read(ROOT / "tooling/windows-toolchain/scripts/distribution/verify-installer.ps1")

    app = read(SRC / "App.svelte")
    close_guard = read(SRC / "app/closeGuard.ts")
    dialog_focus = read(SRC / "app/dialogFocus.ts")
    activity = read(SRC / "pages/Activity.svelte")
    dashboard = read(SRC / "pages/Dashboard.svelte")
    worlds = read(SRC / "pages/Worlds.svelte")
    backup_panel = read(SRC / "pages/BackupPanel.svelte")
    startup_blocked = read(SRC / "components/StartupBlocked.svelte")
    app_css = read(SRC / "styles/app.css")

    require(
        errors,
        "engine module registry",
        engine_mod,
        "pub mod workspace_creation;",
        "pub mod app_instance;",
        "pub mod app_data_migrations;",
        "pub mod backup_recovery;",
        "pub mod privacy_redaction;",
        "pub mod storage_health;",
        "pub mod plugin_ingress;",
    )
    require(
        errors,
        "bootstrap",
        bootstrap,
        "app_instance::acquire()",
        "app_data_migrations::initialize()",
        "OperationRegistry::initialize()",
        "commands::workspace_creation::workspace_create",
    )
    forbid(errors, "bootstrap", bootstrap, "commands::workspace::workspace_create,")

    require(
        errors,
        "startup recovery",
        startup,
        "let mut ready = true;",
        "ready = false;",
        "StartupReport { ready, degraded",
        "workspace_creation::recover_pending_creations()",
        "adoption::recover_pending_adoptions()",
        "workspace_registry::recover_pending_duplicates()",
        "server_restore::recover_pending_restores()",
        "backup_recovery::recover_pending()",
        "server_process_guard::reconcile_registered_process_markers()",
    )
    forbid(errors, "startup recovery", startup, "StartupReport { ready: true")
    require(errors, "instance ownership", instance, "launcher-instance.json", "previous_session_unclean")

    require(
        errors,
        "operation journal",
        operations,
        "MAX_OPERATION_HISTORY: usize = 100",
        "OPERATION_JOURNAL_SCHEMA_VERSION",
        "operations.json",
        "persist_journal",
        "recover_journal_file",
        "persistence::read_json",
        "persistence::write_json_atomically",
        "INTERRUPTED_LAUNCHER_OPERATION",
        "RecoveryRequired",
        "resources_conflict",
    )
    forbid(
        errors,
        "operation journal",
        operations,
        "fn replace_journal_file",
        "fn metadata_entry_exists",
        "fn ensure_regular_metadata_file",
    )

    require(
        errors,
        "launcher settings",
        launcher_settings,
        "SETTINGS_SCHEMA_VERSION: u32 = 2",
        "auto_check_updates: false",
        'update_channel: "stable".into()',
        "legacy_update_preferences_migrate_to_supported_state",
        "unavailable_update_preferences_are_rejected",
    )

    require(errors, "create recovery", creation, "pending-creations.json", "CREATE_RECOVERY_REQUIRED", "recover_pending_creations")
    for label, source in (
        ("create recovery persistence", creation),
        ("adoption recovery persistence", adoption),
        ("restore recovery persistence", restore),
        ("backup recovery persistence", backup_recovery),
    ):
        require(
            errors,
            label,
            source,
            "persistence::recover_atomic_file",
            "persistence::read_json",
            "persistence::write_json_atomically",
        )
        forbid(
            errors,
            label,
            source,
            "fn replace_pending_creation_file",
            "fn replace_pending_adoption_file",
            "fn replace_pending_restore_file",
            "fn atomic_write_json",
        )
    require(
        errors,
        "create command",
        creation_command,
        "ServerStartLease::acquire()",
        'begin_exclusive("create-server"',
        "RecoveryAction::WaitForServerStart",
        "RecoveryAction::OpenActivity",
        "RecoveryAction::RestartLauncher",
    )
    forbid(errors, "create command", creation_command, "ensure_no_running_paper_except")

    require(
        errors,
        "workspace delete/duplicate recovery",
        workspace_commands,
        'begin_exclusive("delete-server"',
        '"DELETE_RECOVERY_REQUIRED"',
        "operations.require_recovery",
        "workspace_registry::delete(&id, &typed_display_name)",
    )
    require(errors, "adoption recovery", adoption, "pending-adoptions.json", "ADOPTION_RECOVERY_REQUIRED", "recover_pending_adoptions", "is_reparse_point")
    require(
        errors,
        "workspace registry recovery",
        registry,
        "pending-duplicates.json",
        "DUPLICATE_RECOVERY_REQUIRED",
        "recover_pending_duplicates",
        "pending-deletions.json",
        "recover_pending_deletions",
        "validate_manifest(&manifest)?;",
    )

    require(
        errors,
        "runtime update metadata",
        runtime_updates,
        "workspace_registry::manifest(workspace)?",
        "workspace_registry::update_paper_build",
        "workspace_registry::update_core_versions",
    )
    forbid(errors, "runtime update metadata", runtime_updates, "fn write_json_atomic(", "fn update_manifest_field(")
    require(errors, "diagnostics manifest", diagnostics_command, "workspace_registry::manifest(Path::new(&entry.path))", "build_commit", "build_channel", "build_target")

    require(errors, "restore recovery", restore, "pending-restores.json", "recover_pending_restores")
    require(errors, "backup integrity", backups, "BACKUP_SCHEMA_VERSION: u32 = 2", "sha256", "pub fn verify(", "verify_snapshot_integrity")
    require(
        errors,
        "backup recovery",
        backup_recovery,
        "pending-backups.json",
        "backup-recovery-index-v1.json",
        "pub fn begin(",
        "pub fn recover_workspace(",
        "legacy_sweep_required",
        "mark_legacy_sweep_complete",
    )
    require(
        errors,
        "backup command durability",
        backup_commands,
        "backup_recovery::begin(&workspace_id)",
        "backup_recovery::recover_workspace(&workspace_id)",
        "DurableProgressReporter",
        "should_persist_progress",
    )

    require(errors, "storage health", storage, "StoragePressure", "inspect_workspace", "CRITICAL_AVAILABLE_BYTES")
    require(errors, "readiness storage", health, '"storage-capacity"', "StoragePressure::Critical")

    require(
        errors,
        "plugin ingress",
        plugin_ingress,
        "MAX_PLUGIN_JAR_BYTES",
        "MAX_PLUGIN_ARCHIVE_ENTRIES",
        "stage_selected_jar",
        "recover_stale_ingress",
        "PluginIngressLease",
        "is_reparse_point",
    )
    require(errors, "plugin command ingress", plugin_commands, "plugin_ingress::stage_selected_jar", "plugins.install(&staged_path)", "plugins.update(&plugin_id, &staged_path)")

    require(
        errors,
        "server start hardening",
        server_commands,
        "server_process_guard::ensure_concurrent_server_capacity(&active.id)?;",
        "world_manager::prepare_control_options_for_start()?;",
        "startup_guard::ensure_memory_headroom(active_runtime_count)?;",
        "select_paper_port(configured_paper_port(&active.path)?)",
        "TcpListener::bind((\"0.0.0.0\", port))",
    )
    require(
        errors,
        "World Manager loopback control",
        world_manager,
        "prepare_control_options_for_start",
        "loopback_port_available",
        'TcpListener::bind(("127.0.0.1", port))',
        '"X-LazyBuilder-Sha256"',
        "sha256_file(&path)?",
    )

    require(
        errors,
        "managed Java",
        java_runtime,
        ".enclosed_name()",
        "failed SHA-256 verification",
        "MAX_JAVA_ARCHIVE_BYTES",
        "MAX_JAVA_ARCHIVE_ENTRIES",
        "MAX_JAVA_EXTRACTED_BYTES",
        "create_new(true)",
        "sync_all()",
        "FILE_ATTRIBUTE_REPARSE_POINT",
    )
    require(
        errors,
        "Paper provider",
        paper_provider,
        "MAX_PAPER_JAR_BYTES",
        "failed SHA-256 verification",
        "create_new(true)",
        "sync_all()",
        "FILE_ATTRIBUTE_REPARSE_POINT",
    )
    require(
        errors,
        "installer smoke isolation",
        installer_smoke,
        "Pre-existing LazyBuilder installation detected.",
        "expected exactly one",
        "clean-PATH startup smoke",
    )

    require(
        errors,
        "privacy redaction",
        privacy_redaction,
        "pub struct RedactionPolicy",
        "for_support_bundle",
        "add_path_variants",
        "json_escaped_windows_paths_are_redacted",
    )
    require(
        errors,
        "support bundle",
        support_bundle,
        "RedactionPolicy::for_support_bundle",
        '"diagnostics.json"',
        '"operations.json"',
        '"startup.json"',
        "MAX_LOG_FILE_BYTES",
        "create_new(true)",
        "sync_all()",
    )
    forbid(errors, "support bundle", support_bundle, "fn redact(", "fn replace_case_insensitive(")

    require(
        errors,
        "close guard",
        close_guard,
        "ACTIVE_OPERATION_STATES",
        "runtimeProduct.operations.list()",
        "RUNNING_SERVER_STATES",
        "Closing now will interrupt the operation and may require recovery",
    )
    require(errors, "App close integration", app, "installLauncherCloseGuard(handleCloseRequest)", "use:dialogFocus", 'aria-modal="true"')
    forbid(errors, "App close integration", app, "getCurrentWindow")
    require(
        errors,
        "dialog focus",
        dialog_focus,
        "const previousFocus",
        "event.key === 'Escape'",
        "event.key !== 'Tab'",
        "last.focus()",
        "first.focus()",
        "previousFocus?.isConnected",
    )

    require(
        errors,
        "protected startup shell",
        app,
        "startupReport = await runtimeProduct.startup.status()",
        "startupBlocked",
        "<StartupBlocked",
    )
    require(errors, "protected startup presentation", startup_blocked, "Protected startup", "Fail-closed behavior", "Open Activity", "Open Support settings")

    require(errors, "Activity polling", activity, "ACTIVE_POLL_MS", "IDLE_POLL_MS", "refreshInFlight", "document.hidden", "HISTORY_PAGE_SIZE")
    forbid(errors, "Activity polling", activity, "setInterval(")
    require(errors, "Overview polling", dashboard, "ACTIVE_RUNTIME_POLL_MS", "IDLE_RUNTIME_POLL_MS", "runtimePollInFlight", "document.hidden")
    forbid(errors, "Overview polling", dashboard, "setInterval(")
    require(errors, "World task polling", worlds, "TASK_POLL_VISIBLE_MS", "TASK_POLL_HIDDEN_MS", "while (pageActive)", "runtimeProduct.worlds.tasks()", "recoverActiveTask")
    forbid(errors, "World task polling", worlds, "TASK_TIMEOUT_MS", "taking unusually long")
    require(errors, "Backup pagination", backup_panel, "BACKUP_PAGE_SIZE", "calculateEstimate", "estimateBusy", "visibleBackups")
    require(errors, "rendering bounds", app_css, "content-visibility: auto", "contain-intrinsic-size: auto 76px")

    if errors:
        print("Launcher production-hardening source contract verification failed:")
        for error in errors:
            print(f" - {error}")
        return 1

    print("Launcher production-hardening source contract OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
