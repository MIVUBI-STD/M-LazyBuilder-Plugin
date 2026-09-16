#!/usr/bin/env python3
"""Verify bounded Launcher polling, rendering, history, and server-runtime budgets."""

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


def rust_usize(source: str, name: str) -> int | None:
    match = re.search(rf"const\s+{re.escape(name)}:\s*usize\s*=\s*([^;]+);", source)
    return eval_int_expression(match.group(1)) if match else None


def rust_u64_expr(source: str, name: str) -> int | None:
    match = re.search(rf"const\s+{re.escape(name)}:\s*u64\s*=\s*([^;]+);", source)
    return eval_int_expression(match.group(1)) if match else None


def ts_int_expr(source: str, name: str) -> int | None:
    match = re.search(rf"const\s+{re.escape(name)}\s*=\s*([^;]+);", source)
    return eval_int_expression(match.group(1)) if match else None


def main() -> int:
    contract = json.loads(CONTRACT.read_text(encoding="utf-8"))
    errors: list[str] = []
    if contract.get("schemaVersion") != 1:
        errors.append("launcher scalability contract schemaVersion must be 1")

    operations = read(LAUNCHER / "src-tauri/src/engine/operations.rs")
    process_guard = read(LAUNCHER / "src-tauri/src/engine/server_process_guard.rs")
    runtime_registry = read(LAUNCHER / "src-tauri/src/engine/server_runtime_registry.rs")
    server_engine = read(LAUNCHER / "src-tauri/src/engine/server_manager/mod.rs")
    server_commands = read(LAUNCHER / "src-tauri/src/commands/server_manager.rs")
    workspace_commands = read(LAUNCHER / "src-tauri/src/commands/workspace.rs")
    workspace_creation = read(LAUNCHER / "src-tauri/src/commands/workspace_creation.rs")
    server_tools = read(LAUNCHER / "src-tauri/src/commands/server_tools.rs")
    backup_commands = read(LAUNCHER / "src-tauri/src/commands/server_backups.rs")
    backup_recovery = read(LAUNCHER / "src-tauri/src/engine/backup_recovery.rs")
    startup = read(LAUNCHER / "src-tauri/src/engine/startup.rs")
    server_repair = read(LAUNCHER / "src-tauri/src/engine/server_repair.rs")
    runtime_api = read(LAUNCHER / "src/app/bridge/runtimeApi.ts")
    runtime_facade = read(LAUNCHER / "src/app/bridge/runtimeProductFacade.ts")
    preview_runtime = read(LAUNCHER / "src/app/bridge/runtimePreviewProduct.ts")
    close_guard = read(LAUNCHER / "src/app/closeGuard.ts")
    activity = read(LAUNCHER / "src/pages/Activity.svelte")
    dashboard = read(LAUNCHER / "src/pages/Dashboard.svelte")
    worlds = read(LAUNCHER / "src/pages/Worlds.svelte")
    backup_panel = read(LAUNCHER / "src/pages/BackupPanel.svelte")
    health_panel = read(LAUNCHER / "src/pages/HealthPanel.svelte")
    app_css = read(LAUNCHER / "src/styles/app.css")

    expected_servers = contract.get("maxConcurrentServers")
    actual_servers = rust_usize(process_guard, "MAX_CONCURRENT_SERVERS")
    if actual_servers != expected_servers:
        errors.append(f"concurrent server limit: expected {expected_servers}, found {actual_servers}")

    required_runtime_markers = [
        "HashMap<String, RuntimeEntry>",
        "ServerManagerState::for_workspace",
        "set_paper_port",
        "pub fn summaries",
    ]
    for marker in required_runtime_markers:
        if marker not in runtime_registry:
            errors.append(f"multi-server runtime registry is missing marker: {marker}")

    if "ensure_concurrent_server_capacity(&active.id)" not in server_commands:
        errors.append("server start no longer enforces the concurrent runtime ceiling")
    if "prepare_control_options_for_start" not in server_commands:
        errors.append("server start no longer allocates a workspace-safe World Manager control port")
    if '.arg("--port").arg(paper_port.to_string())' not in server_engine:
        errors.append("Paper runtime no longer receives its isolated listen-port override")
    for label, source in (("server commands", server_commands), ("workspace commands", workspace_commands), ("workspace creation", workspace_creation)):
        if "ensure_no_running_paper_except" in source:
            errors.append(f"{label} restored the legacy global single-server guard")
    if "runtimeProduct.server.runtimes()" not in close_guard:
        errors.append("close guard no longer checks all attached server runtimes")
    if "runtimeProduct.server.connectionPort()" not in dashboard or "localhost:{connectionPort}" not in dashboard:
        errors.append("Overview no longer exposes the actual active Paper connection port")
    for command_name in ("server_runtime_list", "server_connection_port"):
        if command_name not in runtime_api:
            errors.append(f"canonical runtimeApi no longer exposes {command_name}")
    if "const productionRuntimeProduct = runtimeApi;" not in runtime_facade:
        errors.append("production runtime product no longer uses the single canonical runtimeApi bridge")

    expected = contract["operationHistoryMax"]
    actual = rust_usize(operations, "MAX_OPERATION_HISTORY")
    if actual != expected:
        errors.append(f"operation history: expected {expected}, found {actual}")

    checks = [
        ("activity active poll", ts_int_expr(activity, "ACTIVE_POLL_MS"), contract["activity"]["activePollMs"]),
        ("activity idle poll", ts_int_expr(activity, "IDLE_POLL_MS"), contract["activity"]["idlePollMs"]),
        ("activity initial history rows", ts_int_expr(activity, "HISTORY_PAGE_SIZE"), contract["activity"]["initialHistoryRows"]),
        ("overview active poll", ts_int_expr(dashboard, "ACTIVE_RUNTIME_POLL_MS"), contract["overview"]["activePollMs"]),
        ("overview idle poll", ts_int_expr(dashboard, "IDLE_RUNTIME_POLL_MS"), contract["overview"]["idlePollMs"]),
        ("world task visible poll", ts_int_expr(worlds, "TASK_POLL_VISIBLE_MS"), contract["worldTasks"]["visiblePollMs"]),
        ("world task hidden poll", ts_int_expr(worlds, "TASK_POLL_HIDDEN_MS"), contract["worldTasks"]["hiddenPollMs"]),
        ("backup initial rows", ts_int_expr(backup_panel, "BACKUP_PAGE_SIZE"), contract["backups"]["initialRows"]),
        ("backup journal progress step", rust_u64_expr(backup_commands, "MIN_PROGRESS_JOURNAL_STEP_BYTES"), contract["backups"]["minimumProgressJournalStepBytes"]),
        ("server log tail bytes", rust_u64_expr(server_tools, "MAX_LOG_TAIL_BYTES"), contract["serverLogs"]["tailBytesMax"]),
        ("server log tail lines", rust_usize(server_tools, "MAX_LOG_LINES"), contract["serverLogs"]["tailLinesMax"]),
    ]
    for label, actual_value, expected_value in checks:
        if actual_value != expected_value:
            errors.append(f"{label}: expected {expected_value}, found {actual_value}")

    world_contract = contract["worldTasks"]
    if world_contract.get("lifetimeAuthority") == "world-manager":
        if "TASK_TIMEOUT_MS" in worlds or "Date.now() - startedAt" in worlds:
            errors.append("Worlds restored a frontend-owned world task lifetime timeout")
        if "while (pageActive)" not in worlds:
            errors.append("World task polling no longer observes until backend terminal state or page detach")
    if world_contract.get("frontendTimeoutMs") is not None:
        errors.append("world task frontendTimeoutMs must remain null while World Manager owns task lifetime")
    if world_contract.get("reattachFromTaskList"):
        if "runtimeProduct.worlds.tasks()" not in worlds or "recoverActiveTask" not in worlds:
            errors.append("World task observation no longer re-attaches from backend task snapshots")

    backup_contract = contract["backups"]
    if backup_contract.get("estimateMode") == "on-demand":
        if "calculateEstimate" not in backup_panel or "runtimeProduct.backups.estimate(workspace.id)," in backup_panel:
            errors.append("backup sizing is no longer explicitly on-demand")
    if backup_contract.get("startupRecoveryMode") == "indexed-after-one-legacy-sweep":
        required_index_markers = [
            backup_contract.get("recoveryIndexFile", "pending-backups.json"),
            "recover_pending",
            "legacy_sweep_required",
            "mark_legacy_sweep_complete",
        ]
        missing = [marker for marker in required_index_markers if marker not in backup_recovery]
        if missing:
            errors.append(f"backup indexed recovery is missing markers: {missing}")
        for marker in ("backup_recovery::recover_pending()", "backup_recovery::legacy_sweep_required()", "server_backups::recover_staging()", "backup_recovery::mark_legacy_sweep_complete()"):
            if marker not in startup:
                errors.append(f"startup backup recovery migration is missing {marker}")
        if "backup_recovery::begin(&workspace_id)" not in backup_commands:
            errors.append("backup/restore commands no longer register pending backup recovery by workspace")

    if contract["overview"].get("repairPlanReusesHealthSnapshot"):
        if "pub health: server_health::ServerHealthSnapshot" not in server_repair:
            errors.append("repair plan no longer carries the health snapshot used to derive it")
        if "health: ServerHealthSnapshot" not in runtime_api:
            errors.append("TypeScript ServerRepairPlan no longer exposes the backend health snapshot")
        if "health: healthSnapshot()" not in preview_runtime:
            errors.append("visual preview repair plan no longer matches the production health contract")
        if "health = nextPlan.health;" not in health_panel:
            errors.append("HealthPanel no longer reuses the typed repair-plan health snapshot")
        if "RepairPlanWithHealth" in health_panel:
            errors.append("HealthPanel restored the legacy repair-plan health cast")
        if "runtimeProduct.health.server(workspace.id)" in health_panel:
            errors.append("HealthPanel restored a second backend health diagnosis")

    if contract["rendering"].get("offscreenContentVisibility"):
        if "content-visibility: auto" not in app_css:
            errors.append("off-screen content visibility optimization is missing")

    for label, source in (("Activity", activity), ("Overview", dashboard)):
        if "setInterval(" in source:
            errors.append(f"{label} restored fixed setInterval polling")
        if "document.hidden" not in source:
            errors.append(f"{label} does not pause polling while hidden")

    if errors:
        print("Launcher scalability contract verification failed:")
        for error in errors:
            print(f" - {error}")
        return 1

    print("Launcher scalability contract OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
