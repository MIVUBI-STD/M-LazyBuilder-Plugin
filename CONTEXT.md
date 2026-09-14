# LazyBuilder — Stable Context

## Product

LazyBuilder is a modular Minecraft Java 1.21.4 builder-server workspace. The product is intentionally split by semantic ownership instead of using one master runtime component.

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
    ├── Map Manager
    ├── Utility Manager
    └── Performance Manager
```

External build/edit tools such as Vanilla Minecraft, Axiom, WorldEdit/FAWE, FastAsyncVoxelSniper, ezEdits, and MetaBrushes remain external specialist owners. LazyBuilder must not duplicate their build/edit workflows without a new explicit requirement and ownership review.

## Repository authority

```text
Local = active development / source authority
main  = stable / release authority
```

Do not silently fall back to `main`. Do not create side development branches unless explicitly requested.

## Engineering model

- one semantic owner per responsibility;
- one primary execution path per behavior;
- one persisted fact has one authority;
- no duplicate managers, registries, schedulers, config systems, process markers, transfer systems, or filesystem authorities;
- prefer deletion/consolidation before introducing a new abstraction;
- source/CI proof is distinct from local/live runtime proof;
- no NMS unless a proven requirement cannot be met through stable Paper/Bukkit APIs;
- no idle/background subsystem without a concrete runtime need;
- use the cheapest proof capable of falsifying the changed claim.

Canonical execution discipline: `docs/04-system/development-discipline.md`.
Canonical specialist routing: `docs/04-system/skill-routing.md`.
Current proof authority: `docs/05-operations/current-verification.md`.

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

Runtime ownership:

- actual Paper world folders → `world-system/worlds/`;
- World-Manager registry/import/export/backup/work data → `world-system/`;
- converter support assets → `tools/lazybuilder/cache/converter/`;
- Server-Manager and local-control configuration → `tools/lazybuilder/config/`;
- disabled third-party plugin JARs → `tools/lazybuilder/disabled-plugins/`;
- minimum plugin rollback snapshots → `tools/lazybuilder/plugin-backups/`;
- Paper/runtime/plugin files → `server/`.

Archive is a lifecycle state, not a second physical world store.

## Component ownership

### Server-Manager

Desktop-native authority for workspace bootstrap, Java/Paper runtime discovery, start/stop/restart, process identity/recovery, health/resource settings, Paper provisioning/update, and internal bundled core synchronization.

Runtime policy:

```text
Paper update          -> explicit user decision
bundled core sync     -> internal maintenance before start/restart
CPU scheduling        -> JVM/OS managed
resource tuning       -> Performance / Boost / Custom RAM
process identity      -> one server-process.json authority
server configuration  -> one server_config authority
```

### Plugin-Manager

Desktop-native authority for third-party Paper plugin inventory, metadata, install/update, dependency/compatibility checks, duplicate resolution, restart-safe enable/disable, safe JAR removal with plugin data preserved, and minimum rollback state.

Do not recreate persistent category ownership, timestamped backup history, remove-data/quarantine product paths, or hot reload without a proven requirement.

### World-Manager

Paper-side authority for managed world lifecycle, runtime coordination, files, settings, import/export/conversion, transfer safety, and server authorization.

Durable lifecycle:

```text
ACTIVE
ARCHIVED
```

Loaded/unloaded/loading/unloading are runtime state, not persisted product lifecycle values.

Runtime behavior:

```text
Teleport / settings / required use
→ load automatically when needed

