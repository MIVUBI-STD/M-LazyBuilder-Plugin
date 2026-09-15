from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUILD_SCRIPT = ROOT / "apps" / "launcher" / "build-local.ps1"
PUBLISHER = ROOT / "tooling" / "windows-toolchain" / "scripts" / "distribution" / "package-local.ps1"
FABRIC_VERIFIER = ROOT / "tooling" / "windows-toolchain" / "scripts" / "verify" / "verify-fabric.ps1"
TAURI_CONFIG = ROOT / "apps" / "launcher" / "src-tauri" / "tauri.conf.json"
BOOTSTRAP = ROOT / "apps" / "launcher" / "src-tauri" / "src" / "app_bootstrap.rs"
RUNTIME_API = ROOT / "apps" / "launcher" / "src" / "app" / "bridge" / "runtimeApi.ts"
APP = ROOT / "apps" / "launcher" / "src" / "App.svelte"

text = BUILD_SCRIPT.read_text(encoding="utf-8")
publisher = PUBLISHER.read_text(encoding="utf-8")
fabric_verifier = FABRIC_VERIFIER.read_text(encoding="utf-8")
config = json.loads(TAURI_CONFIG.read_text(encoding="utf-8"))
bootstrap = BOOTSTRAP.read_text(encoding="utf-8")
runtime_api = RUNTIME_API.read_text(encoding="utf-8")
app = APP.read_text(encoding="utf-8")
errors: list[str] = []

for marker in (
    "$PerformanceClientJar",
    "$PerformanceClientTargetJar",
    "$MavenWrapper",
    "$FabricVerifier",
    "$ClientVerifier",
    "$PackageLocal",
    "dist\\Local",
    "dist\\CompileOnly",
    "& $MavenWrapper --batch-mode --no-transfer-progress verify",
    "& $FabricVerifier -RepoRoot $RepoRoot",
    "Copy-Item $PerformanceClientTargetJar $PerformanceClientJar -Force",
    "& $ClientVerifier -ClientModsDir $ClientModsDir -RepoRoot $RepoRoot",
    "& $PackageLocal -RepoRoot $RepoRoot -InstallerPath $FreshInstaller",
):
    if marker not in text:
        errors.append(f"local Launcher build is missing canonical marker: {marker}")

for manager in ("mods/map-manager", "mods/utility-manager", "mods/performance-manager"):
    if manager not in fabric_verifier or "--no-daemon build" not in fabric_verifier:
        errors.append(f"canonical Fabric verifier is missing required manager lane: {manager}")

for forbidden in (
    "Require-Command mvn",
    "Require-Command gradle",
    "Require-Command python",
    "python scripts/verify_client_artifacts.py",
    "dist\\LazyBuilder",
    "$GradleWrapper",
):
    if forbidden in text:
        errors.append(f"local Launcher build restored a legacy/parallel path: {forbidden}")

for marker in (
    "dist\\Local",
    "LazyBuilder-Setup-Local.exe",
    "LazyBuilder-Diagnostics.exe",
    "build-info.json",
    "SHA256SUMS.txt",
    "schemaVersion = 2",
):
    if marker not in publisher:
        errors.append(f"canonical Local publisher is missing package marker: {marker}")

bundle = config.get("bundle", {})
windows = bundle.get("windows", {})
nsis = windows.get("nsis", {})
icons = set(bundle.get("icon", []))
if config.get("productName") != "LazyBuilder":
    errors.append("Tauri productName must remain LazyBuilder")
if config.get("identifier") != "com.halokaryamedia.lazybuilder":
    errors.append("Tauri application identifier changed unexpectedly")
if "icons/icon.ico" not in icons or nsis.get("installerIcon") != "icons/icon.ico":
    errors.append("Windows bundle/NSIS must use the canonical LazyBuilder icon")
if nsis.get("installMode") != "currentUser":
    errors.append("LazyBuilder NSIS installMode must remain currentUser")

for command in (
    "workspace_open_folder",
    "workspace_duplicate_estimate",
    "workspace_duplicate",
    "workspace_remove_from_library",
    "workspace_delete",
):
    if f"commands::workspace::{command}" not in bootstrap:
        errors.append(f"Launcher command registry is missing: {command}")
    if f"'{command}'" not in runtime_api:
        errors.append(f"runtimeApi is missing: {command}")

for ui_marker in (
    "Duplicate server",
    "Remove from library",
    "Delete server…",
    "Delete permanently",
    "Your files will remain on this computer.",
):
    if ui_marker not in app:
        errors.append(f"Server Library UI is missing required wording: {ui_marker}")

if errors:
    print("Local Launcher synchronization check failed:")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("Local Launcher synchronization OK: canonical verification lanes, publisher, Windows identity, Paper/Fabric suite, and lifecycle contracts are synchronized.")
