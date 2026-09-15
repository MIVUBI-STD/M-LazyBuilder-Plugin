#!/usr/bin/env python3
"""Verify LazyBuilder repository routing and canonical-owner contracts.

This intentionally checks durable repository invariants only. It does not try to
infer runtime correctness or replace domain tests.
"""

from __future__ import annotations

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]

REQUIRED_FILES = [
    "AGENTS.md",
    "GITHUB_RULES.md",
    "CONTEXT.md",
    "docs/README.md",
    "docs/04-system/development-discipline.md",
    "docs/04-system/skill-routing.md",
]

EXPECTED_SKILLS = {
    "lazybuilder-desktop-runtime",
    "lazybuilder-plugin-management",
    "lazybuilder-world-management",
    "lazybuilder-ui",
    "lazybuilder-protocol",
}

FORBIDDEN_STANDALONE_SKILLS = {
    "lazybuilder-development-brief",
    "lazybuilder-java",
    "lazybuilder-paper",
    "lazybuilder-fabric",
    "lazybuilder-rust",
    "lazybuilder-tauri",
    "lazybuilder-maven",
    "lazybuilder-gradle",
    "lazybuilder-testing",
    "lazybuilder-ci",
}

REQUIRED_AGENT_PHRASES = [
    "docs/04-system/development-discipline.md",
    "docs/04-system/skill-routing.md",
    "REMOTE_GITHUB",
    "LOCAL_CODE",
    "LIVE_SERVER",
    "Failure classification",
]

REQUIRED_DISCIPLINE_PHRASES = [
    "Evidence Before Mutation",
    "Failure Classification",
    "Execution Partition",
    "Exact-Commit Proof",
    "UNKNOWN",
]


def fail(errors: list[str], message: str) -> None:
    errors.append(message)


def read_text(relative: str, errors: list[str]) -> str:
    path = ROOT / relative
    if not path.is_file():
        fail(errors, f"missing required file: {relative}")
        return ""
    try:
        return path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        fail(errors, f"required text file is not UTF-8: {relative}")
        return ""


def skill_name(skill_file: Path) -> str | None:
    try:
        text = skill_file.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return None
    match = re.search(r"(?m)^name:\s*([^\s]+)\s*$", text)
    return match.group(1) if match else None


def main() -> int:
    errors: list[str] = []

    for relative in REQUIRED_FILES:
        read_text(relative, errors)

    agents = read_text("AGENTS.md", errors)
    discipline = read_text("docs/04-system/development-discipline.md", errors)
    routing = read_text("docs/04-system/skill-routing.md", errors)

    for phrase in REQUIRED_AGENT_PHRASES:
        if phrase not in agents:
            fail(errors, f"AGENTS.md missing canonical routing marker: {phrase}")

    for phrase in REQUIRED_DISCIPLINE_PHRASES:
        if phrase not in discipline:
            fail(errors, f"development discipline missing contract section/marker: {phrase}")

    skills_root = ROOT / ".agents" / "skills"
    if not skills_root.is_dir():
        fail(errors, "missing .agents/skills directory")
        discovered: set[str] = set()
    else:
        discovered = set()
        for child in skills_root.iterdir():
            if not child.is_dir():
                continue
            skill_file = child / "SKILL.md"
            if not skill_file.is_file():
                fail(errors, f"skill directory has no SKILL.md: {child.relative_to(ROOT)}")
                continue
            declared = skill_name(skill_file)
            if not declared:
                fail(errors, f"skill has no frontmatter name: {skill_file.relative_to(ROOT)}")
                continue
            if declared != child.name:
                fail(
                    errors,
                    f"skill directory/name mismatch: {child.name} declares {declared}",
                )
            discovered.add(declared)

    missing_skills = EXPECTED_SKILLS - discovered
    unexpected_forbidden = FORBIDDEN_STANDALONE_SKILLS & discovered
    if missing_skills:
        fail(errors, f"missing canonical skills: {', '.join(sorted(missing_skills))}")
    if unexpected_forbidden:
        fail(
            errors,
            "forbidden implementation/meta skills found: "
            + ", ".join(sorted(unexpected_forbidden)),
        )

    for skill in sorted(EXPECTED_SKILLS):
        if skill not in agents:
            fail(errors, f"AGENTS.md does not route canonical skill: {skill}")
        if skill not in routing:
            fail(errors, f"skill-routing.md does not describe canonical skill: {skill}")

    forbidden_skill_language = [
        "standalone Skills for Rust, Java, TypeScript, Maven, Gradle, CI, testing",
        "There is no meta Development Brief Skill",
    ]
    if forbidden_skill_language[0] not in agents:
        fail(errors, "AGENTS.md must preserve the no-implementation-skill rule")
    if forbidden_skill_language[1] not in routing:
        fail(errors, "skill-routing.md must preserve the no-meta-skill rule")

    if errors:
        print("Repository contract verification FAILED:", file=sys.stderr)
        for error in errors:
            print(f" - {error}", file=sys.stderr)
        return 1

    print("Repository contract verification PASS")
    print("Canonical skills:", ", ".join(sorted(EXPECTED_SKILLS)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
