from __future__ import annotations

import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRODUCT_VERSION = (ROOT / "VERSION").read_text(encoding="utf-8").strip()
SNAPSHOT_VERSION = f"{PRODUCT_VERSION}-SNAPSHOT"

EXPECTED = {
    f"lazybuilder-map-manager-{SNAPSHOT_VERSION}.jar": (
        "lazybuilder_map_manager",
        "LazyBuilder Map Manager",
    ),
    f"lazybuilder-utility-manager-{SNAPSHOT_VERSION}.jar": (
        "lazybuilder_utility_manager",
        "LazyBuilder Utility Manager",
    ),
    f"lazybuilder-performance-manager-{SNAPSHOT_VERSION}.jar": (
        "lazybuilder_performance_manager",
        "LazyBuilder Performance Manager",
    ),
}


def fail(message: str) -> None:
    raise SystemExit(f"Client artifact verification failed: {message}")


def entrypoint_values(metadata: dict) -> list[str]:
    values: list[str] = []
    for entry in metadata.get("entrypoints", {}).get("client", []):
        if isinstance(entry, str):
            values.append(entry)
        elif isinstance(entry, dict) and isinstance(entry.get("value"), str):
            values.append(entry["value"])
    return values


def mixin_configs(metadata: dict) -> list[str]:
    configs: list[str] = []
    for entry in metadata.get("mixins", []):
        if isinstance(entry, str):
            configs.append(entry)
        elif isinstance(entry, dict) and isinstance(entry.get("config"), str):
            configs.append(entry["config"])
    return configs


def verify_jar(path: Path, expected_id: str, expected_name: str) -> None:
    try:
        with zipfile.ZipFile(path) as jar:
            names = set(jar.namelist())
            if "fabric.mod.json" not in names:
                fail(f"{path.name} has no fabric.mod.json")

            metadata = json.loads(jar.read("fabric.mod.json").decode("utf-8"))
            if metadata.get("id") != expected_id:
                fail(f"{path.name} id is {metadata.get('id')!r}, expected {expected_id!r}")
            if metadata.get("name") != expected_name:
                fail(f"{path.name} name is {metadata.get('name')!r}, expected {expected_name!r}")
            if metadata.get("version") != SNAPSHOT_VERSION:
                fail(f"{path.name} version is {metadata.get('version')!r}, expected {SNAPSHOT_VERSION!r}")
            if metadata.get("environment") != "client":
                fail(f"{path.name} must remain client-only")

            entrypoints = entrypoint_values(metadata)
            if not entrypoints:
                fail(f"{path.name} has no client entrypoint")
            for value in entrypoints:
                class_name = value.split("::", 1)[0]
                class_path = class_name.replace(".", "/") + ".class"
                if class_path not in names:
                    fail(f"{path.name} entrypoint class is missing: {class_path}")

            for config in mixin_configs(metadata):
                if config not in names:
                    fail(f"{path.name} mixin config is missing: {config}")
    except zipfile.BadZipFile as error:
        fail(f"{path.name} is not a valid JAR/ZIP: {error}")


def main() -> None:
    artifact_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / ".artifacts" / "client-mods"
    if not artifact_dir.is_dir():
        fail(f"artifact directory does not exist: {artifact_dir}")

    actual = {path.name for path in artifact_dir.glob("*.jar")}
    expected = set(EXPECTED)
    missing = sorted(expected - actual)
    unexpected = sorted(actual - expected)
    if missing:
        fail(f"missing required JARs: {', '.join(missing)}")
    if unexpected:
        fail(f"unexpected client JARs: {', '.join(unexpected)}")

    for file_name, (mod_id, display_name) in EXPECTED.items():
        verify_jar(artifact_dir / file_name, mod_id, display_name)

    print(f"Client artifacts OK: {len(EXPECTED)} Fabric managers for LazyBuilder {PRODUCT_VERSION}")


if __name__ == "__main__":
    main()
