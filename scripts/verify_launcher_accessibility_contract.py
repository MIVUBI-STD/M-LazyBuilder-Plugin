#!/usr/bin/env python3
"""Verify Launcher keyboard, modal, menu, activity and high-contrast accessibility contracts."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "apps" / "launcher" / "src"


def require(path: Path, *needles: str) -> None:
    text = path.read_text(encoding="utf-8")
    missing = [needle for needle in needles if needle not in text]
    if missing:
        raise SystemExit(f"{path.relative_to(ROOT)} is missing accessibility markers: {missing}")


def main() -> int:
    dialog = SRC / "app" / "dialogFocus.ts"
    details_menu = SRC / "app" / "detailsMenu.ts"
    controlled_menu = SRC / "app" / "controlledMenu.ts"
    activity = SRC / "pages" / "Activity.svelte"
    app = SRC / "App.svelte"
    css = SRC / "styles" / "app.css"

    require(
        dialog,
        "const previousFocus",
        "event.key === 'Escape'",
        "event.key !== 'Tab'",
        "last.focus()",
        "first.focus()",
        "previousFocus?.isConnected",
    )
    require(
        details_menu,
        "aria-haspopup",
        "aria-expanded",
        "event.key === 'ArrowDown'",
        "event.key === 'ArrowUp'",
        "event.key === 'Home'",
        "event.key === 'End'",
        "event.key === 'Escape'",
        "summary?.focus()",
        "pointerdown",
    )
    require(
        controlled_menu,
        "aria-haspopup",
        "aria-expanded",
        "event.key === 'ArrowDown'",
        "event.key === 'ArrowUp'",
        "event.key === 'Home'",
        "event.key === 'End'",
        "event.key === 'Escape'",
        "trigger?.focus()",
        "pointerListening",
    )
    require(
        activity,
        'aria-live="off"',
        'role="alert"',
        '<time datetime=',
        'aria-label={`${progressPercent(operation)} percent complete`}',
    )
    require(
        app,
        'class="skip-link"',
        'href="#main-content"',
        'id="main-content"',
        'tabindex="-1"',
        'aria-current=',
        ".skip-link:focus",
    )
    require(
        css,
        "@media (prefers-reduced-motion: reduce)",
        "@media (forced-colors: active)",
        "outline: 2px solid Highlight",
        "[tabindex]:focus-visible",
    )

    print("Launcher accessibility contract OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
