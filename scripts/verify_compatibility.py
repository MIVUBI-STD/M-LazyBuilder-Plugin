#!/usr/bin/env python3
"""Verify cross-runtime compatibility against toolchain.json authority."""

from __future__ import annotations

import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
errors: list[str] = []


def fail(label: str, actual: object, expected: object) -> None:
    if actual != expected:
        errors.append(f"{label}: expected {expected!r}, found {actual!r}")


def gradle_property(text: str, key: str) -> str | None:
    match = re.search(rf"(?m)^{re.escape(key)}=(.+)$", text)
    return match.group(1).strip() if match else None


toolchain = json.loads((ROOT / "toolchain.json").read_text(encoding="utf-8"))
minecraft = str(toolchain.get("target", {}).get("minecraft", "")).strip()
java_major = str(toolchain.get("java", {}).get("major", "")).strip()
if not minecraft or not java_major:
    errors.append("toolchain.json must define canonical target.minecraft and java.major")

# Paper/Maven compatibility.
pom = ET.parse(ROOT / "pom.xml").getroot()
properties = pom.find("m:properties", NS)
def prop(name: str) -> str | None:
    if properties is None:
        return None
    node = properties.find(f"m:{name}", NS)
    return node.text.strip() if node is not None and node.text else None

fail("Maven compiler release", prop("maven.compiler.release"), java_major)
fail("Paper API target", prop("paper.version"), f"{minecraft}-R0.1-SNAPSHOT")

# Launcher runtime compatibility.
launcher_engine = ROOT / "apps" / "launcher" / "src-tauri" / "src" / "engine"
workspace_registry = (launcher_engine / "workspace_registry.rs").read_text(encoding="utf-8")
paper_provider = (launcher_engine / "paper_provider.rs").read_text(encoding="utf-8")
java_runtime = (launcher_engine / "java_runtime.rs").read_text(encoding="utf-8")

match = re.search(r'const MINECRAFT_VERSION: &str = "([^"]+)";', workspace_registry)
fail("Launcher workspace Minecraft target", match.group(1) if match else None, minecraft)
match = re.search(r'const SERVER_PLATFORM: &str = "([^"]+)";', workspace_registry)
fail("Launcher workspace server platform", match.group(1) if match else None, "paper")

match = re.search(r'const PAPER_VERSION: &str = "([^"]+)";', paper_provider)
fail("Launcher Paper provider target", match.group(1) if match else None, minecraft)
if f"/versions/{minecraft}/builds" not in paper_provider:
    errors.append("Paper provider builds URL does not match canonical Minecraft target")

match = re.search(r"const JAVA_MAJOR: u32 = (\d+);", java_runtime)
fail("Launcher managed Java major", match.group(1) if match else None, java_major)
if f"feature_releases/{java_major}/ga" not in java_runtime:
    errors.append("Managed Java provider URL does not match canonical Java major")
if f'join("java-{java_major}")' not in java_runtime:
    errors.append("Managed Java runtime directory does not match canonical Java major")

# Fabric client compatibility.
for manager in ("map-manager", "utility-manager", "performance-manager"):
    root = ROOT / "mods" / manager
    props = (root / "gradle.properties").read_text(encoding="utf-8")
    fail(f"{manager} minecraft_version", gradle_property(props, "minecraft_version"), minecraft)
    fabric_version = gradle_property(props, "fabric_version") or ""
    if not fabric_version.endswith(f"+{minecraft}"):
        errors.append(f"{manager} fabric_version does not target Minecraft {minecraft}: {fabric_version!r}")

    metadata = json.loads((root / "src/main/resources/fabric.mod.json").read_text(encoding="utf-8"))
    depends = metadata.get("depends", {})
    fail(f"{manager} Fabric minecraft dependency", depends.get("minecraft"), minecraft)
    fail(f"{manager} Fabric Java dependency", depends.get("java"), f">={java_major}")

# Build provenance target claims must agree with the same authority.
provenance = (ROOT / "scripts" / "write_build_provenance.py").read_text(encoding="utf-8")
if f'"minecraft": "{minecraft}"' not in provenance:
    errors.append("Build provenance Minecraft target does not match toolchain.json")
if f'"java": "{java_major}"' not in provenance:
    errors.append("Build provenance Java target does not match toolchain.json")

# Cross-runtime protocol truth and canonical documentation must agree.
protocol_root = ROOT / "shared/protocol/src/main/java"
world_wire = protocol_root / "com/halokaryamedia/lazybuilder/world/control/WorldControlWireProtocol.java"
map_wire = protocol_root / "com/halokaryamedia/lazybuilder/world/map/MapActionWireProtocol.java"
builder_wire = protocol_root / "com/halokaryamedia/lazybuilder/builder/wire/BuilderExtensionWireProtocol.java"


def wire_version(path: Path) -> int | None:
    match = re.search(
        r"(?m)^\s*public static final int VERSION\s*=\s*(\d+);",
        path.read_text(encoding="utf-8"),
    )
    return int(match.group(1)) if match else None


protocol_versions = {
    "World Control": wire_version(world_wire),
    "Map Action": wire_version(map_wire),
    "Builder Extension": wire_version(builder_wire),
}
for label, version in protocol_versions.items():
    if version is None:
        errors.append(f"cannot determine canonical {label} version from shared protocol source")

