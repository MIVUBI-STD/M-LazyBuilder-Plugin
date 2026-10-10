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
CANONICAL_ACTIONS = {
    "INSPECT": {"read"},
    "PLAN": {"read", "plan"},
    "DIAGNOSE": {"read", "plan", "diagnose"},
    "DEVELOP": {"read", "plan", "diagnose", "write", "verify"},
    "REPAIR": {"read", "plan", "diagnose", "write", "verify"},
    "VALIDATE": {"read", "verify", "dispatch_ci"},
    "RELEASE": {"read", "verify", "dispatch_ci", "release"},
}
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
    """Deny malformed/unknown mode/action/gates and any unapproved lane switch."""
    mode, action = case.get("mode"), case.get("action")
    modes = policy.get("modes", {})
    required = policy.get("required_gates", {})
    if not isinstance(mode, str) or not isinstance(action, str):
        return False
    if not isinstance(modes, dict) or not isinstance(required, dict):
        return False
    allowed_actions = modes.get(mode)
    if not isinstance(allowed_actions, list) or action not in allowed_actions:
        return False
    conditions = required.get(action, [])
    gates = case.get("gates", {})
    if not isinstance(conditions, list) or not isinstance(gates, dict):
        return False
    if any(gates.get(gate) is not True for gate in conditions):
        return False
    if case.get("mode_changed", False) is True:
        return case.get("mode_change_approved") is True
    return case.get("mode_changed", False) is False


