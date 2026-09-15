from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUILD_SCRIPT = ROOT / "apps" / "launcher" / "build-local.ps1"
text = BUILD_SCRIPT.read_text(encoding="utf-8")

required_markers = (
    "$PerformanceClientJar",
    "$PerformanceClientTargetJar",
    "gradle -p mods/performance-manager --no-daemon build",
    "Copy-Item $PerformanceClientTargetJar $PerformanceClientJar -Force",
    "python scripts/verify_client_artifacts.py $ClientModsDir",
    "@($WorldJar, $UtilitiesJar, $MapJar, $UtilityClientJar, $PerformanceClientJar)",
)

errors: list[str] = []
for marker in required_markers:
    if marker not in text:
        errors.append(f"local Launcher build is missing required client-suite marker: {marker}")

for prefix in (
    "lazybuilder-map-manager-",
    "lazybuilder-utility-manager-",
    "lazybuilder-performance-manager-",
):
    if prefix not in text:
        errors.append(f"local Launcher stale-artifact cleanup does not cover {prefix}")

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

print("Local Launcher synchronization OK: Paper core + all three Fabric managers are required.")
