#!/usr/bin/env python3
"""Verify agent routing and permission contracts using deterministic fixtures.

This checks policy consistency, not natural-language AI routing or native runtime.
"""

from __future__ import annotations

import json
from pathlib import Path
import re
import subprocess
import sys

from agent_preflight import evaluate, path_matches, path_owner, permitted, safe_path

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


def verify_agent_governance(root: Path, errors: list[str]) -> None:
    policy = load_contract(root, ".agents/permissions/work-lanes.json", errors)
    routing_fixtures = load_contract(root, ".agents/evals/routing-cases.json", errors)
    permission_fixtures = load_contract(root, ".agents/evals/permission-cases.json", errors)
    path_catalog = load_contract(root, ".agents/permissions/path-access.json", errors)
    preflight_fixtures = load_contract(root, ".agents/evals/preflight-cases.json", errors)
    if not all((policy, routing_fixtures, permission_fixtures, path_catalog, preflight_fixtures)):
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
        configured = gates.get(action)
        if not isinstance(configured, list) or set(configured) != required or len(configured) != len(required):
            errors.append(f"unexpected or missing mandatory preflight gate for {action}")
    invariant = policy.get("invariant", {})
    if not isinstance(invariant, dict):
        errors.append("work-lane invariant must be an object")
        return
    if invariant.get("no_implicit_lane_switch") is not True:
        errors.append("implicit work-lane switching must be forbidden")
    if invariant.get("manual_ci_only") is not True:
        errors.append("Agent-initiated CI dispatch must remain explicit and authorized")
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
    languages: set[str] = set()
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
        lang, prompt = case.get("language"), case.get("prompt")
        if not isinstance(lang, str) or lang not in {"id", "en"} or not isinstance(prompt, str) or len(prompt.strip()) < 24:
            errors.append(f"routing fixture missing a realistic bilingual prompt: {ident}")
        else:
            languages.add(lang)
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
    if languages != {"id", "en"}:
        errors.append("routing prompt corpus needs Indonesian and English cases")
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

    # Path policy is an executable authorization projection, not a competing
    # semantic ownership source; verify the seven runtime roots exist and agree
    # with the canonical architecture document before trusting the projection.
    path_rules = path_catalog.get("rules")
    if not isinstance(path_rules, list) or not path_rules:
        errors.append("preflight path-access rules must be a non-empty list")
        path_rules = []
    seen_patterns: set[str] = set()
    for rule in path_rules:
        if not isinstance(rule, dict):
            errors.append("preflight path rule must be an object")
            continue
        pattern, owner = rule.get("path"), rule.get("owner")
        if not isinstance(pattern, str) or not isinstance(owner, str) or not owner:
            errors.append("preflight path rule needs a path and owner")
            continue
        is_prefix = pattern.endswith("/**")
        if not safe_path(pattern[:-3] if is_prefix else pattern):
            errors.append(f"unsafe preflight path rule: {pattern}")
        if pattern in seen_patterns:
            errors.append(f"duplicate preflight path rule: {pattern}")
        seen_patterns.add(pattern)
        anchor = pattern[:-3] if is_prefix else pattern
        if safe_path(anchor) and not (root / anchor).exists():
            errors.append(f"stale preflight path rule with no owned source: {pattern}")

    architecture = (root / "docs/04-system/module-boundaries.md").read_text(encoding="utf-8")
    for source_root, owner in {
        "apps/launcher": "desktop-runtime",
        "plugins/world-manager": "paper-world",
        "plugins/utilities-manager": "paper-utilities",
        "mods/map-manager": "fabric-map",
        "mods/utility-manager": "fabric-utility",
        "mods/performance-manager": "fabric-performance",
        "mods/builder-utilities": "fabric-builder",
        "shared/protocol": "shared-protocol",
    }.items():
        if not (root / source_root).is_dir():
            errors.append(f"missing canonical source owner directory: {source_root}")
        if source_root + "/" not in architecture:
            errors.append(f"source owner absent from canonical module boundaries: {source_root}")
        if path_owner(source_root + "/__preflight_probe__", path_catalog) != owner:
            errors.append(f"preflight path ownership drift: {source_root} must map to {owner}")

    cases = preflight_fixtures.get("cases", [])
    if not isinstance(cases, list) or len(cases) < 25:
        errors.append("preflight cases must cover representative positive and negative actions")
        cases = []
    ids: set[str] = set()
    decisions: set[str] = set()
    for case in cases:
        if not isinstance(case, dict):
            errors.append("preflight fixture must be an object")
            continue
        ident, expected = case.get("id"), case.get("decision")
        if not isinstance(ident, str) or not ident or ident in ids:
            errors.append(f"preflight fixture id missing/duplicate: {ident}")
        else:
            ids.add(ident)
        if not isinstance(expected, str) or expected not in {"allow", "ask", "deny"}:
            errors.append(f"preflight fixture has unknown decision: {ident}")
            continue
        actual = evaluate(case.get("request"), policy, path_catalog)
        if actual["decision"] != expected:
            errors.append(f"preflight {ident}: expected {expected}, got {actual['decision']}")
        decisions.add(expected)
    if decisions != {"allow", "ask", "deny"}:
        errors.append("preflight fixture corpus must exercise allow, ask and deny")
    if not all(path_matches("mods/map-manager/src/main/java/X.java", p) for p in ["mods/map-manager/**"]):
        errors.append("preflight path prefix matching regressed")

    # One cheap Git index read verifies *all tracked paths* have a canonical
    # permission owner; no source files are read and no generated folders
    # are scanned. Source-only ZIPs have no Git index, so their evidence
    # ceiling is limited to the explicit owner and fixture checks above.
    if (root / ".git").exists():
        try:
            result = subprocess.run(
                ["git", "ls-files", "--cached", "-z"],
                cwd=root, capture_output=True, check=False, timeout=15,
            )
        except (OSError, subprocess.TimeoutExpired) as exc:
            errors.append(f"cannot audit tracked path ownership: {exc}")
        else:
            if result.returncode != 0:
                errors.append(
                    "Git tracked-path audit failed: "
                    + result.stderr.decode("utf-8", "replace").strip()[:300]
                )
            else:
                tracked = result.stdout.split(b"\0")
                unowned: list[str] = []
                checked = 0
                for raw in tracked:
                    if not raw:
                        continue
                    checked += 1
                    try:
                        relative = raw.decode("utf-8")
                    except UnicodeDecodeError:
                        errors.append("non-UTF-8 tracked repository path")
                        continue
                    if not safe_path(relative) or path_owner(relative, path_catalog) is None:
                        unowned.append(relative)
                if checked == 0:
                    errors.append("Git tracked-path audit found no repository files")
                if unowned:
                    errors.append(
                        f"{len(unowned)} tracked repository paths have no safe canonical "
                        f"permission owner: {', '.join(unowned[:12])}"
                    )

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
