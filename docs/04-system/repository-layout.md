# LazyBuilder Repository Layout

## Product identity

`LazyBuilder` is the umbrella product for the complete builder-server workspace. It is not the name of the World-Manager module.

```text
LazyBuilder
├── Desktop Application
│   ├── Server-Manager
│   └── Plugin-Manager
├── Paper Modules
│   ├── World-Manager
│   └── Utilities-Manager
└── Client Managers
    ├── Map Manager          (implemented)
    ├── Utility Manager      (planned)
    └── Performance Manager  (planned)
```

Each Client Manager is exactly one Fabric mod and one output JAR. External build tools such as Axiom, FastAsyncWorldEdit, FastAsyncVoxelSniper, ezEdits, and MetaBrushes remain external products.

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

### Utilities-Manager

Paper-side builder convenience module. It remains distinct from the Fabric Utility Manager. Its server-side scope is controlled separately and must not absorb client QoL, performance optimization, world lifecycle, or WorldEdit wrappers.

### Map Manager

Fabric client authority for the current LazyBuilder world/map experience:

- in-game world/map surfaces;
- navigation;
- world settings UI;
- transfer UI;
- client-side map cache;
- versioned Fabric-to-Paper World-Manager transport.

Canonical source:

```text
client/map-manager/
```

### Utility Manager

Planned Fabric client mod for passive non-building convenience only. It does not own build/editing tools or performance engines.

Target source:

```text
client/utility-manager/
```

### Performance Manager

Planned Fabric client mod for monitoring, profiles, background resource behavior, and optional integration with specialist performance mods. Sodium, Iris, ImmediatelyFast, FerriteCore, EntityCulling, and MoreCulling remain external engines.

Target source:

```text
client/performance-manager/
```

## Canonical repository layout

```text
LazyBuilder-Plugin/
├── EngineData/
│   ├── Frontend/
│   │   └── RustApp/
│   │       ├── src/
│   │       └── src-tauri/
├── shared/
│   └── protocol/
├── modules/
│   ├── world-manager/
│   └── utilities-manager/
├── client/
│   ├── map-manager/
│   ├── utility-manager/      # planned; create only when implementation starts
│   └── performance-manager/  # planned; create only when implementation starts
├── docs/
├── .github/
├── AGENTS.md
├── CONTEXT.md
└── README.md
```

Paper modules remain Java/Maven modules. Fabric managers remain Java/Gradle. They are not rewritten in Rust merely to match the desktop runtime.

## Shared-code rule

Only stable contracts genuinely consumed by multiple runtimes may become shared modules. Do not create a generic shared dumping ground. Paper implementation, filesystem mutation, desktop UI, Fabric-specific logic, and world business logic each keep one semantic owner.

Do not create a mandatory `client-core` merely because multiple Fabric managers exist. Extract a small stable shared contract only after a second real consumer proves it is necessary.

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
│   ├── worlds/
│   ├── imports/
│   ├── exports/
│   ├── backups/
│   ├── work/
│   └── registry.yml
├── tools/
│   └── lazybuilder/
└── README-Server.txt
```

Client Manager JARs are installed in the Minecraft client instance, not under Paper's `server/plugins/` directory.

## Architecture rules

1. Preserve existing World-Manager behavior and tests.
2. Never create a second World-Manager implementation in the desktop app.
3. Server-Manager and Plugin-Manager remain desktop domains, not Paper JARs.
4. Tauri/Svelte/Rust is the sole desktop architecture.
5. Keep external build tools external.
6. Keep specialist performance engines external unless a future architecture review proves otherwise.
7. One Client Manager equals one Fabric mod and one output JAR.
8. Keep Paper 1.21.4 / Java 21 as the Minecraft baseline.
9. Source/CI proof remains separate from installed Windows and live Paper validation.
10. Runtime path migration must be explicit and fail-safe; never silently relocate existing world folders.
11. Do not create runtime directories or client modules that have no active semantic owner.

## Current client development order

```text
C1. Map Manager identity/path migration
C2. Utility Manager scaffold
C3. Performance Manager scaffold
C4. Cross-manager verification
C5. Build-specific utility review (last, and only after Axiom boundary audit)
```
