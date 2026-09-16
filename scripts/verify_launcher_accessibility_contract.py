#!/usr/bin/env python3
"""Verify Launcher modal, disclosure-menu, activity and high-contrast accessibility contracts."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "apps" / "launcher" / "src"


def require(path: Path, *needles: str) -> None:
    text = path.read_text(encoding="utf-8")
    missing = [needle for needle in needles if needle not in text]
    if missing:
        raise SystemExit(f"{path.relative_to(ROOT)} is missing accessibility markers: {missing}")


def main() -> int:
    modal = SRC / "app" / "modalAccessibility.ts"
    disclosure = SRC / "app" / "disclosureAccessibility.ts"
    activity = SRC / "app" / "activityAccessibility.ts"
    main = SRC / "main.ts"
    css = SRC / "styles" / "app.css"

    require(modal, "MutationObserver", "event.key === 'Escape'", "event.key !== 'Tab'", "restoreFocus")
    require(
        disclosure,
        "details.menu, details.row-menu",
        "event.key === 'ArrowDown'",
        "event.key === 'ArrowUp'",
        "event.key === 'Home'",
        "event.key === 'End'",
        "event.key === 'Escape'",
        "summary?.focus()",
    )
    require(
        activity,
        "installActivityAccessibility",
        "aria-live', 'off'",
        "role', 'status'",
        "HISTORY_ROW_SELECTOR",
        "MAX_SEEN = 100",
        "Existing history is context, not a new event",
    )
    require(
        main,
        "installModalAccessibility();",
        "installDisclosureAccessibility();",
        "installActivityAccessibility();",
    )
    require(css, "@media (prefers-reduced-motion: reduce)", "@media (forced-colors: active)", "outline: 2px solid Highlight")

    print("Launcher accessibility contract OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
