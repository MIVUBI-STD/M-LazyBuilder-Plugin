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
- builder-safe protections for explosions, leaves decay, farmland trample, and dragon-egg teleport behavior.

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

Target top-level structure:

```text
apps/lazybuilder-desktop/
modules/world-manager/
modules/utilities-manager/
client/fabric/
shared/
docs/
```

The existing working World-Manager implementation must be structurally relocated rather than rewritten without reason. Current Fabric code similarly moves under `client/fabric/` while retaining its Minecraft-side responsibility.

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

Repository restructuring and product renaming are now the active remote-GitHub phase.

The next bounded implementation sequence is:

```text
1. establish parent/module build structure
2. relocate existing World-Manager source/resources/tests without behavior changes
3. relocate Fabric project to client/fabric
4. introduce only genuinely shared protocol/models
5. add Utilities-Manager skeleton
6. add LazyBuilder desktop skeleton with Server-Manager + Plugin-Manager boundaries
7. wire desktop control to existing World-Manager authority
8. package release artifacts
9. perform LOCAL_CODE / LIVE_SERVER validation
```

Each structural slice should remain source/CI green before continuing. Live-server proof remains separate.
