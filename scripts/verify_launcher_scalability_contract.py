#!/usr/bin/env python3
"""Verify bounded Launcher polling, rendering and history budgets."""

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


def rust_usize(source: str, name: str) -> int | None:
    match = re.search(rf"const\s+{re.escape(name)}:\s*usize\s*=\s*(\d+)\s*;", source)
    return int(match.group(1)) if match else None


def rust_u64_expr(source: str, name: str) -> int | None:
    match = re.search(rf"const\s+{re.escape(name)}:\s*u64\s*=\s*([^;]+);", source)
    if not match:
        return None
    expression = match.group(1).strip()
    if not re.fullmatch(r"[0-9\s*+_-]+", expression):
        return None
    return int(eval(expression, {"__builtins__": {}}, {}))


def ts_number(source: str, name: str) -> int | None:
    match = re.search(rf"const\s+{re.escape(name)}\s*=\s*(\d+)\s*;", source)
    return int(match.group(1)) if match else None


def main() -> int:
    contract = json.loads(CONTRACT.read_text(encoding="utf-8"))
    errors: list[str] = []
    if contract.get("schemaVersion") != 1:
        errors.append("launcher scalability contract schemaVersion must be 1")

    operations = read(LAUNCHER / "src-tauri/src/engine/operations.rs")
    server_tools = read(LAUNCHER / "src-tauri/src/commands/server_tools.rs")
    backup_commands = read(LAUNCHER / "src-tauri/src/commands/server_backups.rs")
    activity = read(LAUNCHER / "src/pages/Activity.svelte")
    dashboard = read(LAUNCHER / "src/pages/Dashboard.svelte")
    worlds = read(LAUNCHER / "src/pages/Worlds.svelte")
    backup_panel = read(LAUNCHER / "src/pages/BackupPanel.svelte")
    app_css = read(LAUNCHER / "src/styles/app.css")

    expected = contract["operationHistoryMax"]
    actual = rust_usize(operations, "MAX_OPERATION_HISTORY")
    if actual != expected:
        errors.append(f"operation history: expected {expected}, found {actual}")

    checks = [
        ("activity active poll", ts_number(activity, "ACTIVE_POLL_MS"), contract["activity"]["activePollMs"]),
        ("activity idle poll", ts_number(activity, "IDLE_POLL_MS"), contract["activity"]["idlePollMs"]),
        ("activity initial history rows", ts_number(activity, "HISTORY_PAGE_SIZE"), contract["activity"]["initialHistoryRows"]),
        ("overview active poll", ts_number(dashboard, "ACTIVE_RUNTIME_POLL_MS"), contract["overview"]["activePollMs"]),
        ("overview idle poll", ts_number(dashboard, "IDLE_RUNTIME_POLL_MS"), contract["overview"]["idlePollMs"]),
        ("world task visible poll", ts_number(worlds, "TASK_POLL_VISIBLE_MS"), contract["worldTasks"]["visiblePollMs"]),
        ("world task hidden poll", ts_number(worlds, "TASK_POLL_HIDDEN_MS"), contract["worldTasks"]["hiddenPollMs"]),
        ("world task timeout", ts_number(worlds, "TASK_TIMEOUT_MS"), contract["worldTasks"]["timeoutMs"]),
        ("backup initial rows", ts_number(backup_panel, "BACKUP_PAGE_SIZE"), contract["backups"]["initialRows"]),
        ("backup journal progress step", rust_u64_expr(backup_commands, "MIN_PROGRESS_JOURNAL_STEP_BYTES"), contract["backups"]["minimumProgressJournalStepBytes"]),
        ("server log tail bytes", rust_u64_expr(server_tools, "MAX_LOG_TAIL_BYTES"), contract["serverLogs"]["tailBytesMax"]),
        ("server log tail lines", rust_usize(server_tools, "MAX_LOG_LINES"), contract["serverLogs"]["tailLinesMax"]),
    ]
    for label, actual_value, expected_value in checks:
        if actual_value != expected_value:
            errors.append(f"{label}: expected {expected_value}, found {actual_value}")

    if contract["backups"].get("estimateMode") == "on-demand":
        if "calculateEstimate" not in backup_panel or "runtimeProduct.backups.estimate(workspace.id)," in backup_panel:
            errors.append("backup sizing is no longer explicitly on-demand")

    if contract["rendering"].get("offscreenContentVisibility"):
        if "content-visibility: auto" not in app_css:
            errors.append("off-screen content visibility optimization is missing")

    for label, source in (("Activity", activity), ("Overview", dashboard)):
        if "setInterval(" in source:
            errors.append(f"{label} restored fixed setInterval polling")
        if "document.hidden" not in source:
            errors.append(f"{label} does not pause polling while hidden")

    if "runtimeProduct.worlds.tasks()" not in worlds or "pageActive" not in worlds:
        errors.append("World task observation is not restartable/lifecycle-aware")

    if errors:
        print("Launcher scalability contract verification failed:")
        for error in errors:
            print(f" - {error}")
        return 1

    print("Launcher scalability contract OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