world empty + idle + no conflicting operation
→ unload automatically
```

Manual Load/Unload and per-world `autoLoad` are not product features.

World Manager owns:

- managed-world discovery/adoption;
- Flat/Void creation and BUILD_READY defaults;
- teleport;
- automatic load + idle unload;
- Duplicate;
- backup;
- Archive/Restore;
- safe Delete;
- Import/Export and edition/version conversion;
- settings and durable metadata;
- bounded heavy operations;
- transfer/import/export filesystem safety;
- converter capability discovery and request-bound execution.

User-facing terminology is `Duplicate`, never `Clone`.

### Utilities-Manager (Paper)

Server-side builder conveniences only:

```text
World Safety
Movement
Build Helpers
```

It does not own world lifecycle, desktop process management, plugin installation, client QoL, or performance tuning.

### Map Manager (Fabric)

Owns first-party world/map UI, navigation, current managed-world presentation, world settings/lifecycle presentation, Import/Export/transfer UI, Map Export Area selection, and the shared World-Manager protocol client.

### Utility Manager (Fabric)

Owns passive non-building client convenience such as chat/session convenience, reconnect/disconnect presentation, borderless-window presentation, resource-reload notification, contextual screenshot naming, and local preference persistence for those features.

### Performance Manager (Fabric)

Owns lightweight performance/resource observation and background-FPS policy. It may detect external optimizer capabilities but must not replace renderer/shader/culling engines. When Dynamic FPS is present, LazyBuilder's background-FPS controller yields ownership.

## Source boundaries

```text
EngineData/Frontend/RustApp/  canonical Tauri 2 + Svelte 5 + Rust desktop
shared/protocol/               neutral Paper/Fabric contracts
modules/world-manager/         Paper World-Manager
modules/utilities-manager/     Paper Utilities-Manager
client/map-manager/            Fabric Map Manager
client/utility-manager/        Fabric Utility Manager
client/performance-manager/    Fabric Performance Manager
```

Each Fabric Manager is one source authority and one output JAR. Managers do not import one another's implementation packages.

## Protocol boundaries

Minecraft in-game traffic reuses the existing play connection:

```text
lazybuilder:world     general managed-world/product intents
lazybuilder:map       spatial map intents/current-world push
lazybuilder:transfer  file bytes only
```

Current shared protocol contracts:

```text
World Control V5
- no manual Load/Unload
- no autoLoad/runtimeState product metadata
- Duplicate terminology
- verified Export format catalog
- server-authoritative Import inspection
- explicit abandoned Import-review discard
- permission presentation capabilities

Map Action V2
- map teleport
- area export
- server-observed current managed world
- explicit CurrentWorldCleared for unmanaged worlds
```

Desktop ↔ Paper local control is a separate authenticated loopback contract currently using protocol version 2. Desktop protocol versioning must not be confused with World Control V5 or Map Action V2.

## World Manager UX

Primary Fabric flow:

```text
M
→ World Map
→ Worlds
```

Worlds surface:

```text
Search
Pinned
Recent
All Worlds
Archived Worlds
+ Add World
  ├── Create World
  └── Import World
```

Manage World:

```text
Teleport
Import / Export
Duplicate
World Settings
Archive
Delete
```

Import and Export share one workspace. Whole-world export and Map Export Area use the same canonical export service path.

The fullscreen map is first-party LazyBuilder UI. Xaero may be used only as an interaction-quality reference; LazyBuilder does not depend on Xaero as the map authority, transfer owner, or runtime requirement.

## Transfer and recovery

Transfer uses one bounded plugin-message transfer system with ordered chunks, SHA-256 validation, storage preflight, partial-file cleanup, disconnect cleanup, and bounded session ownership. There is no second HTTP/WebSocket/cloud transfer plane and no cross-connection byte-resume subsystem.

Heavy world-operation completion recovery is distinct from byte-transfer resume and remains bounded/operation-local.

## BUILD_READY defaults

New builder worlds target Creative-oriented defaults: natural mob spawning disabled, clear weather, daylight/weather cycles disabled as configured, fire tick and mob griefing disabled, random tick speed 0, unnecessary events disabled where supported, and safe spawn behavior. Flat and Void remain the supported first-party creation types.

## Validation authority

Do not hard-code a permanent current SHA or workflow run in this stable context. Determine readiness from:

```text
current Local HEAD
→ latest Verify workflow for that exact HEAD
→ component-specific build/test evidence
→ LOCAL_CODE proof where CI cannot prove behavior
→ LIVE_SERVER proof for actual Paper/Minecraft behavior
```

`REMOTE_GITHUB` success proves source/static/build/package claims only. It does not prove actual installed Windows behavior, real Paper lifecycle, Fabric interaction inside Minecraft, large-file transfer behavior, gameplay behavior, or restart/shutdown persistence.

The next phase after remote synchronization is local/runtime proof, not further architecture expansion. Fix reproducible defects at the smallest owning boundary and do not reintroduce legacy Load/Unload, autoLoad, Clone, duplicate transfer systems, or extra Managers as workarounds.
