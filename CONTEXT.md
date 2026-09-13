# LazyBuilder — Stable Context

## Product

LazyBuilder is the umbrella product for a Minecraft Java 1.21.4 builder-server workspace. Its purpose is to replace difficult, legacy, or overlapping server workflows with a smaller, clearer, maintainable system.

Canonical components:

```text
LazyBuilder
├── Server-Manager
├── Plugin-Manager
├── World-Manager
└── Utilities-Manager
```

External build tools such as Axiom, FastAsyncWorldEdit, FastAsyncVoxelSniper, ezEdits, and MetaBrushes remain external and are not rebuilt unless a separate explicit requirement appears.

## Repository authority

```text
Local = active development / source authority
main  = stable / release authority
```

Do not create side development branches unless the user explicitly requests isolation.

## Engineering model

- one semantic owner per responsibility;
- one primary execution path per behavior;
- no duplicate managers, registries, schedulers, config systems, or filesystem authorities;
- modules remain independently maintainable;
- source/CI proof is distinct from local/live runtime proof;
- no NMS unless a proven requirement cannot be met through stable Paper/Bukkit APIs;
- no idle/background subsystem without a concrete need.

## REMOTE_GITHUB completion

The remote source/CI phase is complete.

Final source/CI gate:

```text
head: fcae5870192252bcecc63c5f0c458bed446a64a8
run:  Verify #509

Paper modules/tests  SUCCESS
Fabric client build  SUCCESS
Tauri desktop        SUCCESS
Overall              SUCCESS
```

Desktop dependency resolution is now reproducible:

```text
EngineData/Frontend/RustApp/package-lock.json
EngineData/Frontend/RustApp/src-tauri/Cargo.lock

npm ci
cargo check --locked
```

The complete handoff record is canonical in:

```text
docs/05-operations/remote-github-complete.md
```

Any commits after the final source gate that only update handoff documentation do not expand runtime architecture or product scope.

## Server workspace target

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
├── tools/
│   └── lazybuilder/
│       ├── config/
│       ├── cache/
│       ├── logs/
│       ├── disabled-plugins/
│       └── plugin-backups/
└── README-Server.txt
```

Canonical desktop-launched runtime ownership is source-wired:

- actual Paper world folders → `world-system/worlds/`;
- World-Manager registry/import/export/backup/work data → `world-system/`;
- converter support assets → `tools/lazybuilder/cache/converter/`;
- Server-Manager, world-control, and Plugin-Manager configuration → `tools/lazybuilder/config/`;
- disabled plugin JARs → `tools/lazybuilder/disabled-plugins/`;
- plugin backups → `tools/lazybuilder/plugin-backups/`;
- Paper/runtime/plugin files → `server/`.

Archive is currently a World-Manager lifecycle state, not a second physical world store, so no unused `world-system/archives/` directory is created. Manual/non-LazyBuilder launches retain compatibility-safe legacy paths rather than silently moving existing data.

## Component ownership

### Server-Manager

Desktop-native authority for:

- start/stop/restart Paper;
- health and CPU/RAM summary;
- Java/runtime discovery;
- basic server settings;
- crash/process handling;
- canonical runtime directory bootstrap;
- launching Paper against `world-system/worlds` and passing the workspace root to Paper.

It is not a Paper plugin.

### Plugin-Manager

Desktop-native authority for:

- plugin inventory/category;
- install/update;
- duplicate prevention;
- dependency/compatibility checks;
- restart-safe enable/disable;
- safe removal with plugin data preserved by default;
- compatibility-safe migration of legacy disabled-plugin/category-registry locations.

It is not a Paper plugin.

### World-Manager

Paper-side authority for world lifecycle and files. It owns:

- browse/select worlds;
- create Flat/Void;
- BUILD_READY application;
- teleport;
- load/unload;
- clone;
- backup;
- archive/restore;
- safe delete;
- import/export/conversion;
- settings and managed metadata.

Desktop and Fabric only present/control these services through explicit transport contracts. They do not duplicate World-Manager business logic or filesystem ownership.

World-Manager source architecture is structurally locked at `REMOTE_GITHUB` proof level. Canonical rules live in `docs/04-system/world-manager-architecture-lock.md`.

### Utilities-Manager

Paper-side builder convenience module. Current locked scope:

```text
World Safety
- explosion block protection
- leaves decay protection
- farmland trample protection
- dragon egg teleport protection

