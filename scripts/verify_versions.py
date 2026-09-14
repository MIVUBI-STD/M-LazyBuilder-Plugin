from __future__ import annotations

import json
import re
import sys
import tomllib
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRODUCT_VERSION = (ROOT / "VERSION").read_text(encoding="utf-8").strip()
SNAPSHOT_VERSION = f"{PRODUCT_VERSION}-SNAPSHOT"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}

errors: list[str] = []

CLIENT_MANAGERS = {
    "map-manager": {
        "mod_id": "lazybuilder_map_manager",
        "name": "LazyBuilder Map Manager",
        "artifact": "lazybuilder-map-manager",
        "package": "com.halokaryamedia.lazybuilder.client.",
    },
    "utility-manager": {
        "mod_id": "lazybuilder_utility_manager",
        "name": "LazyBuilder Utility Manager",
        "artifact": "lazybuilder-utility-manager",
        "package": "com.halokaryamedia.lazybuilder.utility.",
    },
    "performance-manager": {
        "mod_id": "lazybuilder_performance_manager",
        "name": "LazyBuilder Performance Manager",
        "artifact": "lazybuilder-performance-manager",
        "package": "com.halokaryamedia.lazybuilder.performance.",
    },
}


def expect(label: str, actual: str | None, expected: str) -> None:
    if actual != expected:
        errors.append(f"{label}: expected {expected!r}, found {actual!r}")


def maven_versions(path: str) -> tuple[str | None, str | None]:
    root = ET.parse(ROOT / path).getroot()
    version = root.find("m:version", NS)
    parent_version = root.find("m:parent/m:version", NS)
    return (
        version.text.strip() if version is not None and version.text else None,
        parent_version.text.strip() if parent_version is not None and parent_version.text else None,
    )


def gradle_property(text: str, key: str) -> str | None:
    match = re.search(rf"(?m)^{re.escape(key)}=(.+)$", text)
    return match.group(1).strip() if match else None


root_version, root_parent = maven_versions("pom.xml")
expect("pom.xml", root_version, SNAPSHOT_VERSION)
if root_parent is not None:
    errors.append("Root pom.xml unexpectedly declares a parent version")

for pom in (
    "shared/protocol/pom.xml",
    "plugins/world-manager/pom.xml",
    "plugins/utilities-manager/pom.xml",
):
    version, parent_version = maven_versions(pom)
    expect(pom, version, SNAPSHOT_VERSION)
    expect(f"{pom} parent", parent_version, SNAPSHOT_VERSION)

launcher_root = ROOT / "apps" / "launcher"
package = json.loads((launcher_root / "package.json").read_text(encoding="utf-8"))
expect("desktop package.json", package.get("version"), PRODUCT_VERSION)

package_lock = json.loads((launcher_root / "package-lock.json").read_text(encoding="utf-8"))
expect("desktop package-lock.json", package_lock.get("version"), PRODUCT_VERSION)
expect("desktop package-lock root package", package_lock.get("packages", {}).get("", {}).get("version"), PRODUCT_VERSION)

tauri = json.loads((launcher_root / "src-tauri/tauri.conf.json").read_text(encoding="utf-8"))
expect("tauri.conf.json", tauri.get("version"), PRODUCT_VERSION)

cargo = tomllib.loads((launcher_root / "src-tauri/Cargo.toml").read_text(encoding="utf-8"))
expect("Cargo.toml", cargo.get("package", {}).get("version"), PRODUCT_VERSION)

cargo_lock = tomllib.loads((launcher_root / "src-tauri/Cargo.lock").read_text(encoding="utf-8"))
lazybuilder_lock = next(
    (package for package in cargo_lock.get("package", []) if package.get("name") == "lazybuilder"),
    None,
)
expect(
    "Cargo.lock lazybuilder package",
    lazybuilder_lock.get("version") if lazybuilder_lock else None,
    PRODUCT_VERSION,
)

# Client source identity and isolation contract. Performance Manager remains source-only/deferred in V1.
for manager, contract in CLIENT_MANAGERS.items():
    manager_root = ROOT / "mods" / manager
    props_text = (manager_root / "gradle.properties").read_text(encoding="utf-8")
    expect(f"{manager} mod_version", gradle_property(props_text, "mod_version"), SNAPSHOT_VERSION)
    expect(f"{manager} artifact", gradle_property(props_text, "archives_base_name"), contract["artifact"])

    metadata_files = list((manager_root / "src/main/resources").glob("fabric.mod.json"))
    if len(metadata_files) != 1:
        errors.append(f"{manager}: expected exactly one fabric.mod.json, found {len(metadata_files)}")
    else:
        metadata = json.loads(metadata_files[0].read_text(encoding="utf-8"))
        expect(f"{manager} Fabric id", metadata.get("id"), contract["mod_id"])
        expect(f"{manager} display name", metadata.get("name"), contract["name"])
        if metadata.get("environment") != "client":
            errors.append(f"{manager}: Fabric environment must remain client-only")

        depends = metadata.get("depends", {})
        for other_manager, other_contract in CLIENT_MANAGERS.items():
            if other_manager != manager and other_contract["mod_id"] in depends:
                errors.append(f"{manager}: must not depend on {other_manager} Fabric mod")

    build_text = (manager_root / "build.gradle").read_text(encoding="utf-8")
    for other_manager, other_contract in CLIENT_MANAGERS.items():
        if other_manager == manager:
            continue
        forbidden_tokens = (
            f"mods/{other_manager}",
            f"../{other_manager}",
            other_contract["artifact"],
            other_contract["mod_id"],
        )
        if any(token in build_text for token in forbidden_tokens):
            errors.append(f"{manager}: Gradle build references {other_manager}")

    source_root = manager_root / "src/main/java"
    if source_root.is_dir():
        for java_file in source_root.rglob("*.java"):
            source = java_file.read_text(encoding="utf-8")
            for other_manager, other_contract in CLIENT_MANAGERS.items():
                if other_manager == manager:
                    continue
                if other_contract["package"] in source:
                    relative = java_file.relative_to(ROOT)
                    errors.append(f"{manager}: {relative} references {other_manager} implementation package")

