from __future__ import annotations

import json
import re
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRODUCT_VERSION = (ROOT / "VERSION").read_text(encoding="utf-8").strip()
SNAPSHOT_VERSION = f"{PRODUCT_VERSION}-SNAPSHOT"

if not re.fullmatch(r"\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?", PRODUCT_VERSION):
    raise SystemExit(f"Invalid VERSION value: {PRODUCT_VERSION!r}")


def replace_text(path: str, pattern: str, replacement: str, expected: int = 1) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    updated, count = re.subn(pattern, replacement, text, count=expected, flags=re.MULTILINE)
    if count != expected:
        raise RuntimeError(f"Expected {expected} version match(es) in {path}, found {count}")
    if updated != text:
        target.write_text(updated, encoding="utf-8")
        print(f"updated {path}")


# Maven parent/module versions. All LazyBuilder Java artifacts intentionally move together.
replace_text(
    "pom.xml",
    r"(?s:(<groupId>com\.halokaryamedia</groupId>\s*<artifactId>lazybuilder-parent</artifactId>\s*<version>)[^<]+(</version>))",
    rf"\g<1>{SNAPSHOT_VERSION}\g<2>",
)

child_poms = {
    "shared/protocol/pom.xml": "lazybuilder-protocol",
    "modules/world-manager/pom.xml": "world-manager",
    "modules/utilities-manager/pom.xml": "utilities-manager",
}
for pom, artifact_id in child_poms.items():
    replace_text(
        pom,
        r"(?s:(<parent>.*?<groupId>com\.halokaryamedia</groupId>\s*<artifactId>lazybuilder-parent</artifactId>\s*<version>)[^<]+(</version>.*?</parent>))",
        rf"\g<1>{SNAPSHOT_VERSION}\g<2>",
    )
    replace_text(
        pom,
        rf"(?s:(</parent>\s*<artifactId>{re.escape(artifact_id)}</artifactId>\s*<version>)[^<]+(</version>))",
        rf"\g<1>{SNAPSHOT_VERSION}\g<2>",
    )

# Map Manager Fabric version.
replace_text(
    "client/map-manager/gradle.properties",
    r"^mod_version=[^\r\n]*$",
    f"mod_version={SNAPSHOT_VERSION}",
)

# Rust/Tauri runtime constants and package metadata.
replace_text(
    "EngineData/Frontend/RustApp/src-tauri/src/engine/core_modules.rs",
    r'^pub const CORE_VERSION: &str = "[^"]+";$',
    f'pub const CORE_VERSION: &str = "{SNAPSHOT_VERSION}";',
)
replace_text(
    "EngineData/Frontend/RustApp/src-tauri/src/engine/core_modules.rs",
    r'^const WORLD_FILE_NAME: &str = "[^"]+";$',
    f'const WORLD_FILE_NAME: &str = "World-Manager-{SNAPSHOT_VERSION}.jar";',
)
replace_text(
    "EngineData/Frontend/RustApp/src-tauri/src/engine/core_modules.rs",
    r'^const UTILITIES_FILE_NAME: &str = "[^"]+";$',
    f'const UTILITIES_FILE_NAME: &str = "Utilities-Manager-{SNAPSHOT_VERSION}.jar";',
)
replace_text(
    "EngineData/Frontend/RustApp/src-tauri/src/engine/paper_provider.rs",
    r'^const USER_AGENT: &str = "LazyBuilder/[^ ]+ PaperProvider";$',
    f'const USER_AGENT: &str = "LazyBuilder/{PRODUCT_VERSION} PaperProvider";',
)
replace_text(
    "EngineData/Frontend/RustApp/src-tauri/src/engine/java_runtime.rs",
    r'^const USER_AGENT: &str = "LazyBuilder/[^"]+";$',
    f'const USER_AGENT: &str = "LazyBuilder/{PRODUCT_VERSION}";',
)

cargo_path = ROOT / "EngineData/Frontend/RustApp/src-tauri/Cargo.toml"
cargo_text = cargo_path.read_text(encoding="utf-8")
parsed_cargo = tomllib.loads(cargo_text)
old_cargo_version = parsed_cargo.get("package", {}).get("version")
if old_cargo_version != PRODUCT_VERSION:
    cargo_text, count = re.subn(
        r'(?m)^(version\s*=\s*)"[^"]+"',
        rf'\g<1>"{PRODUCT_VERSION}"',
        cargo_text,
        count=1,
    )
    if count != 1:
        raise RuntimeError("Cargo package version was not found")
    cargo_path.write_text(cargo_text, encoding="utf-8")
    print("updated EngineData/Frontend/RustApp/src-tauri/Cargo.toml")

replace_text(
    "EngineData/Frontend/RustApp/src-tauri/Cargo.lock",
    r'(?s:(\[\[package\]\]\s*name\s*=\s*"lazybuilder"\s*version\s*=\s*)"[^"]+")',
    rf'\g<1>"{PRODUCT_VERSION}"',
)

for json_path in (
    "EngineData/Frontend/RustApp/package.json",
    "EngineData/Frontend/RustApp/src-tauri/tauri.conf.json",
):
    path = ROOT / json_path
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("version") != PRODUCT_VERSION:
        data["version"] = PRODUCT_VERSION
        path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"updated {json_path}")

lock_path = ROOT / "EngineData/Frontend/RustApp/package-lock.json"
lock = json.loads(lock_path.read_text(encoding="utf-8"))
changed = False
if lock.get("version") != PRODUCT_VERSION:
    lock["version"] = PRODUCT_VERSION
    changed = True
root_package = lock.setdefault("packages", {}).setdefault("", {})
if root_package.get("version") != PRODUCT_VERSION:
    root_package["version"] = PRODUCT_VERSION
    changed = True
if changed:
    lock_path.write_text(json.dumps(lock, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("updated EngineData/Frontend/RustApp/package-lock.json")

print(f"LazyBuilder version synchronized: product={PRODUCT_VERSION}, core={SNAPSHOT_VERSION}")
