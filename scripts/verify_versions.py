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


root_version, root_parent = maven_versions("pom.xml")
expect("pom.xml", root_version, SNAPSHOT_VERSION)
if root_parent is not None:
    errors.append("Root pom.xml unexpectedly declares a parent version")

for pom in (
    "shared/protocol/pom.xml",
    "modules/world-manager/pom.xml",
    "modules/utilities-manager/pom.xml",
):
    version, parent_version = maven_versions(pom)
    expect(pom, version, SNAPSHOT_VERSION)
    expect(f"{pom} parent", parent_version, SNAPSHOT_VERSION)

package = json.loads((ROOT / "EngineData/Frontend/RustApp/package.json").read_text(encoding="utf-8"))
expect("desktop package.json", package.get("version"), PRODUCT_VERSION)

package_lock = json.loads((ROOT / "EngineData/Frontend/RustApp/package-lock.json").read_text(encoding="utf-8"))
expect("desktop package-lock.json", package_lock.get("version"), PRODUCT_VERSION)
expect("desktop package-lock root package", package_lock.get("packages", {}).get("", {}).get("version"), PRODUCT_VERSION)

tauri = json.loads((ROOT / "EngineData/Frontend/RustApp/src-tauri/tauri.conf.json").read_text(encoding="utf-8"))
expect("tauri.conf.json", tauri.get("version"), PRODUCT_VERSION)

cargo = tomllib.loads((ROOT / "EngineData/Frontend/RustApp/src-tauri/Cargo.toml").read_text(encoding="utf-8"))
expect("Cargo.toml", cargo.get("package", {}).get("version"), PRODUCT_VERSION)

cargo_lock = tomllib.loads((ROOT / "EngineData/Frontend/RustApp/src-tauri/Cargo.lock").read_text(encoding="utf-8"))
lazybuilder_lock = next(
    (package for package in cargo_lock.get("package", []) if package.get("name") == "lazybuilder"),
    None,
)
expect(
    "Cargo.lock lazybuilder package",
    lazybuilder_lock.get("version") if lazybuilder_lock else None,
    PRODUCT_VERSION,
)

gradle_properties = (ROOT / "client/fabric/gradle.properties").read_text(encoding="utf-8")
match = re.search(r"(?m)^mod_version=(.+)$", gradle_properties)
expect("Fabric mod_version", match.group(1).strip() if match else None, SNAPSHOT_VERSION)

core_modules = (ROOT / "EngineData/Frontend/RustApp/src-tauri/src/engine/core_modules.rs").read_text(encoding="utf-8")
match = re.search(r'pub const CORE_VERSION: &str = "([^"]+)";', core_modules)
expect("Rust CORE_VERSION", match.group(1) if match else None, SNAPSHOT_VERSION)
for label, prefix in (
    ("World Manager runtime filename", "World-Manager"),
    ("Utilities Manager runtime filename", "Utilities-Manager"),
):
    match = re.search(rf'"({re.escape(prefix)}-[^"]+\.jar)"', core_modules)
    expected_name = f"{prefix}-{SNAPSHOT_VERSION}.jar"
    expect(label, match.group(1) if match else None, expected_name)

paper_provider = (ROOT / "EngineData/Frontend/RustApp/src-tauri/src/engine/paper_provider.rs").read_text(encoding="utf-8")
match = re.search(r'const USER_AGENT: &str = "LazyBuilder/([^ (]+)', paper_provider)
expect("Paper provider user-agent", match.group(1) if match else None, PRODUCT_VERSION)

fabric_build = (ROOT / "client/fabric/build.gradle").read_text(encoding="utf-8")
if "../../modules/world-manager/src/main/java" in fabric_build:
    errors.append("Fabric build still compiles source directly from World-Manager")
if "../../shared/protocol/src/main/java" not in fabric_build:
    errors.append("Fabric build is not wired to shared/protocol")

legacy_protocol_paths = (
    "modules/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/control/WorldControlWireProtocol.java",
    "modules/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/map/MapActionWireProtocol.java",
    "modules/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/transfer/TransferWireProtocol.java",
    "modules/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/transfer/TransferDescriptor.java",
    "modules/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/registry/WorldId.java",
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
