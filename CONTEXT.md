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
- no duplicate managers, registries, schedulers, config systems, process markers, or filesystem authorities;
- internal maintenance stays internal unless the user has a real product decision;
- prefer deletion/consolidation before introducing a new abstraction;
- source proof is distinct from local/live runtime proof;
- no NMS unless a proven requirement cannot be met through stable Paper/Bukkit APIs;
- no idle/background subsystem without a concrete need;
- use the cheapest proof capable of falsifying the changed claim.

Canonical development discipline:

```text
docs/04-system/development-discipline.md
```

Canonical specialist routing:

```text
docs/04-system/skill-routing.md
```

Specialist set:

```text
lazybuilder-desktop-runtime
lazybuilder-plugin-management
lazybuilder-world-management
lazybuilder-ui
lazybuilder-protocol
```

Do not add another Skill unless a repeated responsibility has a materially different execution procedure that cannot be routed cleanly to these owners.

## Proof history

An earlier complete remote source/CI gate exists at:

```text
head: fcae5870192252bcecc63c5f0c458bed446a64a8
run:  Verify #509

Paper modules/tests  SUCCESS
Fabric client build  SUCCESS
Tauri desktop        SUCCESS
Overall              SUCCESS
```

That run is historical evidence only. World Manager and Fabric UI have changed substantially after that head. The current `Local` branch therefore requires a fresh compile/test/live proof pass before release claims are made.

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

Canonical desktop-launched runtime ownership:

- actual Paper world folders → `world-system/worlds/`;
- World-Manager registry/import/export/backup/work data → `world-system/`;
- converter support assets → `tools/lazybuilder/cache/converter/`;
- Server-Manager and local-control configuration → `tools/lazybuilder/config/`;
- disabled third-party plugin JARs → `tools/lazybuilder/disabled-plugins/`;
- minimum plugin rollback snapshots → `tools/lazybuilder/plugin-backups/`;
- Paper/runtime/plugin files → `server/`.

Archive is a lifecycle state, not a second physical world store. Do not create `world-system/archives/`.

## Component ownership

### Server-Manager

Desktop-native authority for:

- workspace lifecycle/runtime bootstrap;
- start/stop/restart Paper;
- one Paper process owner and one detached-recovery path;
- one persisted process marker using PID + actual process start time;
- health and CPU/RAM summary;
- managed Java/runtime discovery;
- one typed `server-manager.json` config authority;
- Paper provisioning/manual Paper update;
- internal bundled World/Utilities core compatibility sync;
- resource/runtime settings;
- launching Paper against `world-system/worlds` and passing the workspace root to Paper.

Runtime policy:

```text
Paper update          -> explicit user decision
bundled core sync     -> internal automatic maintenance before start/restart
CPU scheduling        -> JVM/OS managed
resource tuning       -> Performance / Boost / Custom RAM
process identity      -> one server-process.json marker
server configuration  -> one server_config owner
```

### Plugin-Manager

Desktop-native authority for third-party Paper plugin lifecycle:

- plugin discovery/inventory;
- derived category presentation;
- install/update;
- duplicate detection/resolution;
- dependency/compatibility checks;
- restart-safe enable/disable;
- safe JAR removal with plugin data preserved;
- minimum rollback state required for plugin mutation.

Do not recreate persistent category ownership, timestamped backup history, or hot-reload behavior without a proven requirement.

### World-Manager

Paper-side authority for managed world lifecycle, runtime coordination, files, settings, import/export/conversion, and transfer safety.

Canonical persistent lifecycle:

```text
ACTIVE
ARCHIVED
```

Loaded/unloaded/loading/unloading are not durable world states. Paper is the runtime authority.

Canonical runtime behavior:

```text
Teleport / settings / required use
→ load automatically when needed

world empty + idle + no conflicting operation
→ unload automatically
```

Manual Load/Unload and per-world `autoLoad` are not product features.

World Manager owns:

- discover/adopt managed Paper worlds;
- create Flat/Void;
- BUILD_READY application;
- teleport;
- automatic runtime load/unload coordination;
- Duplicate;
- backup;
- Archive/Restore;
- safe Delete;
- Import/Export/edition-version conversion;
- settings and durable managed metadata;
- bounded world operations;
- transfer/import/export filesystem safety;
- converter capability discovery and request-bound execution.

Operations are transient operations, not lifecycle values.

```text
DUPLICATE
BACKUP
IMPORT
EXPORT
ARCHIVE
RESTORE
DELETE
```

Operations that require a consistent filesystem snapshot are blocked while builders remain inside the target world. World Manager does not silently eject builders merely to Duplicate/Export/Archive/Delete.

User-facing copy says `Duplicate`, never `Clone`. Internal legacy Clone classes/screens/routes are removed.

