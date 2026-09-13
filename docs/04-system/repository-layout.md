# LazyBuilder Repository Layout

## Product identity

`LazyBuilder` is the umbrella product for the complete builder-server workspace. It is not the name of the World-Manager module.

```text
LazyBuilder
├── Server-Manager
├── Plugin-Manager
├── World-Manager
└── Utilities-Manager
```

External build tools such as Axiom, FastAsyncWorldEdit, FastAsyncVoxelSniper, ezEdits, and MetaBrushes remain external products.

## Desktop architecture

LazyBuilder follows the same long-term desktop architecture pattern as TranslateIT:

```text
Svelte 5 + TypeScript + Vite + Tailwind CSS 4
        ↓ typed product/runtime bridge
Tauri 2 commands
        ↓
Rust desktop engine
        ↓
Windows process/filesystem + authenticated World-Manager loopback bridge
```

`EngineData/Frontend/RustApp` is the sole desktop source authority. The previous .NET 8/WPF transition implementation has been removed after Tauri/Svelte/Rust reached parity and passed CI.

### Frontend ownership

`EngineData/Frontend/RustApp/src/` owns presentation and application state only. Svelte must not directly start Java, scan plugin files, mutate world storage, or own Paper business behavior.

### Rust desktop ownership

`EngineData/Frontend/RustApp/src-tauri/src/commands/` is the thin Tauri-facing command boundary.

`EngineData/Frontend/RustApp/src-tauri/src/engine/` owns reusable desktop-native behavior:

- Server-Manager process/runtime work;
- Plugin-Manager filesystem/JAR inventory work;
- World-Manager control-client work;
- desktop configuration/path handling.

Keep `main.rs` small and do not place domain behavior in command wrappers.

## Responsibility boundaries

### Server-Manager

Desktop Rust responsibility:

- start, stop, restart Paper;
- Java 21 discovery/validation;
- health summary;
- CPU/RAM status;
- basic server settings;
- process/crash handling;
- create the canonical runtime folders before Paper starts;
- launch Paper with `world-system/worlds` as its universe/world container;
- pass the workspace root to Paper through `LAZYBUILDER_WORKSPACE_ROOT`.

It is part of `LazyBuilder.exe`; it is not a Paper plugin.

### Plugin-Manager

Desktop Rust responsibility:

- discover installed plugins;
- categorize plugins by purpose;
- add/update plugins;
- prevent duplicate plugin versions;
- dependency/compatibility checks;
- enable/disable with restart-safe file handling;
- safe removal while preserving plugin data by default.

It is part of `LazyBuilder.exe`; it is not a Paper plugin.

Canonical Plugin-Manager runtime ownership is:

```text
server/plugins/                         enabled Paper plugin JARs
tools/lazybuilder/disabled-plugins/     disabled plugin JARs
tools/lazybuilder/plugin-backups/       update/duplicate-resolution backups
tools/lazybuilder/config/plugin-registry.json
                                        category overrides
```

Legacy `server/plugins-disabled/` and `tools/lazybuilder/plugin-registry.json` are compatibility inputs only. Plugin-Manager moves non-conflicting legacy disabled JARs into the canonical directory and copies a legacy category registry only when the canonical registry does not already exist. Canonical state is never overwritten by compatibility migration; conflicts remain visible to duplicate detection rather than being silently discarded.

### World-Manager

Paper-side authority for all world lifecycle operations:

- create Flat/Void worlds;
- BUILD_READY application;
- list/load/unload;
- teleport;
- settings;
- clone;
- backup;
- archive/restore;
- delete;
- import/export;
- conversion integration;
- map/location actions.

The desktop Rust runtime may call the authenticated loopback control bridge, but must not duplicate world business logic or become a second filesystem owner.

World-Manager receives the canonical workspace root from Server-Manager and validates that Paper is actually using `<workspace>/world-system/worlds`. It then owns registry/import/export/backup/work paths under the canonical layout. Archive/Restore currently changes managed lifecycle metadata and load state; it does not maintain a second physical archive world store. A manual plugin-only launch that does not supply the workspace environment remains on the historical plugin-data layout for compatibility rather than silently moving existing data.

### Utilities-Manager

Paper-side builder convenience module. Current locked scope is:

- Movement: Advanced Fly, Noclip, Night Vision;
- Build Helpers: Iron Door Toggle, Double Slab Break, Glazed Terracotta Rotate;
- World Safety: explosions, leaves decay, farmland trample, dragon-egg teleport protection.

Creation Tools and duplicate custom Spectator controls are intentionally out of scope. Utilities-Manager must not absorb economy, homes, chat, performance optimization, world lifecycle, or WorldEdit wrappers.

