#!/usr/bin/env python3
"""Ensure every canonical Launcher source contract is actually wired into Launcher Verify."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github" / "workflows" / "launcher-verify.yml"

# These are the canonical source-level contracts for the Launcher. Keep this list
# explicit: updater manifest validation is invoked inside the updater tooling step,
# while these contracts must each have their own direct workflow invocation.
REQUIRED_DIRECT_CONTRACTS = (
    "verify_local_launcher_contract.py",
    "verify_launcher_release_contract.py",
    "verify_launcher_hardening_contract.py",
    "verify_compatibility.py",
    "verify_launcher_data_preservation_contract.py",
    "verify_launcher_failure_matrix.py",
    "verify_launcher_scalability_contract.py",
    "verify_launcher_ownership_contract.py",
    "verify_launcher_error_ux.py",
    "verify_launcher_accessibility_contract.py",
    "verify_launcher_notification_contract.py",
    "verify_launcher_bridge_contract.py",
    "verify_launcher_contract_wiring.py",
)

# Python contract/tooling scripts that should be syntax-checked in the secretless
# tooling step. Local packaging/release verifiers run directly and do not need to be
# duplicated in this list merely for coverage.
REQUIRED_PYCOMPILE = (
    "build_launcher_update_manifest.py",
    "verify_launcher_update_manifest.py",
    "set_launcher_version.py",
    "verify_launcher_hardening_contract.py",
    "verify_compatibility.py",
    "verify_launcher_data_preservation_contract.py",
    "verify_launcher_failure_matrix.py",
    "verify_launcher_scalability_contract.py",
    "verify_launcher_ownership_contract.py",
    "verify_launcher_error_ux.py",
    "verify_launcher_accessibility_contract.py",
    "verify_launcher_notification_contract.py",
    "verify_launcher_bridge_contract.py",
    "verify_launcher_contract_wiring.py",
)


def main() -> int:
    workflow = WORKFLOW.read_text(encoding="utf-8")
    errors: list[str] = []

    for name in REQUIRED_DIRECT_CONTRACTS:
        path = ROOT / "scripts" / name
        if not path.is_file():
            errors.append(f"required Launcher contract script is missing: scripts/{name}")
            continue
        marker = f"python scripts/{name}"
        if marker not in workflow:
            errors.append(f"Launcher Verify does not invoke scripts/{name}")

    pycompile_block = workflow.split("python -m py_compile", 1)
    if len(pycompile_block) != 2:
        errors.append("Launcher Verify has no python -m py_compile tooling block")
    else:
        tooling = pycompile_block[1]
        for name in REQUIRED_PYCOMPILE:
            if f"scripts/{name}" not in tooling:
                errors.append(f"Launcher Verify py_compile block is missing scripts/{name}")

    if errors:
        print("Launcher contract wiring verification failed:")
        for error in errors:
            print(f" - {error}")
        return 1

    print(f"Launcher contract wiring OK ({len(REQUIRED_DIRECT_CONTRACTS)} direct contracts)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
