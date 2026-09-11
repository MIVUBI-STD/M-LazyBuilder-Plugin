# LazyBuilder Plugin — Stable Context

## Product

LazyBuilder is a Minecraft Java 1.21.4 builder-server workspace. Its purpose is to replace difficult, legacy, or overlapping server workflows with a smaller, clearer, maintainable system.

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

## Plugin modernization objective

Existing plugins are classified by the smallest stable outcome:

```text
KEEP
UPDATE / CLEAN UP
MERGE / CONSOLIDATE
REBUILD
NEW PLUGIN
RETIRE / REPLACE
```

Third-party software is not preserved merely because it already exists. Rebuild is preferred when the required scope is narrow enough that a native implementation materially improves operation, ownership, and long-term maintenance.

## First confirmed rebuild: World Manager

Multiverse-Core is being replaced rather than wrapped as the long-term world-management owner.

Target architecture:

```text
LazyBuilder Client Mod
        │
        │ UI / map interaction
        ▼
LazyBuilder Server Plugin
        │
        │ world lifecycle / validation / authority
        ▼
Paper API / Minecraft 1.21.4
```

Multiverse may be used only as migration/reference evidence while transition is underway; it is not the intended runtime dependency of the finished World Manager.

## World Manager confirmed requirements

### Main surface

- world map is the primary visual surface;
- Xaero World Map is reused for map preview rather than rebuilding a full map engine;
- the only Xaero behavior required beyond preview/navigation is **Teleport to Location**;
- LazyBuilder owns world operations and settings.

### Create World

Creation is intentionally minimal:

```text
Create World
├── Flat World
└── Void World
```

No normal/default terrain generator is required for the current builder workflow.

Every newly created world receives an internal `BUILD_READY` profile automatically. Create UI should not expose advanced world settings.

### BUILD_READY defaults

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

### World Settings

Advanced configuration is separated from creation and belongs to World Settings. It will own additional world behavior such as gamerules, spawn rules, and other explicit overrides.

### Other confirmed World Manager capabilities

- browse/select worlds;
- teleport to world;
- teleport to map location;
- load/unload;
- clone;
- archive/delete with safety confirmation;
- import/export;
- world settings;
- metadata/display information.

Feature design is discussed and specified sequentially before implementation so the source remains small and intentional.

## Architecture principles

- one canonical owner per responsibility;
- one execution path per behavior;
- no mandatory master/core plugin without real runtime need;
- related modules may share one repository but remain independently deployable when lifecycle requires it;
- UI/client code never becomes server authority;
- destructive world operations are server validated;
- no NMS unless a proven requirement cannot be met through stable Paper/Bukkit APIs;
- source/CI proof remains distinct from live-server proof.

## Current phase

World Manager product specification and repository-development-system alignment. Implementation should begin only after the next sequential feature contracts are sufficiently defined.
