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
- process/crash handling.

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

### Utilities-Manager

Paper-side builder convenience module. Initial target scope includes movement/build helpers, creation tools, spectator helpers, and builder-safe protections. It must not absorb economy, homes, chat, performance optimization, world lifecycle, or WorldEdit wrappers.

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
│   └── plugins/
│       ├── World-Manager.jar
│       ├── Utilities-Manager.jar
│       └── external build-tool plugins...
├── world-system/
│   ├── worlds/
│   ├── imports/
│   ├── exports/
│   ├── backups/
│   ├── archives/
│   └── work/
├── tools/
│   └── lazybuilder/
└── README-Server.txt
```

All world-related persistent/work files belong under `world-system/`. Paper runtime files and plugins stay under `server/`.

## Architecture rules

1. Preserve existing World-Manager behavior and tests.
2. Never create a second World-Manager implementation in the desktop app.
3. Server-Manager and Plugin-Manager remain desktop domains, not Paper JARs.
4. Tauri/Svelte/Rust is the sole desktop architecture.
5. Keep external build tools external.
6. Keep Paper 1.21.4 / Java 21 as the Minecraft baseline.
7. Source/CI proof remains separate from installed Windows and live Paper validation.

## Current development order

```text
1. Tauri/Svelte/Rust desktop parity — complete
2. remove legacy WPF/.NET desktop — complete
3. continue World control stage 2
4. implement Utilities-Manager features in isolated packages
5. package LazyBuilder.exe and perform installed/live validation
```
