#!/usr/bin/env python3
"""Verify bounded Launcher polling, rendering, history, and multi-server budgets."""

from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LAUNCHER = ROOT / "apps" / "launcher"
CONTRACT = ROOT / "docs" / "launcher-scalability-contract.json"


def read(path: Path) -> str:
    if not path.is_file():
        raise SystemExit(f"required scalability source is missing: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def eval_int_expression(expression: str) -> int | None:
    expression = expression.strip().replace("_", "")
    if not re.fullmatch(r"[0-9\s*+/-]+", expression):
        return None
    try:
        value = eval(expression, {"__builtins__": {}}, {})
    except Exception:
        return None
    return int(value) if isinstance(value, int) else None


def rust_int(source: str, name: str, kind: str) -> int | None:
    match = re.search(rf"(?:pub\s+)?const\s+{re.escape(name)}:\s*{kind}\s*=\s*([^;]+);", source)
    return eval_int_expression(match.group(1)) if match else None


def ts_int(source: str, name: str) -> int | None:
    match = re.search(rf"const\s+{re.escape(name)}\s*=\s*([^;]+);", source)
    return eval_int_expression(match.group(1)) if match else None


def require(errors: list[str], label: str, source: str, *markers: str) -> None:
    for marker in markers:
        if marker not in source:
            errors.append(f"{label} is missing marker: {marker}")


def forbid(errors: list[str], label: str, source: str, *markers: str) -> None:
    for marker in markers:
        if marker in source:
            errors.append(f"{label} restored forbidden scalability path: {marker}")


def main() -> int:
    contract = json.loads(CONTRACT.read_text(encoding="utf-8"))
    errors: list[str] = []
    if contract.get("schemaVersion") != 1:
        errors.append("launcher scalability contract schemaVersion must be 1")

    operations = read(LAUNCHER / "src-tauri/src/engine/operations.rs")
    process_guard = read(LAUNCHER / "src-tauri/src/engine/server_process_guard.rs")
    runtime_registry = read(LAUNCHER / "src-tauri/src/engine/server_runtime_registry.rs")
    server_engine = read(LAUNCHER / "src-tauri/src/engine/server_manager/mod.rs")
    startup_guard = read(LAUNCHER / "src-tauri/src/engine/startup_guard.rs")
    server_commands = read(LAUNCHER / "src-tauri/src/commands/server_manager.rs")
    world_commands = read(LAUNCHER / "src-tauri/src/commands/world_manager.rs")
    server_tools = read(LAUNCHER / "src-tauri/src/commands/server_tools.rs")
    backup_commands = read(LAUNCHER / "src-tauri/src/commands/server_backups.rs")
    backup_recovery = read(LAUNCHER / "src-tauri/src/engine/backup_recovery.rs")
    server_repair = read(LAUNCHER / "src-tauri/src/engine/server_repair.rs")

    runtime_api = read(LAUNCHER / "src/app/bridge/runtimeApi.ts")
    runtime_types = read(LAUNCHER / "src/app/bridge/runtimeTypes.ts")
    server_api = read(LAUNCHER / "src/app/bridge/serverApi.ts")
    runtime_facade = read(LAUNCHER / "src/app/bridge/runtimeProductFacade.ts")
    library_actions = read(LAUNCHER / "src/app/serverLibraryRuntimeActions.ts")
    close_guard = read(LAUNCHER / "src/app/closeGuard.ts")
    server_console = read(LAUNCHER / "src/components/ServerConsole.svelte")
    app = read(LAUNCHER / "src/App.svelte")
    library = read(LAUNCHER / "src/pages/ServersLibrary.svelte")
    activity = read(LAUNCHER / "src/pages/Activity.svelte")
    dashboard = read(LAUNCHER / "src/pages/Dashboard.svelte")
    worlds = read(LAUNCHER / "src/pages/Worlds.svelte")
    backup_panel = read(LAUNCHER / "src/pages/BackupPanel.svelte")
    app_css = read(LAUNCHER / "src/styles/app.css")

    expected_servers = contract["maxConcurrentServers"]
    actual_servers = rust_int(process_guard, "MAX_CONCURRENT_SERVERS", "usize")
    if actual_servers != expected_servers:
        errors.append(f"concurrent server limit: expected {expected_servers}, found {actual_servers}")
    ui_limit = ts_int(dashboard, "MAX_CONCURRENT_SERVERS")
    if ui_limit != expected_servers:
        errors.append(f"Overview server-capacity label: expected {expected_servers}, found {ui_limit}")

    multi = contract["multiServer"]
    if multi.get("runtimeAuthority") != "workspace-keyed-server-runtime-registry":
        errors.append("runtime authority contract changed unexpectedly")
    if multi.get("workspaceSwitchKeepsRunningServers") is not True:
        errors.append("workspace switch must keep running servers attached")
    if multi.get("lifecycleSerialization") != "global-server-lifecycle-lease":
        errors.append("server lifecycle transitions must use one global lifecycle lease")
    if multi.get("paperPortAssignment") != "runtime-dynamic-visible":
        errors.append("Paper port assignment contract changed unexpectedly")
    if multi.get("worldControlPortAssignment") != "workspace-persistent-loopback":
        errors.append("World control port assignment contract changed unexpectedly")
    if multi.get("detachedProcessesConsumeCapacity") is not True:
        errors.append("detached managed Paper processes must consume capacity")
    if multi.get("targetedControls") != ["snapshot", "console", "stop", "detached-recovery", "log-tail"]:
        errors.append("targeted server control set changed unexpectedly")
    if multi.get("targetIdentity") != "workspace-id":
        errors.append("targeted controls must remain workspace-id keyed")
    if multi.get("targetedControlsRequireActiveWorkspace") is not False:
        errors.append("targeted controls must not require target to be selected workspace")
    if multi.get("runtimeFallbackMayReadAnotherActiveWorkspaceConfig") is not False:
        errors.append("workspace-bound runtime may not fall back to another active workspace config")
    if multi.get("workspaceBoundControllerRequiresRuntimeRoot") is not True:
        errors.append("workspace-bound controllers must require an immutable runtime root")
    if multi.get("invalidProcessMarkersFailClosed") is not True:
        errors.append("invalid process-marker evidence must fail closed")

    require(
        errors,
        "ServerRuntimeRegistry",
        runtime_registry,
        "HashMap<String, RuntimeEntry>",
        "ServerManagerState::for_workspace",
        "pub fn runtime_for_id",
        "pub fn summaries",
        "pub fn active_count",
        "used_memory_bytes",
        "max_memory_bytes",
        "running_registered_papers()",
    )
    forbid(errors, "ServerRuntimeRegistry", runtime_registry, "ServerRuntimeFleet", "managed_used_memory_bytes")
    require(
        errors,
        "ServerManagerState",
        server_engine,
        "workspace_root: PathBuf",
        "pub fn for_workspace(workspace_root: PathBuf)",
        "Server runtime controller is missing its immutable workspace root.",
        '.arg("--port").arg(paper_port.to_string())',
    )
    forbid(errors, "ServerManagerState", server_engine, "impl Default for ServerManagerState")
    require(
        errors,
        "server process guard",
        process_guard,
        "running_registered_papers()",
        "ensure_concurrent_server_capacity",
        "malformed",
        "preserved for recovery",
    )
    require(
        errors,
        "server lifecycle commands",
        server_commands,
        "ensure_concurrent_server_capacity(&active.id)",
        "prepare_control_options_for_start",
        "ensure_memory_headroom(active_runtime_count)",
        "resolve_runtime(&registry, workspace_id.as_deref())",
        "registry.runtime_for_id(id)",
    )
    if server_commands.count("let _lease = ServerStartLease::acquire()?;") < 4:
        errors.append("Start, Stop, Restart, and Detached recovery must share the lifecycle lease")
    require(errors, "startup RAM admission", startup_guard, "active_runtime_count: usize", "system.available_memory()")

    require(errors, "runtimeApi", runtime_api, "server: serverApi.server", "operations: appApi.operations")
    forbid(errors, "runtimeApi", runtime_api, "server_runtime_list", "server_connection_port", "invokeRuntime<")
    require(
        errors,
        "runtimeTypes",
        runtime_types,
        "export type ServerRuntimeSummary",
        "usedMemoryBytes: number",
        "maxMemoryBytes: number",
    )
    require(
        errors,
        "serverApi",
        server_api,
        "'server_runtime_list'",
        "'server_connection_port'",
        "snapshot: (workspaceId?: string)",
        "command: (command: string, workspaceId?: string)",
        "stop: (workspaceId?: string)",
        "recoverDetached: (workspaceId?: string)",
        "logTail: (path: string, workspaceId?: string)",
    )
    require(errors, "runtime facade", runtime_facade, "const productionRuntimeProduct = runtimeApi;")

    require(errors, "App server library feed", app, "runtimeProduct.server.runtimes()", "runtimes={serverRuntimes}")
    forbid(errors, "App server library feed", app, "setInterval(")
    require(
        errors,
        "ServersLibrary runtime UI",
        library,
        "runtimeMeta(runtime)",
        "openLibraryConsole(server, runtime)",
        "requestStopRuntime(server, runtime)",
        "Stop external server",
        "workspaceId={libraryConsoleWorkspaceId ?? undefined}",
    )
    require(
        errors,
        "Server Library targeted actions",
        library_actions,
        "canOpenRuntimeConsole",
        "canStopLibraryRuntime",
        "runtimeProduct.server.stop(target)",
    )
    require(
        errors,
        "ServerConsole targeting",
        server_console,
        "runtimeProduct.server.snapshot(workspaceId)",
        "runtimeProduct.server.command(nextCommand, workspaceId)",
        "runtimeProduct.server.logTail(snapshot.logPath || '', workspaceId)",
    )
    require(errors, "close guard", close_guard, "runtimeProduct.server.runtimes()")
    require(
        errors,
        "Overview fleet",
        dashboard,
        "runtimeProduct.server.connectionPort()",
        "localhost:{connectionPort}",
        "runtimeProduct.server.runtimes()",
        "managedMemoryBytes()",
    )

    require(
        errors,
        "World Manager targeting",
        world_commands,
        "active_world_target()",
        "run_targeted_read",
        "run_targeted_mutation",
        "ensure_world_target(&target)",
        "ServerStartLease::acquire()",
        '"WORLD_TARGET_CHANGED"',
    )
    world_contract = contract["worldTasks"]
    if world_contract.get("lifetimeAuthority") != "world-manager":
        errors.append("World task lifetime authority must remain world-manager")
    if world_contract.get("frontendTimeoutMs") is not None:
        errors.append("World tasks must not have a frontend terminal timeout")
    if world_contract.get("reattachFromTaskList") is not True:
        errors.append("World task UI must reattach from backend task list")
    require(errors, "Worlds UI", worlds, "runtimeProduct.worlds.tasks()", "recoverActiveTask", "while (pageActive)")
    forbid(errors, "Worlds UI", worlds, "TASK_TIMEOUT_MS", "taking unusually long")

    numeric_checks = [
        ("operation history", rust_int(operations, "MAX_OPERATION_HISTORY", "usize"), contract["operationHistoryMax"]),
        ("activity active poll", ts_int(activity, "ACTIVE_POLL_MS"), contract["activity"]["activePollMs"]),
        ("activity idle poll", ts_int(activity, "IDLE_POLL_MS"), contract["activity"]["idlePollMs"]),
        ("activity initial history rows", ts_int(activity, "HISTORY_PAGE_SIZE"), contract["activity"]["initialHistoryRows"]),
        ("overview active poll", ts_int(dashboard, "ACTIVE_RUNTIME_POLL_MS"), contract["overview"]["activePollMs"]),
        ("overview idle poll", ts_int(dashboard, "IDLE_RUNTIME_POLL_MS"), contract["overview"]["idlePollMs"]),
        ("world visible poll", ts_int(worlds, "TASK_POLL_VISIBLE_MS"), world_contract["visiblePollMs"]),
        ("world hidden poll", ts_int(worlds, "TASK_POLL_HIDDEN_MS"), world_contract["hiddenPollMs"]),
        ("backup initial rows", ts_int(backup_panel, "BACKUP_PAGE_SIZE"), contract["backups"]["initialRows"]),
        ("backup minimum journal step", rust_int(backup_commands, "MIN_PROGRESS_JOURNAL_STEP_BYTES", "u64"), contract["backups"]["minimumProgressJournalStepBytes"]),
        ("server log tail bytes", rust_int(server_tools, "MAX_LOG_TAIL_BYTES", "u64"), contract["serverLogs"]["tailBytesMax"]),
        ("server log tail lines", rust_int(server_tools, "MAX_LOG_LINES", "usize"), contract["serverLogs"]["tailLinesMax"]),
    ]
    for label, actual, expected in numeric_checks:
        if actual != expected:
            errors.append(f"{label}: expected {expected}, found {actual}")

    if contract["overview"].get("repairPlanReusesHealthSnapshot") is not True:
        errors.append("repair plan must reuse the readiness snapshot used to derive it")
    require(errors, "server repair", server_repair, "health:", "server_health::inspect")

    backups_contract = contract["backups"]
    if backups_contract.get("estimateMode") != "on-demand":
        errors.append("backup estimate mode must remain on-demand")
    if backups_contract.get("relativeProgressJournalStepPercent") != 1:
        errors.append("backup relative journal progress step must remain 1 percent")
    if backups_contract.get("startupRecoveryMode") != "indexed-after-one-legacy-sweep":
        errors.append("backup startup recovery mode changed unexpectedly")
    if backups_contract.get("recoveryIndexFile") != "pending-backups.json":
        errors.append("backup recovery index file changed unexpectedly")
    require(errors, "backup UI", backup_panel, "calculateEstimate", "estimateBusy", "visibleBackups")
    require(errors, "backup journal", backup_commands, "DurableProgressReporter", "should_persist_progress", "total / 100")
    require(errors, "backup recovery", backup_recovery, "pending-backups.json", "legacy_sweep_required", "mark_legacy_sweep_complete")

    if contract["rendering"].get("offscreenContentVisibility") is not True:
        errors.append("offscreen rendering contract must remain enabled")
    require(errors, "Launcher CSS", app_css, "content-visibility: auto", "contain-intrinsic-size: auto 76px")

    if errors:
        print("Launcher scalability contract verification failed:")
        for error in errors:
            print(f" - {error}")
        return 1

    print("Launcher scalability contract OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
