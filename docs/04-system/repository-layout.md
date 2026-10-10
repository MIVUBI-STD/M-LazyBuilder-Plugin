# LazyBuilder Repository Layout

## Goal

The repository is organized by runtime/product responsibility so a maintainer can immediately distinguish the desktop Launcher, Paper plugins, Fabric mods, and shared contracts.

## Canonical top-level layout

```text
LazyBuilder-Plugin/
├── apps/
│   └── launcher/
│       ├── src/
│       └── src-tauri/
├── plugins/
│   ├── world-manager/
│   └── utilities-manager/
├── mods/
│   ├── map-manager/
│   ├── utility-manager/
│   ├── performance-manager/
│   └── builder-utilities/         # independent Axiom-first extension
├── shared/
│   └── protocol/
├── docs/
├── scripts/
├── tooling/
│   └── windows-toolchain/
├── .agents/
├── .github/
├── DEV.cmd
├── mvnw.cmd
├── gradlew.bat
├── toolchain.json
├── pom.xml
├── VERSION
├── AGENTS.md
├── GITHUB_RULES.md
├── CONTEXT.md
└── README.md
```

## Directory meanings

### `apps/`

End-user applications. `apps/launcher/` is the sole Tauri/Svelte/Rust desktop source authority and contains Server-Manager and Plugin-Manager desktop behavior.

### `plugins/`

Paper server plugins only.

```text
plugins/world-manager/      world lifecycle/import-export authority
plugins/utilities-manager/  server-side builder conveniences
```

### `mods/`

Fabric client mods only.

```text
mods/map-manager/          world/map/transfer UI
mods/utility-manager/      passive client convenience
mods/performance-manager/  client performance/resource policy
mods/builder-utilities/     separate Axiom-first builder extension (not part of core Client Setup)
```

Each core Manager is one Fabric mod and one output JAR. Builder Utilities is a separate extension mod/artifact and does not become a fourth required Client Setup component.

### `shared/`

Neutral contracts genuinely consumed across runtime boundaries. The current owner is `shared/protocol/`. Do not turn `shared/` into a generic implementation dump.

### `docs/`, `scripts/` and `tooling/`

`docs/` owns durable product/system/operations documentation. `scripts/` owns repository-level verification/contract support, not runtime business logic. `tooling/windows-toolchain/` owns repository-supported Windows bootstrap, builds and Local packaging. `DEV.cmd` delegates to the canonical tooling command surface; retired root build/update CMD wrappers must not return.

## Runtime ownership

| Source | Runtime | Primary owner |
| --- | --- | --- |
| `apps/launcher/` | Windows desktop | Server-Manager + Plugin-Manager + desktop World-Manager client |
| `plugins/world-manager/` | Paper | world lifecycle, files, import/export/conversion, settings, transfer safety |
| `plugins/utilities-manager/` | Paper | World Safety, Movement, Build Helpers |
| `mods/map-manager/` | Fabric | world/map/transfer presentation |
| `mods/utility-manager/` | Fabric | passive client QoL |
| `mods/performance-manager/` | Fabric | bounded performance/resource policy |
| `mods/builder-utilities/` | Fabric / Axiom | independent builder extension with bounded mutation/history/recovery, not V1 core Client Setup |
| `tooling/windows-toolchain/` | Developer/build tooling | canonical developer bootstrap and verification/package commands |
| `shared/protocol/` | Paper + Fabric | versioned neutral request/result contracts |

## Naming rule

Do not reintroduce ambiguous top-level source buckets such as:

```text
EngineData/
modules/
client/
```

Those names hide runtime ownership. New implementation source must enter the existing semantic runtime directory unless a new substantial product/runtime owner is explicitly approved.

## Architecture rules

1. One semantic owner per responsibility.
2. One Manager = one deployable artifact.
3. No cross-Manager implementation imports.
4. Desktop remains Tauri/Svelte/Rust.
5. Paper plugins remain Java/Maven.
6. Fabric mods remain Java/Gradle.
7. `shared/protocol/` remains contract-only.
8. External build/edit/performance engines remain external specialist owners.
9. Source/CI proof remains separate from installed Windows/live Paper/Minecraft validation.
10. Repository layout changes must update build scripts, CI, verification guards, and canonical docs in the same coherent change.
