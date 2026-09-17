#!/usr/bin/env python3
"""Verify that production Tauri command invocation stays behind the canonical typed bridge."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "apps" / "launcher" / "src"
BRIDGE = SRC / "app" / "bridge"
RUNTIME_API = BRIDGE / "runtimeApi.ts"
INVOKE_RUNTIME = BRIDGE / "invokeRuntime.ts"
APP_API = BRIDGE / "appApi.ts"
FACADE = BRIDGE / "runtimeProductFacade.ts"


def main() -> int:
    runtime = RUNTIME_API.read_text(encoding="utf-8")
    invoke_runtime = INVOKE_RUNTIME.read_text(encoding="utf-8")
    app_api = APP_API.read_text(encoding="utf-8")
    facade = FACADE.read_text(encoding="utf-8")
    errors: list[str] = []

    required_runtime = (
        "import { appApi } from './appApi';",
        "import { clientApi } from './clientApi';",
        "import { pluginApi } from './pluginApi';",
        "import { serverApi } from './serverApi';",
        "import { workspaceApi } from './workspaceApi';",
        "import { worldApi } from './worldApi';",
        "readiness: appApi.readiness",
        "operations: appApi.operations",
    )
    for marker in required_runtime:
        if marker not in runtime:
            errors.append(f"runtimeApi.ts is missing composition marker: {marker}")

    if "@tauri-apps/api/core" in runtime or "invokeRuntime<" in runtime:
        errors.append("runtimeApi.ts must remain composition-only and must not own command invocation")

    required_invoke = (
        "import { invoke } from '@tauri-apps/api/core';",
        "import { RuntimeError, runtimeError } from './errors';",
        "export async function invokeRuntime<T>",
        "throw new RuntimeError(runtimeError(value));",
    )
    for marker in required_invoke:
        if marker not in invoke_runtime:
            errors.append(f"invokeRuntime.ts is missing canonical invocation marker: {marker}")

    required_app_api = (
        "diagnostics_export_support_bundle",
        "exportSupportBundle: () => invokeRuntime<string | null>",
        "readiness: readinessApi",
    )
    for marker in required_app_api:
        if marker not in app_api:
            errors.append(f"appApi.ts is missing bounded app-command marker: {marker}")

    forbidden_facade = (
        "@tauri-apps/api/core",
        "invoke<",
        "invoke(",
        "runtimeError(",
        "diagnostics_export_support_bundle",
    )
    for marker in forbidden_facade:
        if marker in facade:
            errors.append(f"runtimeProductFacade.ts bypasses or duplicates the canonical typed bridge: {marker}")

    if "const productionRuntimeProduct = runtimeApi;" not in facade:
        errors.append("production runtime product is no longer the canonical runtimeApi")

    # Only invokeRuntime.ts may import Tauri command invocation. Bounded *Api modules
    # express typed command ownership by calling invokeRuntime; UI and facade layers
    # must never create a second Tauri command boundary.
    for path in SRC.rglob("*"):
        if not path.is_file() or path.suffix not in {".ts", ".svelte"} or path == INVOKE_RUNTIME:
            continue
        text = path.read_text(encoding="utf-8")
        if "@tauri-apps/api/core" in text:
            errors.append(f"direct Tauri core import outside invokeRuntime.ts: {path.relative_to(ROOT)}")

    if errors:
        print("Launcher runtime bridge contract verification failed:")
        for error in errors:
            print(f" - {error}")
        return 1

    print("Launcher runtime bridge contract OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
