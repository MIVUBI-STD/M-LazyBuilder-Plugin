from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUILD_SCRIPT = ROOT / "apps" / "launcher" / "build-local.ps1"
TAURI_CONFIG = ROOT / "apps" / "launcher" / "src-tauri" / "tauri.conf.json"
BOOTSTRAP = ROOT / "apps" / "launcher" / "src-tauri" / "src" / "app_bootstrap.rs"
RUNTIME_API = ROOT / "apps" / "launcher" / "src" / "app" / "bridge" / "runtimeApi.ts"
APP = ROOT / "apps" / "launcher" / "src" / "App.svelte"

text = BUILD_SCRIPT.read_text(encoding="utf-8")
config = json.loads(TAURI_CONFIG.read_text(encoding="utf-8"))
bootstrap = BOOTSTRAP.read_text(encoding="utf-8")
runtime_api = RUNTIME_API.read_text(encoding="utf-8")
app = APP.read_text(encoding="utf-8")

required_markers = (
    "$PerformanceClientJar",
    "$PerformanceClientTargetJar",
    "$MavenWrapper",
    "$GradleWrapper",
    "$ClientVerifier",
    "& $MavenWrapper --batch-mode --no-transfer-progress verify",
    "& $GradleWrapper -p mods/map-manager --no-daemon build",
    "& $GradleWrapper -p mods/utility-manager --no-daemon build",
    "& $GradleWrapper -p mods/performance-manager --no-daemon build",
    "Copy-Item $PerformanceClientTargetJar $PerformanceClientJar -Force",
    "& $ClientVerifier -ClientModsDir $ClientModsDir -RepoRoot $RepoRoot",
    "@($WorldJar, $UtilitiesJar, $MapJar, $UtilityClientJar, $PerformanceClientJar)",
)

errors: list[str] = []
for marker in required_markers:
    if marker not in text:
        errors.append(f"local Launcher build is missing required toolchain/client-suite marker: {marker}")

for prefix in (
    "lazybuilder-map-manager-",
    "lazybuilder-utility-manager-",
    "lazybuilder-performance-manager-",
):
    if prefix not in text:
        errors.append(f"local Launcher stale-artifact cleanup does not cover {prefix}")

for forbidden in (
    "Require-Command mvn",
    "Require-Command gradle",
    "Require-Command python",
    "python scripts/verify_client_artifacts.py",
    "\n        mvn --batch-mode --no-transfer-progress verify",
    "\n        gradle -p mods/",
):
    if forbidden in text:
        errors.append(f"local Launcher build restored a forbidden global/legacy tool path: {forbidden.strip()}")

legacy_removal = (
    "Get-ChildItem $ClientModsDir -Filter 'lazybuilder-performance-manager-*.jar' "
    "-File -ErrorAction SilentlyContinue | Remove-Item -Force"
)
if legacy_removal in text:
    errors.append("local Launcher build still removes Performance Manager instead of publishing it")

bundle = config.get("bundle", {})
windows = bundle.get("windows", {})
nsis = windows.get("nsis", {})
icons = set(bundle.get("icon", []))
if config.get("productName") != "LazyBuilder":
    errors.append("Tauri productName must remain LazyBuilder for consistent Windows identity")
if config.get("identifier") != "com.halokaryamedia.lazybuilder":
    errors.append("Tauri Windows application identifier changed unexpectedly")
if "icons/icon.ico" not in icons:
    errors.append("Windows bundle must include the canonical LazyBuilder .ico")
if nsis.get("installerIcon") != "icons/icon.ico":
    errors.append("NSIS installer must use the canonical LazyBuilder .ico")
if nsis.get("installMode") != "currentUser":
    errors.append("LazyBuilder NSIS installMode must remain currentUser")

lifecycle_commands = (
    "workspace_open_folder",
    "workspace_duplicate_estimate",
    "workspace_duplicate",
    "workspace_remove_from_library",
    "workspace_delete",
)
for command in lifecycle_commands:
    if f"commands::workspace::{command}" not in bootstrap:
        errors.append(f"Launcher command registry is missing server lifecycle command: {command}")
    if f"'{command}'" not in runtime_api:
        errors.append(f"runtimeApi is missing server lifecycle command: {command}")

for ui_marker in (
    "Duplicate server",
    "Remove from library",
    "Delete server…",
    "Delete permanently",
    "Your files will remain on this computer.",
):
    if ui_marker not in app:
        errors.append(f"Server Library UI is missing required lifecycle wording: {ui_marker}")

if errors:
    print("Local Launcher synchronization check failed:")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("Local Launcher synchronization OK: toolchain, Windows identity, Paper/Fabric suite, and server lifecycle contracts are synchronized.")
