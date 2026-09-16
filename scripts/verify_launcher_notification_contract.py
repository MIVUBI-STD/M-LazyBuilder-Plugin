#!/usr/bin/env python3
"""Verify bounded, in-app Launcher operation notification behavior."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "apps" / "launcher" / "src"


def require(path: Path, *needles: str) -> None:
    text = path.read_text(encoding="utf-8")
    missing = [needle for needle in needles if needle not in text]
    if missing:
        raise SystemExit(f"{path.relative_to(ROOT)} is missing notification contract markers: {missing}")


def forbid(path: Path, *needles: str) -> None:
    text = path.read_text(encoding="utf-8")
    found = [needle for needle in needles if needle in text]
    if found:
        raise SystemExit(f"{path.relative_to(ROOT)} contains unsupported notification paths: {found}")


def main() -> int:
    host = SRC / "components" / "OperationNotificationHost.svelte"
    main = SRC / "main.ts"

    require(
        host,
        "runtimeProduct.operations.list()",
        "MAX_NOTICES = 3",
        "ACTIVE_POLL_MS = 2500",
        "IDLE_POLL_MS = 15000",
        "HIDDEN_POLL_MS = 10000",
        "document.querySelector('.activity-page')",
        "knownStates.clear()",
        "seeded = false",
        "TERMINAL.has(operation.state)",
        "operation.correlationId",
        "Reference: {notice.reference}",
        "role=\"status\"",
    )
    forbid(host, "new Notification(", "Notification.requestPermission", "setInterval(")
    require(main, "OperationNotificationHost", "mount(OperationNotificationHost, { target: document.body });")

    print("Launcher notification contract OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
