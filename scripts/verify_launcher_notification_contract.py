#!/usr/bin/env python3
"""Verify bounded, in-app Launcher operation attention behavior."""

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
    attention = SRC / "components" / "OperationAttention.svelte"
    app = SRC / "App.svelte"

    require(
        attention,
        "runtimeProduct.operations.list()",
        "ACTIVE_POLL_MS = 3000",
        "IDLE_POLL_MS = 15000",
        "SUCCESS_DISMISS_MS = 7000",
        "initializedAtUnixSeconds",
        "transitionedToTerminal",
        "completedBetweenPolls",
        "document.hidden",
        "refreshInFlight",
        "if (suppressed)",
        "aria-live={notice.state === 'SUCCEEDED' ? 'polite' : 'assertive'}",
        "Open Activity",
    )
    forbid(attention, "new Notification(", "Notification.requestPermission", "setInterval(")
    require(
        app,
        "import OperationAttention from './components/OperationAttention.svelte';",
        "<OperationAttention",
        "suppressed={globalPage === 'Activity'}",
        "onOpenActivity={openActivityFromAttention}",
    )

    print("Launcher notification contract OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
