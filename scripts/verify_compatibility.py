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

if errors:
    print("Compatibility contract failed:")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print(f"Compatibility contract OK: Minecraft {minecraft}, Java {java_major}, Paper/Fabric/LazyBuilder aligned")
