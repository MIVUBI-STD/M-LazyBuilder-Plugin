#!/usr/bin/env python3
"""Validate that every mapped Launcher failure scenario still has a concrete source owner."""

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

    required_ids = {
        "operation-interrupted-by-launcher-exit",
        "create-crash-before-intent-identity-update",
        "adoption-crash-mid-move",
        "duplicate-crash-after-publish-before-register",
        "restore-crash-during-workspace-swap",
        "backup-corruption-before-restore",
        "plugin-input-changes-during-install",
        "second-launcher-instance",
        "close-launcher-during-active-operation",
        "disk-pressure-before-heavy-write",
    }
    missing_required = sorted(required_ids - seen_ids)
    if missing_required:
        errors.append(f"failure matrix is missing required scenarios: {missing_required}")

    if errors:
        print("Launcher failure matrix verification failed:")
        for error in errors:
            print(f" - {error}")
        return 1

    print(f"Launcher failure matrix OK ({len(scenarios)} scenarios)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
