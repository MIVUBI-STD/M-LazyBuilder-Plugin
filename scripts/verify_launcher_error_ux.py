#!/usr/bin/env python3
"""Verify recovery-heavy Launcher surfaces preserve structured backend errors."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LAUNCHER = ROOT / "apps" / "launcher" / "src"


def require(path: Path, *needles: str) -> None:
    text = path.read_text(encoding="utf-8")
    missing = [needle for needle in needles if needle not in text]
    if missing:
        raise SystemExit(f"{path.relative_to(ROOT)} is missing structured error UX markers: {missing}")


def forbid(path: Path, *needles: str) -> None:
    text = path.read_text(encoding="utf-8")
    found = [needle for needle in needles if needle in text]
    if found:
        raise SystemExit(f"{path.relative_to(ROOT)} restored string-only error handling: {found}")


def main() -> int:
    presentation = LAUNCHER / "app" / "runtimeErrorPresentation.ts"
    notice = LAUNCHER / "components" / "RuntimeErrorNotice.svelte"
    backup = LAUNCHER / "pages" / "BackupPanel.svelte"
    health = LAUNCHER / "pages" / "HealthPanel.svelte"

    require(presentation, "RuntimeErrorPresentation", "presentRuntimeError", "correlationId", "recoverable", "action")
    require(notice, "error.details", "error.correlationId", "error.recoverable && error.action", "Next step")
    for path in (backup, health):
        require(path, "RuntimeErrorNotice", "presentRuntimeError", "RuntimeErrorPresentation")
        forbid(path, "function friendlyError(")

    print("Launcher structured RuntimeError UX contract OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
