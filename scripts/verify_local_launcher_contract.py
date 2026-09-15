from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUILD_SCRIPT = ROOT / "apps" / "launcher" / "build-local.ps1"
text = BUILD_SCRIPT.read_text(encoding="utf-8")

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

if errors:
    print("Local Launcher synchronization check failed:")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("Local Launcher synchronization OK: repo-owned toolchain + Paper core + all three Fabric managers are required.")
