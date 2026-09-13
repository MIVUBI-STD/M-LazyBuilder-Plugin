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


def maven_version(path: str) -> str | None:
    root = ET.parse(ROOT / path).getroot()
    node = root.find("m:version", NS)
    return node.text.strip() if node is not None and node.text else None


for pom in (
    "pom.xml",
    "shared/protocol/pom.xml",
    "modules/world-manager/pom.xml",
    "modules/utilities-manager/pom.xml",
):
    expect(pom, maven_version(pom), SNAPSHOT_VERSION)

package = json.loads((ROOT / "EngineData/Frontend/RustApp/package.json").read_text(encoding="utf-8"))
expect("desktop package.json", package.get("version"), PRODUCT_VERSION)

package_lock = json.loads((ROOT / "EngineData/Frontend/RustApp/package-lock.json").read_text(encoding="utf-8"))
expect("desktop package-lock.json", package_lock.get("version"), PRODUCT_VERSION)
expect("desktop package-lock root package", package_lock.get("packages", {}).get("", {}).get("version"), PRODUCT_VERSION)

tauri = json.loads((ROOT / "EngineData/Frontend/RustApp/src-tauri/tauri.conf.json").read_text(encoding="utf-8"))
expect("tauri.conf.json", tauri.get("version"), PRODUCT_VERSION)

cargo = tomllib.loads((ROOT / "EngineData/Frontend/RustApp/src-tauri/Cargo.toml").read_text(encoding="utf-8"))
expect("Cargo.toml", cargo.get("package", {}).get("version"), PRODUCT_VERSION)

gradle_properties = (ROOT / "client/fabric/gradle.properties").read_text(encoding="utf-8")
match = re.search(r"(?m)^mod_version=(.+)$", gradle_properties)
expect("Fabric mod_version", match.group(1).strip() if match else None, SNAPSHOT_VERSION)

core_modules = (ROOT / "EngineData/Frontend/RustApp/src-tauri/src/engine/core_modules.rs").read_text(encoding="utf-8")
match = re.search(r'pub const CORE_VERSION: &str = "([^"]+)";', core_modules)
expect("Rust CORE_VERSION", match.group(1) if match else None, SNAPSHOT_VERSION)

paper_provider = (ROOT / "EngineData/Frontend/RustApp/src-tauri/src/engine/paper_provider.rs").read_text(encoding="utf-8")
match = re.search(r'const USER_AGENT: &str = "LazyBuilder/([^ ]+)', paper_provider)
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