Movement
- Advanced Fly
- Noclip
- Night Vision

Build Helpers
- Iron Door Toggle
- Double Slab Break
- Glazed Terracotta Rotate
```

Banner Creator, Armor Color Creator, Special Builder Items, and a custom Spectator helper family are intentionally excluded. Movement/Noclip already owns LazyBuilder-specific spectator movement transition; normal Minecraft/Paper spectator controls own camera targeting.

Utilities-Manager source architecture is structurally locked at `REMOTE_GITHUB` proof level. Canonical rules live in `docs/04-system/utilities-manager-architecture-lock.md`.

## Client / Desktop boundaries

Desktop canonical source:

```text
EngineData/Frontend/RustApp/
```

Stack:

- Tauri 2
- Svelte 5
- TypeScript
- Rust native backend

Fabric client canonical source:

```text
client/fabric/
```

World control channels remain separated by responsibility:

```text
lazybuilder:world     general world control/state
lazybuilder:map       spatial Xaero/map intents
lazybuilder:transfer  file bytes
```

The first-party Fabric World Manager surface is source-implemented for list/refresh, Create, Teleport, Load/Unload, Archive/Restore, Clone, Settings, permanent Delete, Import publication, and native Java 1.21.4 whole-world Export. Xaero remains contextual for Teleport Here and Export Area.

## BUILD_READY defaults

New builder worlds target:

- structures disabled;
- natural mob spawning disabled;
- Creative default game mode;
- Normal difficulty unless explicitly changed;
- clear weather with weather cycle disabled;
- daylight cycle disabled and daytime selected;
- fire tick disabled;
- mob griefing disabled;
- random tick speed 0;
- patrol/trader/insomnia/warden/raid events disabled where supported;
- unnecessary spawn-chunk persistence disabled when safe;
- unrelated vanilla gamerules left vanilla until changed.

Flat uses a simple vanilla-compatible flat preset. Void is empty terrain with a small safe spawn platform.

## Plugin modernization direction

```text
KEEP / EXTERNAL BUILD TOOLS
- Axiom
- FastAsyncWorldEdit
- FastAsyncVoxelSniper
- ezEdits
- MetaBrushes

REPLACE WITH LAZYBUILDER MODULES
- Multiverse-Core -> World-Manager
- VoidWorld -> World-Manager
- BuildersUtilities -> Utilities-Manager current locked scope

REMOVE FROM NEW BASELINE
- EssentialsX
- EssentialsXChat
- LightOptimizer
- MasterOptimizer
- ChunkManager
- PlaceholderAPI (no current required consumer)
- SimpleCloud-Placeholder
```

Performance authority is Paper 1.21.4 native configuration rather than generic optimizer plugins.

## Proof state

`REMOTE_GITHUB` is complete and green at the locked-dependency source head.

Remote proof covers source/static/CI evidence only. It does not prove installed Windows desktop behavior, running Paper lifecycle, Fabric runtime UI, native dialogs, Xaero mixins, network transfer, converter quality, real filesystem permissions, or gameplay behavior.

## Current phase

Do not add more speculative remote refactors.

The next phase is explicitly:

```text
LOCAL_CODE
↓
LIVE_SERVER
```

Start from the current `Local` branch containing the verified source head and completion documentation. First prove local build/artifacts, then run the integrated server workflow.

Priority validation:

```text
1. Desktop start/stop/restart + Java/runtime paths
2. canonical runtime filesystem creation
3. World-Manager enable/control bridges
4. Fabric World Manager lifecycle UI
5. Import/Export + transfer + native dialogs
6. Xaero Teleport Here / Export Area
7. Utilities World Safety / Movement / Build Helpers
8. Plugin-Manager canonical/legacy behavior
9. restart/shutdown persistence and cleanup
```

Only reproducible `LOCAL_CODE` / `LIVE_SERVER` defects should reopen source work. Fix them directly on `Local` at the smallest wrong owner. Do not reopen locked architecture unless runtime evidence demonstrates a real architecture-level requirement.
