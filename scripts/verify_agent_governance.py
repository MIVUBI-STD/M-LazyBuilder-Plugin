#!/usr/bin/env python3
"""Verify agent routing and permission contracts using deterministic fixtures.

This checks policy consistency, not natural-language AI routing or native runtime.
"""

from __future__ import annotations

import json
from pathlib import Path
import re
import sys

MODES = {"INSPECT", "PLAN", "DIAGNOSE", "DEVELOP", "REPAIR", "VALIDATE", "RELEASE"}
READ_ONLY = {"INSPECT", "PLAN", "DIAGNOSE"}
MUTATIONS = {"write", "dispatch_ci", "release"}
UNSAFE_PIPE = re.compile(
    r"\b(?:curl|wget)\b[^\n|]{0,250}\|\s*(?:sh|bash|pwsh|powershell)\b",
    re.IGNORECASE,
)


def load_contract(root: Path, path: str, errors: list[str]) -> dict:
    try:
        data = json.loads((root / path).read_text(encoding="utf-8"))
        if not isinstance(data, dict) or data.get("schema") != 1:
            raise ValueError("expected object with schema 1")
        return data
    except (OSError, UnicodeError, ValueError) as exc:
        errors.append(f"invalid agent contract {path}: {exc}")
        return {}


def permitted(policy: dict, case: dict) -> bool:
    mode, action = case.get("mode"), case.get("action")
    allowed = action in policy.get("modes", {}).get(mode, [])
    for gate in policy.get("required_gates", {}).get(action, []):
        if case.get("gates", {}).get(gate) is not True:
            allowed = False
    if case.get("mode_changed") and case.get("mode_change_approved") is not True:
        allowed = False
    return bool(allowed)


def verify_agent_governance(root: Path, errors: list[str]) -> None:
    policy = load_contract(root, ".agents/permissions/work-lanes.json", errors)
    routing_fixtures = load_contract(root, ".agents/evals/routing-cases.json", errors)
    permission_fixtures = load_contract(root, ".agents/evals/permission-cases.json", errors)
    if not all((policy, routing_fixtures, permission_fixtures)):
        return

    modes = policy.get("modes", {})
    if set(modes) != MODES:
        errors.append("work-lane policy must declare exactly seven modes")
    for mode, actions in modes.items():
        if not isinstance(actions, list) or not all(isinstance(a, str) for a in actions):
            errors.append(f"work-lane actions invalid: {mode}")
            continue
        if mode in READ_ONLY and MUTATIONS.intersection(actions):
            errors.append(f"read-only lane allows mutation: {mode}")
        if mode not in {"VALIDATE", "RELEASE"} and "dispatch_ci" in actions:
            errors.append(f"lane may not dispatch CI: {mode}")
        if mode != "RELEASE" and "release" in actions:
            errors.append(f"lane may not publish releases: {mode}")

    gates = policy.get("required_gates", {})
    for action, required in {
        "write": {"scope_approved", "owner_matches", "branch_local"},
        "dispatch_ci": {"ci_approved", "branch_local"},
        "release": {"release_approved"},
    }.items():
        if not isinstance(gates.get(action), list) or not required.issubset(gates[action]):
            errors.append(f"missing mandatory preflight gate for {action}")
    invariant = policy.get("invariant", {})
    if invariant.get("no_implicit_lane_switch") is not True:
        errors.append("implicit work-lane switching must be forbidden")
    if invariant.get("manual_ci_only") is not True:
        errors.append("CI must remain manual-only")

    try:
        routing = (root / "docs/04-system/skill-routing.md").read_text(encoding="utf-8")
        skills = {p.parent.name for p in (root / ".agents/skills").glob("*/SKILL.md")}
    except (OSError, UnicodeError) as exc:
        errors.append(f"unable to resolve canonical specialist routing: {exc}")
        return

    kinds: set[str] = set()
    covered: set[str] = set()
    cases = routing_fixtures.get("cases", [])
    if not isinstance(cases, list):
        errors.append("routing fixtures must be a list")
        cases = []
    for case in cases:
        if not isinstance(case, dict):
            errors.append("routing fixture must be an object")
            continue
        ident = case.get("id")
        kind, primary, cue = case.get("kind"), case.get("primary"), case.get("cue")
        kinds.add(kind)
        covered.add(primary)
        if primary not in skills or not isinstance(cue, str) or not cue:
            errors.append(f"invalid specialist/cue in routing fixture: {ident}")
            continue
        candidate = re.search(
            rf"(?m)^{re.escape(primary)}\ncandidate:([^\n]+)\nreject:([^\n]+)",
            routing,
        )
        if candidate is None or cue not in candidate.group(1):
            errors.append(f"routing candidate cue does not match canonical owner: {ident}")
        forbidden = case.get("forbidden")
        if forbidden:
            rejected = re.search(
                rf"(?m)^{re.escape(forbidden)}\ncandidate:([^\n]+)\nreject:([^\n]+)",
                routing,
            )
            if forbidden == primary or rejected is None or case.get("reject_cue") not in rejected.group(2):
                errors.append(f"routing negative/ambiguous case is not supported: {ident}")
        receiver = case.get("handoff_to")
        if kind == "cross_domain" and (
            receiver not in skills or receiver == primary or "typed handoff" not in routing.lower()
        ):
            errors.append(f"invalid cross-owner handoff fixture: {ident}")
    if not {"positive", "negative", "ambiguous", "cross_domain"}.issubset(kinds):
        errors.append("routing eval cases must include all four outcome classes")
    if not skills.issubset(covered):
        errors.append(f"routing eval misses specialist: {sorted(skills - covered)}")

    permission_cases = permission_fixtures.get("cases", [])
    if not isinstance(permission_cases, list) or not permission_cases:
        errors.append("permission eval cases must be non-empty")
    else:
        for case in permission_cases:
            if not isinstance(case, dict) or not isinstance(case.get("allow"), bool):
                errors.append("malformed permission eval fixture")
                continue
            if permitted(policy, case) != case["allow"]:
                errors.append(f"permission eval mismatch: {case.get('id')}")

    for skill in (root / ".agents/skills").glob("*/SKILL.md"):
        try:
            if UNSAFE_PIPE.search(skill.read_text(encoding="utf-8")):
                errors.append(f"untrusted network-to-shell recipe in Skill: {skill.relative_to(root)}")
        except (OSError, UnicodeError) as exc:
            errors.append(f"cannot inspect skill safety: {skill.relative_to(root)}: {exc}")


if __name__ == "__main__":
    errors: list[str] = []
    verify_agent_governance(Path(__file__).resolve().parents[1], errors)
    if errors:
        print("\n".join(errors), file=sys.stderr)
        raise SystemExit(1)
    print("Agent governance fixtures PASS (structural only)")
