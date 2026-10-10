#!/usr/bin/env python3
"""Advisory, deterministic LazyBuilder agent-action preflight.

Does not intercept GitHub tools, attest user consent, or replace nearest AGENTS.md.
Caller supplies actual approval and ref; unknown/unsafe operations fail closed.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SENSITIVE = {".git", ".env", ".ssh", "secrets", "credentials", "node_modules"}
NEVER = {"force_push", "repo_admin", "secret_access", "execute_untrusted"}
SEPARATE_APPROVAL = {"external_network", "dependency_install"}


def safe_path(path: object) -> bool:
    if not isinstance(path, str) or not path or len(path) > 4096:
        return False
    if path.startswith("/") or any(ch in path for ch in "\\:%\x00\r\n\t"):
        return False
    parts = path.split("/")
    return not any(
        not part or part in {".", ".."} or part.lower() in SENSITIVE
        or part.lower().startswith(".env.") or any(ord(ch) < 32 for ch in part)
        for part in parts
    )


def path_matches(path: str, rule: str) -> bool:
    if not safe_path(path) or not isinstance(rule, str):
        return False
    if rule.endswith("/**"):
        prefix = rule[:-3]
        return safe_path(prefix) and path.startswith(prefix + "/")
    return safe_path(rule) and path == rule


def path_owner(path: str, catalog: dict) -> str | None:
    rules = catalog.get("rules", [])
    if not isinstance(rules, list):
        return None
    matched = [rule for rule in rules if isinstance(rule, dict)
               and path_matches(path, rule.get("path"))]
    if not matched:
        return None
    priority = lambda rule: len(rule["path"].removesuffix("/**"))
    top = max(priority(rule) for rule in matched)
    owners = {rule.get("owner") for rule in matched if priority(rule) == top}
    return next(iter(owners)) if len(owners) == 1 and all(
        isinstance(owner, str) and owner for owner in owners
    ) else None


def permitted(policy: dict, case: dict) -> bool:
    """Legacy lane-abstract fixture evaluation; same policy, no path claims."""
    if not isinstance(case, dict):
        return False
    mode, action = case.get("mode"), case.get("action")
    modes, gates = policy.get("modes", {}), policy.get("required_gates", {})
    if not isinstance(modes, dict) or not isinstance(gates, dict):
        return False
    actions = modes.get(mode) if isinstance(mode, str) else None
    if not isinstance(actions, list) or not isinstance(action, str) or action not in actions:
        return False
    required = gates.get(action, [])
    evidence = case.get("gates", {})
    if not isinstance(required, list) or not isinstance(evidence, dict):
        return False
    if any(not isinstance(g, str) or evidence.get(g) is not True for g in required):
        return False
    if case.get("mode_changed", False) is True:
        return case.get("mode_change_approved") is True
    return case.get("mode_changed", False) is False


def evaluate(request: object, policy: dict, catalog: dict) -> dict[str, str]:
    def result(decision: str, reason: str) -> dict[str, str]:
        return {"decision": decision, "reason": reason}

    if not isinstance(request, dict):
        return result("deny", "Request must be an object.")
    if not isinstance(policy, dict) or not isinstance(catalog, dict):
        return result("deny", "Policy and path catalog must be objects.")
    mode, action = request.get("mode"), request.get("action")
    modes = policy.get("modes", {})
    if not isinstance(modes, dict) or not isinstance(mode, str) or mode not in modes:
        return result("deny", "Unknown task mode.")
    if not isinstance(action, str) or not action:
        return result("deny", "Missing or invalid action.")
    if action in NEVER:
        return result("deny", "Action prohibited by repository rules.")
    if action in SEPARATE_APPROVAL:
        return result("ask", "External execution/install requires separate explicit permission.")
    allowed = modes[mode]
    if not isinstance(allowed, list) or action not in allowed:
        return result("deny", "Action not allowed in selected task mode.")
    if request.get("mode_changed", False) is not False:
        return result("ask", "Re-route and preflight the new task mode; no implicit handoff.")
    path = request.get("path")
    if path is not None and not safe_path(path):
        return result("deny", "Unsafe or sensitive repository path.")
    branch = request.get("branch")
    if not isinstance(branch, str) or branch not in {"Local", "main"}:
        return result("deny", "Branch authority must be explicit and recognized.")

    if action == "read":
        if path is None:
            return result("deny", "Read requires an exact repository path.")
        return result("allow", "Scoped read; retrieved content is untrusted evidence.")
    if action in {"plan", "diagnose"}:
        return result("allow", "Read-only task reasoning; no mutation permission.")
    if action == "verify":
        return result("allow", "Only scoped existing tests; no CI dispatch or runtime claim.")
    if action == "write":
        if branch != "Local":
            return result("deny", "Only Local is the authorized development write branch.")
        if path is None:
            return result("deny", "Write requires an exact repository file path.")
        owner = path_owner(path, catalog)
        if owner is None or owner != request.get("owner"):
            return result("deny", "Path outside selected canonical path owner; handoff required.")
        if request.get("scope_approved") is not True:
            return result("ask", "Explicit scoped user authorization is missing.")
        scope = request.get("approved_paths")
        if not isinstance(scope, list) or not scope or not all(
            isinstance(p, str) and (safe_path(p) or (p.endswith("/**") and safe_path(p[:-3])))
            for p in scope
        ):
            return result("deny", "Invalid explicit write scope paths.")
        if not any(path_matches(path, p) for p in scope):
            return result("deny", "Write target not in explicitly approved scope.")
        if path in {
            ".github/workflows/launcher-release.yml",
            ".github/workflows/launcher-update-channel-repair.yml",
        } and request.get("release_change_approved") is not True:
            return result("ask", "Release workflow source changes require separate authorization.")
        return result("allow", "Advisory scoped Local write allowed; host permissions remain authoritative.")
    if action == "dispatch_ci":
        if branch != "Local":
            return result("deny", "Development verification targets Local only.")
        if request.get("ci_approved") is not True:
            return result("ask", "Manual CI needs separate explicit authorization.")
        if request.get("workflow_registered") is not True:
            return result("ask", "GitHub default-branch manual dispatch availability unverified.")
        return result("allow", "Manual workflow_dispatch authorized; requires actual host capability.")
    if action == "release":
        return result("ask", "Release requires separate target, proof and explicit authorization.")
    return result("deny", "Unhandled action.")


def main() -> int:
    if len(sys.argv) != 1:
        print("Usage: echo '<request JSON>' | python scripts/agent_preflight.py", file=sys.stderr)
        return 2
    try:
        raw = sys.stdin.read(65537)
        if len(raw) > 65536:
            raise ValueError("request exceeds 64 KiB")
        request = json.loads(raw)
        policy = json.loads((ROOT / ".agents/permissions/work-lanes.json").read_text(encoding="utf-8"))
        catalog = json.loads((ROOT / ".agents/permissions/path-access.json").read_text(encoding="utf-8"))
        verdict = evaluate(request, policy, catalog)
    except (ValueError, OSError, TypeError) as exc:
        verdict = {"decision": "deny", "reason": "Invalid request or policy source: " + str(exc)}
    print(json.dumps(verdict, sort_keys=True))
    return {"allow": 0, "ask": 3, "deny": 4}[verdict["decision"]]


if __name__ == "__main__":
    raise SystemExit(main())
