#!/usr/bin/env python3
"""Ensure every canonical Launcher source contract is wired into both verification workflows."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS = {
    "Launcher Verify": ROOT / ".github" / "workflows" / "launcher-verify.yml",
    "Verify": ROOT / ".github" / "workflows" / "verify.yml",
}

# These are the canonical source-level contracts for the Launcher. Keep this list
# explicit so the focused/manual Launcher Verify and the integrated Verify gate
# cannot drift apart.
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
    "verify_launcher_command_surface.py",
    "verify_launcher_operation_coverage.py",
    "verify_launcher_metadata_durability.py",
    "verify_launcher_contract_wiring.py",
)

# Python contract/tooling scripts that should be syntax-checked in the focused
# Launcher Verify secretless tooling step. The integrated Verify workflow executes
# the canonical contract scripts directly, so a second py_compile block is not
# required there.
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
    "verify_launcher_command_surface.py",
    "verify_launcher_operation_coverage.py",
    "verify_launcher_metadata_durability.py",
    "verify_launcher_contract_wiring.py",
)


def main() -> int:
    workflows = {name: path.read_text(encoding="utf-8") for name, path in WORKFLOWS.items()}
    errors: list[str] = []

    for contract in REQUIRED_DIRECT_CONTRACTS:
        path = ROOT / "scripts" / contract
        if not path.is_file():
            errors.append(f"required Launcher contract script is missing: scripts/{contract}")
            continue
        marker = f"python scripts/{contract}"
        for workflow_name, workflow in workflows.items():
            if marker not in workflow:
                errors.append(f"{workflow_name} does not invoke scripts/{contract}")

    launcher_verify = workflows["Launcher Verify"]
    pycompile_block = launcher_verify.split("python -m py_compile", 1)
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

    print(
        "Launcher contract wiring OK "
        f"({len(REQUIRED_DIRECT_CONTRACTS)} direct contracts across {len(WORKFLOWS)} workflows)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