def verify_agent_governance(root: Path, errors: list[str]) -> None:
    policy = load_contract(root, ".agents/permissions/work-lanes.json", errors)
    routing_fixtures = load_contract(root, ".agents/evals/routing-cases.json", errors)
    permission_fixtures = load_contract(root, ".agents/evals/permission-cases.json", errors)
    if not all((policy, routing_fixtures, permission_fixtures)):
        return

    modes = policy.get("modes", {})
    if not isinstance(modes, dict):
        errors.append("work-lane modes must be an object")
        return
    if set(modes) != MODES:
        errors.append("work-lane policy must declare exactly seven modes")
    for mode, actions in modes.items():
        if not isinstance(actions, list) or not all(isinstance(a, str) for a in actions):
            errors.append(f"work-lane actions invalid: {mode}")
            continue
        if len(actions) != len(set(actions)) or set(actions) != CANONICAL_ACTIONS.get(mode, set()):
            errors.append(f"unexpected or missing work-lane action: {mode}")
        if mode in READ_ONLY and MUTATIONS.intersection(actions):
            errors.append(f"read-only lane allows mutation: {mode}")
        if mode not in {"VALIDATE", "RELEASE"} and "dispatch_ci" in actions:
            errors.append(f"lane may not dispatch CI: {mode}")
        if mode != "RELEASE" and "release" in actions:
            errors.append(f"lane may not publish releases: {mode}")

    gates = policy.get("required_gates", {})
    if not isinstance(gates, dict):
        errors.append("work-lane required_gates must be an object")
        return
    if set(gates) != MUTATIONS:
        errors.append("work-lane permission gates must cover only write, dispatch_ci and release")
    for action, required in {
        "write": {"scope_approved", "owner_matches", "branch_local"},
        "dispatch_ci": {"ci_approved", "branch_local"},
        "release": {"release_approved"},
    }.items():
        if not isinstance(gates.get(action), list) or not required.issubset(gates[action]):
            errors.append(f"missing mandatory preflight gate for {action}")
    invariant = policy.get("invariant", {})
    if not isinstance(invariant, dict):
        errors.append("work-lane invariant must be an object")
        return
    if invariant.get("no_implicit_lane_switch") is not True:
        errors.append("implicit work-lane switching must be forbidden")
    if invariant.get("manual_ci_only") is not True:
        errors.append("CI must remain manual-only")
    read_only_modes = invariant.get("read_only_modes")
    if not isinstance(read_only_modes, list) or not all(
        isinstance(mode, str) for mode in read_only_modes
    ) or set(read_only_modes) != READ_ONLY:
        errors.append("read-only work-lane declarations must match policy")

    try:
        routing = (root / "docs/04-system/skill-routing.md").read_text(encoding="utf-8")
        skills = {p.parent.name for p in (root / ".agents/skills").glob("*/SKILL.md")}
    except (OSError, UnicodeError) as exc:
        errors.append(f"unable to resolve canonical specialist routing: {exc}")
        return

    kinds: set[str] = set()
    covered: set[str] = set()
    seen_routing_ids: set[str] = set()
    cases = routing_fixtures.get("cases", [])
    if not isinstance(cases, list):
        errors.append("routing fixtures must be a list")
        cases = []
    for case in cases:
        if not isinstance(case, dict):
            errors.append("routing fixture must be an object")
            continue
        ident = case.get("id")
        if not isinstance(ident, str) or not ident or ident in seen_routing_ids:
            errors.append(f"routing fixture has missing/duplicate ID: {ident}")
        else:
            seen_routing_ids.add(ident)
        kind, primary, cue = case.get("kind"), case.get("primary"), case.get("cue")
        if not isinstance(kind, str) or kind not in {"positive", "negative", "ambiguous", "cross_domain"}:
            errors.append(f"routing fixture has unknown class: {ident}")
            continue
        kinds.add(kind)
        if isinstance(primary, str):
            covered.add(primary)
        if kind in {"negative", "ambiguous"} and (
            not isinstance(case.get("forbidden"), str)
            or not isinstance(case.get("reject_cue"), str)
            or not case["reject_cue"]
        ):
            errors.append(f"routing negative/ambiguous case requires forbidden owner and rejection: {ident}")
        if not isinstance(primary, str) or primary not in skills or not isinstance(cue, str) or not cue:
            errors.append(f"invalid specialist/cue in routing fixture: {ident}")
            continue
        candidate = re.search(
            rf"(?m)^{re.escape(primary)}\ncandidate:([^\n]+)\nreject:([^\n]+)",
            routing,
        )
        if candidate is None or cue not in candidate.group(1):
            errors.append(f"routing candidate cue does not match canonical owner: {ident}")
        forbidden = case.get("forbidden")
        if isinstance(forbidden, str) and forbidden:
            rejected = re.search(
                rf"(?m)^{re.escape(forbidden)}\ncandidate:([^\n]+)\nreject:([^\n]+)",
                routing,
            )
            reject_cue = case.get("reject_cue")
            if forbidden == primary or rejected is None or (
                not isinstance(reject_cue, str) or reject_cue not in rejected.group(2)
            ):
                errors.append(f"routing negative/ambiguous case is not supported: {ident}")
        receiver = case.get("handoff_to")
        if kind == "cross_domain" and (
            not isinstance(receiver, str) or receiver not in skills or receiver == primary
            or "typed handoff" not in routing.lower()
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
        seen_permission_ids: set[str] = set()
        coverage: set[tuple[str, str, bool]] = set()
        gate_denials: set[tuple[str, str]] = set()
        for case in permission_cases:
            if not isinstance(case, dict) or not isinstance(case.get("allow"), bool):
                errors.append("malformed permission eval fixture")
                continue
            ident = case.get("id")
            if not isinstance(ident, str) or not ident or ident in seen_permission_ids:
                errors.append(f"permission fixture missing/duplicate ID: {ident}")
            else:
                seen_permission_ids.add(ident)
            mode, action = case.get("mode"), case.get("action")
            if isinstance(mode, str) and isinstance(action, str):
                coverage.add((mode, action, case["allow"]))
                required_for_action = gates.get(action, [])
                conditions = case.get("gates", {})
                if isinstance(required_for_action, list) and isinstance(conditions, dict):
                    for gate in required_for_action:
                        if conditions.get(gate) is False and all(
                            conditions.get(other) is True for other in required_for_action if other != gate
                        ):
                            gate_denials.add((action, gate))
            if permitted(policy, case) != case["allow"]:
                errors.append(f"permission eval mismatch: {ident}")
        for required_case in {
            ("INSPECT", "write", False),
            ("DIAGNOSE", "write", False),
            ("DEVELOP", "write", True),
            ("REPAIR", "write", True),
            ("VALIDATE", "write", False),
            ("VALIDATE", "dispatch_ci", True),
            ("VALIDATE", "dispatch_ci", False),
            ("RELEASE", "release", True),
            ("RELEASE", "release", False),
            ("DEVELOP", "release", False),
            ("DEVELOP", "admin_repository", False),
            ("UNKNOWN", "read", False),
        }:
            if required_case not in coverage:
                errors.append(f"agent permission evaluation missing important outcome: {required_case}")
        for action, required in gates.items():
            if action in MUTATIONS and isinstance(required, list):
                for gate in required:
                    if (action, gate) not in gate_denials:
                        errors.append(f"missing negative permission fixture for {action}/{gate}")

    # Referenced procedures are agent input too; scanning only SKILL.md misses
    # a network-to-shell instruction supplied by a nested reference.
    skills_root = root / ".agents/skills"
    if not skills_root.is_dir():
        errors.append("missing canonical Skills root for supply-chain scan")
        return
    for entry in sorted(skills_root.rglob("*")):
        if entry.is_symlink():
            errors.append(f"Skill supply-chain symlink requires review: {entry.relative_to(root)}")
            continue
        if not entry.is_file():
            continue
        if entry.suffix.lower() != ".md":
            errors.append(
                f"unreviewed executable/non-Markdown Skill asset: {entry.relative_to(root)}"
            )
            continue
        try:
            if UNSAFE_PIPE.search(entry.read_text(encoding="utf-8")):
                errors.append(
                    f"untrusted network-to-shell recipe in Skill: {entry.relative_to(root)}"
                )
        except (OSError, UnicodeError) as exc:
            errors.append(f"cannot inspect Skill source: {entry.relative_to(root)}: {exc}")


if __name__ == "__main__":
    errors: list[str] = []
    verify_agent_governance(Path(__file__).resolve().parents[1], errors)
    if errors:
        print("\n".join(errors), file=sys.stderr)
        raise SystemExit(1)
    print("Agent governance fixtures PASS (structural only)")