## Canonical repository layout

```text
LazyBuilder-Plugin/
├── EngineData/
│   ├── Frontend/
│   │   └── RustApp/
│   │       ├── src/
│   │       │   ├── App.svelte
│   │       │   ├── pages/
│   │       │   ├── components/
│   │       │   ├── app/bridge/
│   │       │   └── styles/
│   │       └── src-tauri/
│   │           └── src/
│   │               ├── main.rs
│   │               ├── app_bootstrap.rs
│   │               ├── commands/
│   │               └── engine/
│   └── README.md
├── modules/
│   ├── world-manager/
│   └── utilities-manager/
├── client/
│   └── fabric/
├── docs/
├── .github/
├── AGENTS.md
├── CONTEXT.md
└── README.md
```

Paper modules remain Java/Maven modules. Fabric remains Java/Gradle. They are not rewritten in Rust merely to match the desktop runtime.

## Shared-code rule

Only stable contracts genuinely consumed by multiple runtimes may become shared modules. Do not create a generic shared dumping ground. Paper implementation, filesystem mutation, desktop UI, Fabric-specific logic, and world business logic each keep one semantic owner.

## Runtime/deployment target

```text
Work Server - 1.21.4/
├── LazyBuilder.exe
├── server/
│   ├── paper.jar
│   ├── plugins/
│   │   ├── World-Manager.jar
│   │   ├── Utilities-Manager.jar
│   │   └── external build-tool plugins...
│   └── normal Paper-generated configuration/runtime files
├── world-system/
│   ├── worlds/          # Paper universe / actual world folders
│   ├── imports/         # validated inbound world archives
│   ├── exports/         # export artifacts ready for transfer
│   ├── backups/         # World-Manager backups
│   ├── work/            # request-scoped temporary work
│   │   └── transfer/    # transient transfer session files
│   └── registry.yml     # durable managed-world registry
├── tools/
│   └── lazybuilder/
│       ├── config/
│       │   ├── server-manager.json
│       │   ├── world-control.json
│       │   └── plugin-registry.json
│       ├── cache/
│       │   └── converter/
│       ├── logs/
│       ├── disabled-plugins/
│       └── plugin-backups/
└── README-Server.txt
```

### Runtime ownership rules

- Paper world folders live only in `world-system/worlds/` for the canonical desktop-launched runtime.
- World-Manager registry/import/export/backup/work data lives only under `world-system/`.
- Archive is lifecycle metadata, so no unused physical `archives/` folder is created.
- converter binaries/download cache live under `tools/lazybuilder/cache/converter/` because they are executable support assets, not world data.
- Server-Manager, World-control, and Plugin-Manager category configuration live under `tools/lazybuilder/config/`.
- enabled Paper plugin JARs live under `server/plugins/`; disabled JARs and Plugin-Manager backups live under `tools/lazybuilder/`.
- Paper runtime/plugin files remain under `server/`.
- the desktop launcher creates the canonical directories before starting Paper and supplies the same workspace root to the plugin.
- if the launcher supplies a workspace root but Paper reports a different world container, World-Manager fails closed instead of creating two world-storage authorities.
- manual/non-LazyBuilder Paper launches retain the legacy World-Manager plugin-data layout unless they are explicitly launched with the canonical `world-system/worlds` container; no automatic destructive migration of existing server worlds is performed.
- Plugin-Manager legacy config/disabled-JAR locations are migrated conservatively and remain compatibility inputs only.

## Architecture rules

1. Preserve existing World-Manager behavior and tests.
2. Never create a second World-Manager implementation in the desktop app.
3. Server-Manager and Plugin-Manager remain desktop domains, not Paper JARs.
4. Tauri/Svelte/Rust is the sole desktop architecture.
5. Keep external build tools external.
6. Keep Paper 1.21.4 / Java 21 as the Minecraft baseline.
7. Source/CI proof remains separate from installed Windows and live Paper validation.
8. Runtime path migration must be explicit and fail-safe; never silently relocate existing world folders.
9. Compatibility paths must not become permanent parallel storage authorities.
10. Do not create runtime directories that have no active semantic owner.

## Current development order

```text
1. Desktop architecture parity / WPF removal — complete
2. World-Manager source architecture — locked
3. Utilities-Manager source architecture — locked
4. canonical runtime storage wiring — implemented at source level
5. Plugin-Manager runtime storage consolidation — implemented at source level
6. final repository consistency/packaging audit
7. package LazyBuilder.exe and perform LOCAL_CODE / LIVE_SERVER validation
```
