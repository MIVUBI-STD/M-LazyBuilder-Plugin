#!/usr/bin/env python3
"""Source-level uninstall/reinstall preservation invariants for LazyBuilder Launcher."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LAUNCHER = ROOT / "apps" / "launcher"
RUST = LAUNCHER / "src-tauri" / "src"


def read(path: Path) -> str:
    if not path.is_file():
        raise SystemExit(f"required file missing: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def main() -> int:
    errors: list[str] = []

    tauri = json.loads(read(LAUNCHER / "src-tauri" / "tauri.conf.json"))
    if tauri.get("identifier") != "com.halokaryamedia.lazybuilder":
        errors.append("Tauri application identifier changed; reinstall continuity requires an explicit migration plan")

    nsis = tauri.get("bundle", {}).get("windows", {}).get("nsis", {})
    if nsis.get("installMode") != "currentUser":
        errors.append("Launcher NSIS installMode must remain currentUser unless data-preservation review is updated")

    forbidden_nsis_keys = {"installerHooks", "installModeScript", "uninstallScript"}
    present = sorted(forbidden_nsis_keys.intersection(nsis))
    if present:
        errors.append(f"Custom NSIS lifecycle hooks require explicit preservation review: {present}")

    owners = {
        "settings": RUST / "engine" / "launcher_settings.rs",
        "operations": RUST / "engine" / "operations.rs",
        "instance": RUST / "engine" / "app_instance.rs",
        "migrations": RUST / "engine" / "app_data_migrations.rs",
        "registry": RUST / "engine" / "workspace_registry.rs",
        "restore": RUST / "engine" / "server_restore.rs",
        "creation": RUST / "engine" / "workspace_creation.rs",
        "adoption": RUST / "engine" / "adoption.rs",
    }
    for label, path in owners.items():
        source = read(path)
        if "LazyBuilder" not in source:
            errors.append(f"{label} persistence owner no longer clearly targets LazyBuilder application data")

    backup_source = read(RUST / "engine" / "server_backups.rs")
    if '.join(".lazybuilder-backups")' not in backup_source:
        errors.append("Server restore points are no longer stored in the explicit sibling .lazybuilder-backups location")

    startup = read(RUST / "engine" / "startup.rs")
    for required in (
        "workspace_creation::recover_pending_creations()",
        "adoption::recover_pending_adoptions()",
        "workspace_registry::recover_pending_duplicates()",
        "server_restore::recover_pending_restores()",
        "reopen_most_recent_workspace",
    ):
        if required not in startup:
            errors.append(f"Reinstall/startup recovery contract missing: {required}")

    workspace_command = read(RUST / "commands" / "workspace.rs")
    if "typed_display_name" not in workspace_command or "workspace_registry::delete" not in workspace_command:
        errors.append("Explicit server deletion boundary is missing typed confirmation or registry-owned deletion")

    # Installer/uninstaller must not become an implicit data-deletion authority.
    launcher_files = list((LAUNCHER / "src-tauri").rglob("*.rs"))
    suspicious = []
    for path in launcher_files:
        source = read(path)
        if "remove_dir_all" not in source:
            continue
        for marker in ("LOCALAPPDATA", "APPDATA"):
            if marker in source and 'join("LazyBuilder")' in source:
                # Allow scoped cleanup owners; flag only direct all-app-data deletion patterns.
                if "remove_dir_all(app_data_root" in source or "remove_dir_all(&app_data_root" in source:
                    suspicious.append(str(path.relative_to(ROOT)))
    if suspicious:
        errors.append(f"Direct deletion of the LazyBuilder app-data root is forbidden: {sorted(set(suspicious))}")

    contract = read(ROOT / "docs" / "launcher-data-preservation.md")
    for phrase in (
        "Uninstall must not silently delete this tree.",
        "Uninstall must not delete, move, rewrite, or unregister them.",
        "Neither installer uninstall nor application-data migration is a server deletion authority.",
        "packaged-Windows-proven",
    ):
        if phrase not in contract:
            errors.append(f"Data-preservation documentation is missing required contract text: {phrase}")

    if errors:
        print("Launcher data-preservation contract failed:")
        for error in errors:
            print(f" - {error}")
        return 1

    print("Launcher data-preservation source contract OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
