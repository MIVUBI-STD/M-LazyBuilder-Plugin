# LazyBuilder Repository Layout

## Product identity

`LazyBuilder` is the umbrella product for the complete Minecraft Java 1.21.4 builder-server workspace. It is not the name of one runtime module.

```text
LazyBuilder
├── Desktop Application
│   ├── Server-Manager
│   └── Plugin-Manager
├── Shared Contracts
│   └── Protocol
├── Paper Modules
│   ├── World-Manager
│   └── Utilities-Manager
└── Fabric Client Managers
    ├── Map Manager          implemented / scope locked
    ├── Utility Manager      implemented / scope locked
    └── Performance Manager  implemented baseline / scope locked
```

Each Client Manager is exactly one Fabric mod and one output JAR. External build/edit tools remain external specialist products.

## Desktop architecture

```text
Svelte 5 + TypeScript + Vite
→ typed Tauri command boundary
→ Rust desktop engine
→ Windows process/filesystem + authenticated World-Manager loopback bridge
```

`EngineData/Frontend/RustApp` is the sole desktop source authority.

`src/` owns presentation/application state. `src-tauri/src/commands/` is the thin Tauri-facing adapter layer. `src-tauri/src/engine/` owns reusable desktop-native behavior for Server-Manager, Plugin-Manager, runtime provisioning/configuration, and the World-Manager desktop control client.

## Responsibility boundaries

### Server-Manager

Desktop Rust owns Paper process lifecycle, managed Java/runtime discovery, workspace/bootstrap paths, server health/resource settings, provisioning/update, process identity/recovery, and internal bundled core synchronization.

It does not own World-Manager business behavior.

### Plugin-Manager

Desktop Rust owns third-party Paper plugin inventory, metadata/category presentation, install/update, dependency/compatibility checks, duplicate resolution, restart-safe enable/disable, safe removal with plugin data preserved, and minimum rollback state.

It does not own plugin feature logic or Paper runtime internals.

### World-Manager

Paper owns managed world lifecycle and semantics:

```text
create Flat / Void
BUILD_READY
teleport
automatic load + idle unload
settings
Duplicate
backup
Archive / Restore
Delete
Import / Export / conversion
map/location actions
transfer/import/export safety
```

Manual Load/Unload and per-world autoLoad are not product features. User-facing copy uses `Duplicate`, not `Clone`.

### Utilities-Manager (Paper)

Owns small server-side builder conveniences: World Safety, Movement, and Build Helpers. It remains independent from World-Manager and from the Fabric Utility Manager.

### Map Manager (Fabric)

Owns first-party world/map UI, navigation, current managed-world presentation, world settings/lifecycle presentation, Import/Export/transfer UI, map cache, Map Export Area, Copy Review Reference, and World-Manager protocol client behavior.

Canonical source: `client/map-manager/`.

### Utility Manager (Fabric)

Owns passive non-building client convenience. It does not own build/editing tools, world lifecycle, or performance engines.

Canonical source: `client/utility-manager/`.

### Performance Manager (Fabric)

Owns lightweight performance/resource observation and background-FPS policy. External optimization engines remain external. Dynamic FPS, when present, owns background-FPS behavior and LazyBuilder yields.

Canonical source: `client/performance-manager/`.

## Canonical repository layout

```text
LazyBuilder-Plugin/
├── EngineData/
│   └── Frontend/
│       └── RustApp/
│           ├── src/
│           └── src-tauri/
├── shared/
│   └── protocol/
├── modules/
│   ├── world-manager/
│   └── utilities-manager/
├── client/
│   ├── map-manager/
│   ├── utility-manager/
│   └── performance-manager/
├── docs/
├── scripts/
├── .github/
├── AGENTS.md
├── CONTEXT.md
├── VERSION
└── README.md
```

Paper modules remain Java/Maven modules. Fabric Managers remain Java/Gradle projects. Desktop remains Tauri/Svelte/Rust. Do not rewrite a component merely to match another component's implementation language.

## Shared-code rule

Only stable contracts genuinely consumed across boundaries belong in `shared/`. The current neutral Paper/Fabric protocol is the shared contract owner.

Do not create a mandatory client-core/shared implementation module merely because three Fabric Managers exist. Managers remain independently maintainable and do not import one another's implementation packages.

## Runtime/deployment target

```text
Work Server - 1.21.4/
├── LazyBuilder.exe
├── server/
│   ├── paper.jar
│   └── plugins/
├── world-system/
│   ├── worlds/
│   ├── imports/
│   ├── exports/
│   ├── backups/
│   └── work/
└── tools/lazybuilder/
```

Client Manager JARs are installed in the Minecraft client instance, not under Paper's `server/plugins/` directory.

Archive is lifecycle metadata, not a second physical world store.

## Architecture rules

1. One semantic owner per responsibility.
2. Never create a second World-Manager implementation in Desktop or Fabric.
3. Server-Manager and Plugin-Manager remain desktop domains, not Paper JARs.
4. Tauri/Svelte/Rust is the sole desktop architecture.
5. Keep external build/edit tools external.
6. Keep specialist performance engines external.
7. One Client Manager = one Fabric mod = one output JAR.
8. No cross-Manager implementation imports.
9. Keep Paper 1.21.4 / Java 21 as the current Minecraft baseline.
10. Shared Paper/Fabric contracts live in `shared/protocol/`.
11. Source/CI proof remains separate from installed Windows/live Paper/Minecraft validation.
12. Runtime path migration must be explicit and fail-safe.
13. Do not create runtime directories, managers, registries, workers, or compatibility layers without an active owner/consumer.

## Current client state

```text
C1 Map Manager          complete / scope locked
C2 Utility Manager      complete / scope locked
C3 Performance Manager  complete baseline / scope locked
C4 Cross-manager audit  complete / architecture locked
```

The next client phase is compile/runtime proof and bounded defect correction, not another manager/scaffold phase.