core_modules = (launcher_root / "src-tauri/src/engine/core_modules.rs").read_text(encoding="utf-8")
match = re.search(r'pub const CORE_VERSION: &str = "([^"]+)";', core_modules)
expect("Rust CORE_VERSION", match.group(1) if match else None, SNAPSHOT_VERSION)
for label, prefix in (
    ("World Manager runtime filename", "World-Manager"),
    ("Utilities Manager runtime filename", "Utilities-Manager"),
):
    match = re.search(rf'"({re.escape(prefix)}-[^"]+\.jar)"', core_modules)
    expected_name = f"{prefix}-{SNAPSHOT_VERSION}.jar"
    expect(label, match.group(1) if match else None, expected_name)

# Simplified V1 runtime scope must remain narrow.
client_integration = (launcher_root / "src-tauri/src/engine/client_integration.rs").read_text(encoding="utf-8")
for required in (
    "lazybuilder-map-manager-0.1.0-SNAPSHOT.jar",
    "lazybuilder-utility-manager-0.1.0-SNAPSHOT.jar",
    "const MODS: [ClientModSpec; 2]",
):
    if required not in client_integration:
        errors.append(f"Client Setup V1 contract is missing required marker: {required}")
if "lazybuilder-performance-manager-" in client_integration:
    errors.append("Client Setup V1 must not require or install Performance Manager")
if "profile.json" in client_integration:
    errors.append("Client Setup V1 must not restore legacy profile.json metadata authority")

engine_mod = (launcher_root / "src-tauri/src/engine/mod.rs").read_text(encoding="utf-8")
for removed_owner in ("cpu_governor", "paper_performance"):
    if removed_owner in engine_mod:
        errors.append(f"Launcher simplification regressed: {removed_owner} was reintroduced")
    if (launcher_root / f"src-tauri/src/engine/{removed_owner}.rs").exists():
        errors.append(f"Launcher simplification regressed: {removed_owner}.rs exists again")

if "modules/world-manager" in core_modules or "modules/utilities-manager" in core_modules:
    errors.append("Core module runtime resolution still references removed modules/ source paths")

workflow = (ROOT / ".github/workflows/verify.yml").read_text(encoding="utf-8")
if re.search(r"(?m)^  utilities:\s*$", workflow):
    errors.append("Verify workflow restored the duplicate standalone utilities job")
for required_build in (
    "gradle -p mods/map-manager --no-daemon build",
    "gradle -p mods/utility-manager --no-daemon build",
):
    if required_build not in workflow:
        errors.append(f"Verify workflow is missing required V1 Fabric build: {required_build}")
if "gradle -p mods/performance-manager" in workflow:
    errors.append("Verify workflow must not make Performance Manager a V1 package gate")

paper_provider = (launcher_root / "src-tauri/src/engine/paper_provider.rs").read_text(encoding="utf-8")
match = re.search(r'const USER_AGENT: &str = "LazyBuilder/([^ (]+)', paper_provider)
expect("Paper provider user-agent", match.group(1) if match else None, PRODUCT_VERSION)

java_runtime = (launcher_root / "src-tauri/src/engine/java_runtime.rs").read_text(encoding="utf-8")
match = re.search(r'const USER_AGENT: &str = "LazyBuilder/([^"]+)";', java_runtime)
expect("Managed Java user-agent", match.group(1) if match else None, PRODUCT_VERSION)

map_build = (ROOT / "mods/map-manager/build.gradle").read_text(encoding="utf-8")
if "../../plugins/world-manager/src/main/java" in map_build or "../../modules/world-manager/src/main/java" in map_build:
    errors.append("Map Manager build still compiles source directly from World-Manager")
if "../../shared/protocol/src/main/java" not in map_build:
    errors.append("Map Manager build is not wired to shared/protocol")

for manager in ("utility-manager", "performance-manager"):
    build = (ROOT / f"mods/{manager}/build.gradle").read_text(encoding="utf-8")
    if "../../shared/protocol" in build:
        errors.append(f"{manager} has an unintended shared World-Manager protocol dependency")

legacy_protocol_paths = (
    "plugins/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/control/WorldControlWireProtocol.java",
    "plugins/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/map/MapActionWireProtocol.java",
    "plugins/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/transfer/TransferWireProtocol.java",
    "plugins/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/transfer/TransferDescriptor.java",
    "plugins/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/registry/WorldId.java",
)
for path in legacy_protocol_paths:
    if (ROOT / path).exists():
        errors.append(f"Shared protocol source still has a legacy World-Manager copy: {path}")

if errors:
    print("Repository consistency check failed:")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print(f"Repository consistency OK for LazyBuilder {PRODUCT_VERSION}")
