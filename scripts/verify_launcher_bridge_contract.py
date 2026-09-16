#!/usr/bin/env python3
"""Verify that production Tauri command invocation stays behind the canonical typed bridge."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BRIDGE = ROOT / "apps" / "launcher" / "src" / "app" / "bridge"
RUNTIME_API = BRIDGE / "runtimeApi.ts"
FACADE = BRIDGE / "runtimeProductFacade.ts"


def main() -> int:
    runtime = RUNTIME_API.read_text(encoding="utf-8")
    facade = FACADE.read_text(encoding="utf-8")
    errors: list[str] = []

    required_runtime = (
        "import { invoke } from '@tauri-apps/api/core';",
        "async function invokeRuntime<T>",
        "diagnostics_export_support_bundle",
        "exportSupportBundle: () => invokeRuntime<string | null>",
    )
    for marker in required_runtime:
        if marker not in runtime:
            errors.append(f"runtimeApi.ts is missing canonical bridge marker: {marker}")

    forbidden_facade = (
        "@tauri-apps/api/core",
        "invoke<",
        "invoke(",
        "runtimeError(",
        "new RuntimeError",
    )
    for marker in forbidden_facade:
        if marker in facade:
            errors.append(f"runtimeProductFacade.ts bypasses the canonical typed bridge: {marker}")

    if "const productionRuntimeProduct = runtimeApi;" not in facade:
        errors.append("production runtime product is no longer the canonical runtimeApi")

    # The facade may override deterministic preview behavior, but production command
    # names must be owned by runtimeApi rather than repeated in facade code.
    if "diagnostics_export_support_bundle" in facade:
        errors.append("support bundle command name is duplicated outside runtimeApi")

    if errors:
        print("Launcher runtime bridge contract verification failed:")
        for error in errors:
            print(f" - {error}")
        return 1

    print("Launcher runtime bridge contract OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