### Import / Export

Import and Export are one World Manager capability. Chunker/converter runtime is internal implementation only and never product navigation.

```text
World Manager
↓
Import / Export
↓
canonical Import / Export services
↓
verified on-demand conversion runtime when required
```

Native Java 1.21.4 uses the fast path. Other target formats appear in UI only when the verified server runtime reports support.

Import is file-first and always publishes a new managed world. Source format/version is detected automatically; canonical server target remains Java Edition 1.21.4.

Export uses the same service path for whole-world and Map Export Area. Map area is transient context and must never become a saved daily preset.

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

Banner Creator, Armor Color Creator, Special Builder Items, and a custom Spectator helper family remain excluded.

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

Desktop work is separate from the current Fabric World Manager UI pass. Do not modify launcher UX as part of World Manager cleanup unless the user explicitly requests it.

Fabric canonical source:

```text
client/fabric/
```

Transport ownership:

```text
lazybuilder:world     general managed-world/product intents
lazybuilder:map       spatial map intents/current-world push
lazybuilder:transfer  file bytes only
```

Current protocol contracts:

```text
World Control V3
- no manual Load/Unload
- no autoLoad/runtimeState metadata
- Duplicate terminology
- verified Export format catalog
- canManage / canTeleport presentation capabilities

Map Action V2
- map teleport / area export
- server-observed current managed world
- explicit CurrentWorldCleared when player enters unmanaged world
```

## Fabric UX target

Primary entry:

```text
M
→ World Map
→ Worlds
```

World Manager:

```text
WORLDS
├── Search
├── Pinned
├── Recent
├── All Worlds
├── Archived Worlds
└── + Add World
    ├── Create World
    └── Import World
```

Pinned and Recent are client-owned, user-scoped, server-scoped navigation preferences. Pinned does not mean keep-loaded. Recent means the player was actually observed inside the world.

Manage World:

```text
Teleport
Import / Export
Duplicate
World Settings
Archive
Delete
```

Import / Export is one final workspace:

```text
IMPORT / EXPORT
[ Export ] [ Import ]
```

Entry behavior:

```text
Manage World → Export tab
Add World    → Import tab
Map selection → Export tab with transient area
```

Daily Export defaults are user/server scoped, not per-world. Advanced overrides remain temporary unless the user explicitly saves them as default.

No standalone Import screen, standalone Export screen, Clone screen, or manual runtime-state UI should return.

## Map direction

The first-party fullscreen map follows the familiar Xaero-style mental model without copying Xaero code/assets/branding:

```text
left drag       pan
wheel           cursor-anchored zoom
CTRL + wheel    precise zoom
middle click    recenter
right click     contextual actions
hover           X/Z coordinates
selection       Export Area
```

`ClientMapSurfaceCache` uses bounded regional presentation storage per managed world + dimension. It never becomes server authority and never force-loads chunks.

## Transfer and recovery

Transfer uses the canonical bounded plugin-message transfer system; no extra HTTP/WebSocket/cloud transport is introduced.

Safety includes:

- checksum validation;
- bounded chunk/pipeline behavior;
- server upload disk-space check;
- client save-location disk-space check once authoritative download size is known;
- partial-file cleanup;
- disconnect cleanup;
- idle session expiry;
- one active client transfer flow.

Leaving the Import / Export screen does not imply cancellation. The UI uses `Continue in Background`. Safe cancellation is not exposed until it exists end-to-end.

Heavy world operations are single-flight per player. One bounded pending completion may be retained so a completed heavy action can be surfaced after reconnect rather than leaving a permanent stuck state.

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

## Validation state

The current branch is still **source-level implementation**, not fresh compile/live proof.

Do not claim current `Local` is compile-validated or runtime-validated until the final validation phase runs.

Final validation priority for the current World Manager pass:

```text
1. current Local compile/test
2. Paper + Fabric protocol compatibility (World V3 / Map V2)
3. M → Map → Worlds navigation across GUI scales
4. current-world push through normal teleport, command, portal and unmanaged world
5. Pinned / Recent / Search / Archived behavior
6. permission-limited UI and server authorization
7. automatic load + idle unload
8. occupied-world safeguards
9. Duplicate / Archive / Restore / Delete
10. whole-world Export native fast path
11. capability-driven conversion targets
12. Map Export Area through the same Export workspace
13. Import .zip / .mcworld
14. native file dialogs, large transfer, checksum and disk-space failures
15. disconnect/reconnect and pending completion recovery
16. shutdown/restart cleanup and persistence
```

Fix reproducible defects at the smallest wrong owner. Do not reopen duplicated architecture or reintroduce legacy runtime-state/product flows merely to work around a local bug.
