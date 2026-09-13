# LazyBuilder — Stable Context

## Product

LazyBuilder is the umbrella product for a Minecraft Java 1.21.4 builder-server workspace. Its purpose is to replace difficult, legacy, or overlapping server workflows with a smaller, clearer, maintainable system.

LazyBuilder is **not** the World Manager module name.

Canonical component naming:

```text
LazyBuilder
├── Server-Manager
├── Plugin-Manager
├── World-Manager
└── Utilities-Manager
```

External build tools such as Axiom, FastAsyncWorldEdit, FastAsyncVoxelSniper, ezEdits, and MetaBrushes remain external and are not renamed or rebuilt by LazyBuilder unless a separate explicit decision is made later.

## Repository authority

```text
Local = active development / source authority
main  = stable / release authority
```

## Engineering model

LazyBuilder follows the same repository-development discipline used by the user's BuildIT/LazyDesigner repository:

- hierarchical canonical documentation;
- selective context loading;
- explicit execution-context/proof ceilings;
- one semantic owner per responsibility;
- bounded/standard/complex development routing;
- GitHub-first completion before local/live-server residue;
- domain Skills only when a real responsibility exists;
- no duplicate systems or speculative framework layers.

The process is mirrored; product-specific implementation remains native to LazyBuilder rather than copying unrelated Blockbench/MCP architecture.

## Server workspace target

Fresh local-server layout:

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
│   ├── archives/
│   └── work/
├── tools/
│   └── lazybuilder/
└── README-Server.txt
```

All world-related lifecycle data lives under `world-system/`; Paper/runtime/plugin files stay under `server/`.

The default server world is a clean BUILD_READY flat builder world. Nether and End are disabled by default because this server is build-focused.

## Component ownership

### Server-Manager

Lives in `LazyBuilder.exe` and owns only server-process/desktop concerns:

- start/stop/restart Paper safely;
- health summary;
- CPU/RAM status;
- basic server settings;
- crash/process handling.

It is not a Paper plugin.

### Plugin-Manager

Lives in `LazyBuilder.exe` and owns plugin-file management:

- plugin discovery;
- functional categorization;
- install/update;
- duplicate-version prevention;
- dependency/compatibility checks;
- enable/disable using restart-safe file movement;
- safe removal while preserving plugin data by default.

It is not a Paper plugin.

### World-Manager

Paper-side authority for world lifecycle. Multiverse-Core and VoidWorld are replaced rather than wrapped as long-term authorities.

World-Manager owns:

- browse/select worlds;
- create Flat/Void worlds;
- BUILD_READY application;
- teleport to world/map location;
- load/unload;
- clone;
- backup;
- archive/restore;
- safe delete;
- import/export;
- world settings;
- conversion integration;
- metadata/display state.

The desktop app may present these actions but must not duplicate World-Manager business logic or become a second filesystem authority.

World-Manager is structurally locked at source/CI proof level. Canonical architecture is recorded in `docs/04-system/world-manager-architecture-lock.md`. Live Paper/Desktop/Fabric proof remains a separate later stage.

### Utilities-Manager

Paper-side builder convenience module. Current target scope:

- advanced fly;
- noclip;
- night vision;
- iron-door toggle;
- double-slab helper;
- glazed-terracotta rotation helper;
- builder-safe protections for explosions, leaves decay, farmland trample, and dragon-egg teleport behavior.

Banner Creator, Armor Color Creator, Special Builder Items, and a custom Spectator helper family are intentionally excluded from the current product scope. Movement/Noclip already owns the LazyBuilder-specific spectator movement transition, while normal Minecraft/Paper spectator controls own camera targeting. Do not add overlapping spectator controls unless a concrete missing capability appears later.

Do not place world management, performance optimization, economy, home/warp/chat suites, or WorldEdit aliases in Utilities-Manager.

## BUILD_READY defaults

New builder worlds should be immediately safe and predictable for building:

- structures disabled;
- natural mob spawning disabled;
- default game mode Creative;
- difficulty Normal unless explicitly changed later;
- clear weather with weather cycle disabled;
- daylight cycle disabled and daytime selected;
- fire tick disabled;
- mob griefing disabled;
- random tick speed set to 0;
- patrol, wandering trader, insomnia/phantom, warden, and raid spawning/events disabled where supported by the target API/gamerules;
- unnecessary spawn-chunk persistence disabled when safe for the target Paper API;
- other unrelated vanilla gamerules remain vanilla until changed through World Settings.

Flat World uses a simple vanilla-compatible flat world with structures disabled. Void World is empty terrain with a small safe spawn platform by default unless later requirements change that decision.

## Plugin modernization decisions

Current baseline direction:

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
- BuildersUtilities -> Utilities-Manager target scope

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

## Desktop UX direction

Primary navigation is intentionally small:

```text
Dashboard
Worlds
Plugins
Settings
```

Dashboard only shows server state/health plus CPU/RAM and start/stop/restart actions. Technical details, logs, console, Java/JVM settings, and diagnostics belong under contextual problem views or Advanced settings rather than primary navigation.

Worlds delegates to World-Manager. Plugins delegates to Plugin-Manager.

## Repository layout direction

Canonical repository layout is documented in `docs/04-system/repository-layout.md`.

Current primary source boundaries:

```text
EngineData/Frontend/RustApp/
modules/world-manager/
modules/utilities-manager/
client/fabric/
docs/
```

Keep each manager independently maintainable. Do not relocate or rewrite stable World-Manager source merely for symmetry.

## Architecture principles

- one canonical owner per responsibility;
- one execution path per behavior;
- no mandatory master/core Paper plugin without real runtime need;
- related modules may share one repository but remain independently deployable when lifecycle requires it;
- desktop/client UI never becomes server authority;
- destructive world operations are server validated;
- no NMS unless a proven requirement cannot be met through stable Paper/Bukkit APIs;
- source/CI proof remains distinct from live-server proof;
- no runtime hot-reload hacks for Paper plugins;
- no background optimizer layer without demonstrated need.

## Current phase

World-Manager source architecture and its Desktop/Fabric control paths are structurally stable. Utilities-Manager now has the intended remote-source feature set:

```text
World Safety
Movement
Build Helpers
```

Creation Tools and custom Spectator helpers are intentionally out of scope because they do not add required capability to the current builder workflow.

A compile issue in the initial glazed-terracotta rotation implementation was corrected by using explicit cardinal `BlockFace` rotation compatible with the current Paper API. The latest source must be considered source/CI-stable only after the current `Local` verification run completes successfully.

After the current CI gate is green, the next stage is LOCAL_CODE / LIVE_SERVER validation of the three Utilities families plus the already locked World-Manager flows. World-Manager changes require a concrete requirement that cannot be satisfied inside its locked ownership boundaries. Source/CI proof remains distinct from live runtime validation.
