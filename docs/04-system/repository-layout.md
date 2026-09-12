# LazyBuilder Repository Layout

## Product identity

`LazyBuilder` is the umbrella product for the complete builder-server workspace. It is **not** the name of the World Manager module.

Canonical component names:

```text
LazyBuilder
├── Server-Manager
├── Plugin-Manager
├── World-Manager
└── Utilities-Manager
```

External build tools such as Axiom, FastAsyncWorldEdit, FastAsyncVoxelSniper, ezEdits, and MetaBrushes remain external products and are not renamed or reimplemented by LazyBuilder.

## Responsibility boundaries

### Server-Manager

Desktop-side responsibility:

- start, stop, restart Paper;
- health summary;
- CPU/RAM status;
- basic server settings;
- process/crash handling.

It is part of `LazyBuilder.exe`; it is not a Paper plugin.

### Plugin-Manager

Desktop-side responsibility:

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

`LazyBuilder.exe` may present World-Manager state and actions, but it must not duplicate world business logic or directly become a second filesystem owner.

### Utilities-Manager

Paper-side builder convenience module. Initial target scope:

- advanced fly;
- noclip;
- night vision;
- iron-door toggle;
- double-slab helper;
- glazed-terracotta rotation helper;
- banner creator;
- armor-color creator;
- special builder items;
- spectator helpers;
- builder-safe protections such as explosion/leaves/farmland/dragon-egg behavior.

Do not add economy, homes, warps, chat suites, performance optimization, world management, or WorldEdit command wrappers to this module.

## Target repository layout

```text
LazyBuilder-Plugin/
├── apps/
│   └── lazybuilder-desktop/
│       └── ...
├── modules/
│   ├── world-manager/
│   │   ├── src/
│   │   └── pom.xml
│   └── utilities-manager/
│       ├── src/
│       └── pom.xml
├── client/
│   └── fabric/
│       ├── src/
│       ├── build.gradle
│       └── ...
├── shared/
│   ├── protocol/
│   ├── models/
│   └── contracts/
├── docs/
├── .github/
├── AGENTS.md
├── CONTEXT.md
└── README.md
```

The directory names describe ownership, not deployment packaging.

## Current-source migration map

Current Paper source under:

```text
src/main/java/com/halokaryamedia/lazybuilder/world/
```

belongs to:

```text
modules/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/
```

This includes the existing application services, control protocol, conversion runtime, persistence, transfer/map adapters, and Paper gateways. Preserve behavior while relocating; do not rewrite working World-Manager logic merely for the folder move.

Current root plugin bootstrap:

```text
src/main/java/com/halokaryamedia/lazybuilder/LazyBuilderPlugin.java
```

must become the World-Manager Paper bootstrap during migration and be renamed only when imports/resources/tests are moved consistently.

Current Fabric project:

```text
client/
```

moves conceptually to:

```text
client/fabric/
```

without changing its runtime responsibility. The Fabric client remains Minecraft-side UI/input/network integration, not the desktop application.

## Shared-code rule

`shared/` is intentionally small. Only stable data/protocol contracts that are genuinely consumed by more than one runtime belong there.

Allowed examples:

```text
ProtocolVersion
WorldSummary
ServerHealth
request/response DTOs
stable protocol contracts
```

Do not put these in `shared/`:

- Paper API implementations;
- filesystem mutation services;
- conversion implementation;
- desktop UI logic;
- Fabric-specific code;
- world business logic.

One semantic owner remains mandatory.

## Runtime/deployment target

Fresh local server layout:

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

## Migration rules

1. Preserve the existing World-Manager implementation and tests while moving it.
2. Do not create a second World-Manager implementation in the desktop app.
3. Do not create `Server-Manager.jar` or `Plugin-Manager.jar`; those responsibilities belong to the desktop app.
4. Introduce `Utilities-Manager` as a separate Paper module instead of expanding World-Manager.
5. Keep external build tools external.
6. Keep Paper 1.21.4 / Java 21 as the target baseline.
7. Complete structural relocation in bounded slices and keep source/CI green after each slice.
8. Live-server proof remains separate from remote source/CI proof.

## Recommended relocation order

```text
1. establish parent/module build structure
2. relocate World-Manager source/resources/tests without behavioral changes
3. relocate Fabric project to client/fabric
4. introduce only the shared contracts actually needed
5. add Utilities-Manager skeleton and tests
6. add desktop app skeleton with Server-Manager + Plugin-Manager boundaries
7. wire desktop ↔ World-Manager control protocol
8. package release artifacts
9. perform LOCAL_CODE / LIVE_SERVER validation
```

Avoid a single large rewrite commit. Structural moves should remain reviewable and reversible.
