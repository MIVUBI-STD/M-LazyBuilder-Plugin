#!/usr/bin/env python3
"""Validate that every mapped Launcher failure scenario has concrete source and test evidence."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MATRIX = ROOT / "docs" / "launcher-failure-matrix.json"


def main() -> int:
    data = json.loads(MATRIX.read_text(encoding="utf-8"))
    errors: list[str] = []

    if data.get("schemaVersion") != 1:
        errors.append(f"failure matrix schemaVersion must be 1, found {data.get('schemaVersion')!r}")

    scenarios = data.get("scenarios")
    if not isinstance(scenarios, list) or not scenarios:
        errors.append("failure matrix must contain at least one scenario")
        scenarios = []

    seen_ids: set[str] = set()
    scenarios_with_tests = 0
    for scenario in scenarios:
        scenario_id = scenario.get("id")
        owner = scenario.get("owner")
        markers = scenario.get("markers")
        if not isinstance(scenario_id, str) or not scenario_id.strip():
            errors.append("failure matrix scenario has no id")
            continue
        if scenario_id in seen_ids:
            errors.append(f"duplicate failure scenario id: {scenario_id}")
        seen_ids.add(scenario_id)

        if not isinstance(owner, str) or not owner.strip():
            errors.append(f"{scenario_id}: missing source owner")
            continue
        path = ROOT / owner
        if not path.is_file():
            errors.append(f"{scenario_id}: source owner does not exist: {owner}")
            continue

        if not isinstance(markers, list) or not markers:
            errors.append(f"{scenario_id}: no source evidence markers")
            continue
        source = path.read_text(encoding="utf-8")
        missing = [marker for marker in markers if not isinstance(marker, str) or marker not in source]
        if missing:
            errors.append(f"{scenario_id}: owner {owner} is missing markers {missing}")

        forbidden = scenario.get("forbidMarkers", [])
        if not isinstance(forbidden, list):
            errors.append(f"{scenario_id}: forbidMarkers must be an array when present")
        else:
            found = [marker for marker in forbidden if isinstance(marker, str) and marker in source]
            if found:
                errors.append(f"{scenario_id}: owner {owner} contains forbidden regression markers {found}")

        test_markers = scenario.get("testMarkers", [])
        if not isinstance(test_markers, list):
            errors.append(f"{scenario_id}: testMarkers must be an array when present")
        elif test_markers:
            scenarios_with_tests += 1
            if "#[cfg(test)]" not in source:
                errors.append(f"{scenario_id}: declares testMarkers but owner has no Rust test module")
            missing_tests = [marker for marker in test_markers if not isinstance(marker, str) or marker not in source]
            if missing_tests:
                errors.append(f"{scenario_id}: owner {owner} is missing test evidence {missing_tests}")

    required_ids = {
        "operation-interrupted-by-launcher-exit",
        "workspace-library-overlaps-workspace-mutation",
        "workspace-metadata-crash-during-publish",
        "app-data-crash-during-metadata-publish",
        "launcher-settings-crash-during-metadata-publish",
        "creation-intent-crash-during-metadata-publish",
        "adoption-intent-crash-during-metadata-publish",
        "restore-intent-crash-during-metadata-publish",
        "create-crash-before-intent-identity-update",
        "adoption-crash-mid-move",
        "duplicate-crash-after-publish-before-register",
        "delete-crash-during-staging-or-cleanup",
        "restore-crash-during-workspace-swap",
        "backup-crash-during-staging-copy",
        "legacy-backup-staging-upgrade",
        "backup-corruption-before-restore",
        "large-backup-progress-write-amplification",
        "backup-delete-interrupted-mid-remove",
        "world-task-exceeds-frontend-duration",
        "plugin-input-changes-during-install",
        "plugin-mutation-races-server-start",
        "server-config-crash-during-metadata-publish",
        "second-launcher-instance",
        "close-launcher-during-active-operation",
        "disk-pressure-before-heavy-write",
        "world-control-port-conflict-before-paper-start",
    }
    missing_required = sorted(required_ids - seen_ids)
    if missing_required:
        errors.append(f"failure matrix is missing required scenarios: {missing_required}")

    if scenarios_with_tests < 10:
        errors.append(f"failure matrix must retain deterministic test evidence for at least 10 scenarios; found {scenarios_with_tests}")

    if errors:
        print("Launcher failure matrix verification failed:")
        for error in errors:
            print(f" - {error}")
        return 1

    print(f"Launcher failure matrix OK ({len(scenarios)} scenarios; {scenarios_with_tests} with test evidence)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
