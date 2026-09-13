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


def replace_text(path: str, pattern: str, replacement: str, expected_min: int = 1) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    updated, count = re.subn(pattern, replacement, text, flags=re.MULTILINE)
    if count < expected_min:
        raise RuntimeError(f"Version pattern was not found in {path}")
    if updated != text:
        target.write_text(updated, encoding="utf-8")
        print(f"updated {path}")


# Maven parent/module versions. All LazyBuilder Java artifacts intentionally move together.
for pom in (
    "pom.xml",
    "shared/protocol/pom.xml",
    "modules/world-manager/pom.xml",
    "modules/utilities-manager/pom.xml",
):
    replace_text(
        pom,
        r"(?s)(<groupId>com\.halokaryamedia</groupId>\s*<artifactId>(?:lazybuilder-parent|lazybuilder-protocol|world-manager|utilities-manager)</artifactId>\s*<version>)[^<]+(</version>)",
        rf"\g<1>{SNAPSHOT_VERSION}\g<3>",
    )
    if pom != "pom.xml":
        replace_text(
            pom,
            r"(?s)(<parent>.*?<groupId>com\.halokaryamedia</groupId>\s*<artifactId>lazybuilder-parent</artifactId>\s*<version>)[^<]+(</version>)",
            rf"\g<1>{SNAPSHOT_VERSION}\g<3>",
        )

# Fabric version.
replace_text(
    "client/fabric/gradle.properties",
    r"^mod_version=.*$",
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