# Only current canonical documents are checked; archived/historical evidence
# may legitimately describe an earlier protocol and is not current authority.
canonical_world_map_docs = (
    "CONTEXT.md",
    "docs/02-world-management/README.md",
    "docs/03-client-ui/README.md",
    "docs/04-system/README.md",
    "docs/04-system/networking.md",
    "docs/04-system/world-control-bridge.md",
)
for relative in canonical_world_map_docs:
    doc = (ROOT / relative).read_text(encoding="utf-8")
    for label in ("World Control", "Map Action"):
        described = {int(value) for value in re.findall(
            rf"\b{re.escape(label)} V(\d+)\b", doc
        )}
        actual = protocol_versions[label]
        if actual is not None and described != {actual}:
            errors.append(
                f"{relative}: {label} should describe only source V{actual}, found {sorted(described)}"
            )

builder_channel = "lazybuilder:builder_ext"
builder_wire_source = builder_wire.read_text(encoding="utf-8")
builder_client_source = (
    ROOT / "mods/builder-utilities/src/main/java/com/halokaryamedia/lazybuilder/"
           "builder/net/BuilderExtensionPayload.java"
).read_text(encoding="utf-8")
builder_paper_source = (
    ROOT / "plugins/utilities-manager/src/main/java/com/halokaryamedia/"
           "lazybuilder/utilities/PaperBuilderExtensionPayloadAdapter.java"
).read_text(encoding="utf-8")
if f'CHANNEL = "{builder_channel}"' not in builder_wire_source:
    errors.append("shared Builder Extension channel does not match canonical channel identity")
if 'Identifier.of("lazybuilder", "builder_ext")' not in builder_client_source:
    errors.append("Fabric Builder channel identity differs from shared contract")
if "BuilderExtensionWireProtocol" not in builder_paper_source or "PERMISSION" not in builder_paper_source:
    errors.append("Paper Builder adapter no longer shows shared contract and permission boundary")
for relative in ("CONTEXT.md", "docs/04-system/README.md", "docs/04-system/networking.md"):
    doc = (ROOT / relative).read_text(encoding="utf-8")
    expected_builder = protocol_versions["Builder Extension"]
    described = {int(value) for value in re.findall(r"\bBuilder Extension V(\d+)\b", doc)}
    if builder_channel not in doc or (
        expected_builder is not None and described != {expected_builder}
    ):
        errors.append(
            f"{relative}: missing/mismatched Builder extension channel or source protocol version"
        )

# A shared DTO/wire layer cannot acquire runtime implementation dependencies.
# Discover canonical source types directly; no second manually maintained type registry.
protocol_types = {
    ".".join(path.relative_to(protocol_root).with_suffix("").parts)
    for path in protocol_root.rglob("*.java")
}
if not protocol_types:
    errors.append("shared protocol source has no discoverable Java contracts")
for path in sorted(protocol_root.rglob("*.java")):
    source = path.read_text(encoding="utf-8")
    for imp in re.findall(r"(?m)^\s*import\s+(?:static\s+)?([\w.]+)\s*;", source):
        if imp.startswith(("java.", "javax.")):
            continue
        if imp in protocol_types or any(imp.startswith(name + ".") for name in protocol_types):
            continue
        errors.append(
            f"shared protocol cannot import non-neutral implementation: {path.relative_to(ROOT)}: {imp}"
        )

# Paper plugins consume neutral shared DTOs but not each other's implementation.
for module in ("world-manager", "utilities-manager"):
    pom_root = ET.parse(ROOT / "plugins" / module / "pom.xml").getroot()
    dependencies = pom_root.findall("m:dependencies/m:dependency", NS)
    internal_artifacts = []
    for dependency in dependencies:
        group = dependency.findtext("m:groupId", "", NS)
        if group == "com.halokaryamedia":
            internal_artifacts.append(dependency.findtext("m:artifactId", "", NS))
    if internal_artifacts != ["lazybuilder-protocol"]:
        errors.append(
            f"{module} Maven internal dependencies must be only lazybuilder-protocol: {internal_artifacts}"
        )

# Fabric source sets may reuse neutral shared sources, not sibling Manager trees.
for module in ("map-manager", "utility-manager", "performance-manager", "builder-utilities"):
    build = (ROOT / "mods" / module / "build.gradle").read_text(encoding="utf-8")
    if any(folder in build for folder in ("../../mods/", "../../plugins/", "../../apps/")):
        errors.append(f"{module} Gradle build references a foreign implementation source tree")
    if re.search(r"\bproject\s*\(\s*['\"]:", build):
        errors.append(f"{module} Gradle build must not depend on a sibling Manager project")
    if module in ("map-manager", "utility-manager", "builder-utilities") and (
        "../../shared/protocol/src/main/java" not in build
    ):
        errors.append(f"{module} must consume its neutral shared-protocol source owner")

if errors:
    print("Compatibility contract failed:")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print(f"Compatibility contract OK: Minecraft {minecraft}, Java {java_major}, Paper/Fabric/LazyBuilder aligned")
