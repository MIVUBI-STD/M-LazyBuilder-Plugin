#!/usr/bin/env python3
"""Verify the Launcher has one coherent Tauri command surface."""

from __future__ import annotations

import re
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUST = ROOT / "apps" / "launcher" / "src-tauri" / "src"
COMMANDS = RUST / "commands"
BOOTSTRAP = RUST / "app_bootstrap.rs"
RUNTIME_API = ROOT / "apps" / "launcher" / "src" / "app" / "bridge" / "runtimeApi.ts"

COMMAND_RE = re.compile(r"#\[tauri::command\]\s*pub\s+(?:async\s+)?fn\s+([A-Za-z0-9_]+)", re.M)
REGISTERED_RE = re.compile(r"commands::([A-Za-z0-9_]+)::([A-Za-z0-9_]+)")
INVOKE_RE = re.compile(r"invokeRuntime(?:<[^;\n]+?>)?\(\s*['\"]([A-Za-z0-9_]+)['\"]")


def main() -> int:
    errors: list[str] = []
    owners: dict[str, list[str]] = defaultdict(list)
    module_commands: set[tuple[str, str]] = set()

    for path in sorted(COMMANDS.glob("*.rs")):
        if path.name in {"mod.rs", "error.rs"}:
            continue
        text = path.read_text(encoding="utf-8")
        module = path.stem
        for command in COMMAND_RE.findall(text):
            owners[command].append(module)
            module_commands.add((module, command))

    for command, modules in sorted(owners.items()):
        if len(modules) != 1:
            errors.append(f"Tauri command {command!r} has multiple owners: {modules}")

    bootstrap = BOOTSTRAP.read_text(encoding="utf-8")
    handler_match = re.search(r"\.invoke_handler\(tauri::generate_handler!\[(.*?)\]\)", bootstrap, re.S)
    if not handler_match:
        errors.append("app_bootstrap.rs has no parseable tauri::generate_handler! command list")
        registered: list[tuple[str, str]] = []
    else:
        registered = REGISTERED_RE.findall(handler_match.group(1))

    registration_counts = Counter(registered)
    for item, count in sorted(registration_counts.items()):
        if count != 1:
            errors.append(f"Tauri handler registers commands::{item[0]}::{item[1]} {count} times")

    registered_set = set(registered)
    missing_registration = sorted(module_commands - registered_set)
    stale_registration = sorted(registered_set - module_commands)
    for module, command in missing_registration:
        errors.append(f"command owner commands/{module}.rs::{command} is not registered in app_bootstrap.rs")
    for module, command in stale_registration:
        errors.append(f"app_bootstrap.rs registers missing/non-command owner commands::{module}::{command}")

    runtime_api = RUNTIME_API.read_text(encoding="utf-8")
    invoked = INVOKE_RE.findall(runtime_api)
    invoked_counts = Counter(invoked)
    for command, count in sorted(invoked_counts.items()):
        if count != 1:
            errors.append(f"runtimeApi.ts declares command string {command!r} {count} times")
        if command not in owners:
            errors.append(f"runtimeApi.ts invokes unregistered Tauri command {command!r}")

    for command in invoked:
        modules = owners.get(command, [])
        if len(modules) == 1 and (modules[0], command) not in registered_set:
            errors.append(f"runtimeApi.ts command {command!r} is owned by {modules[0]} but not exposed by generate_handler")

    registered_names = {command for _, command in registered_set}
    invoked_names = set(invoked_counts)
    for command in sorted(registered_names - invoked_names):
        errors.append(f"registered Tauri command {command!r} has no canonical runtimeApi frontend owner")
    for command in sorted(invoked_names - registered_names):
        errors.append(f"runtimeApi command {command!r} is not registered in generate_handler")

    if errors:
        print("Launcher command surface verification failed:")
        for error in errors:
            print(f" - {error}")
        return 1

    print(
        f"Launcher command surface OK ({len(module_commands)} Rust commands; "
        f"{len(registered_set)} registered; {len(invoked_counts)} frontend commands)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
