# World Management

Canonical owner for LazyBuilder world lifecycle and world-setting behavior.

## Ownership

LazyBuilder World Manager is the intended native replacement for Multiverse-Core for the required builder workflow. It talks directly to Paper/Bukkit APIs; Multiverse is not part of the target runtime architecture.

## World Identity / Registry

Each managed world has one stable LazyBuilder identity that is independent from its presentation name and runtime load state.

```text
WorldId        → internal UUID identity
folderName     → canonical filesystem identity
displayName    → user-facing mutable name
kind           → FLAT | VOID | IMPORTED
lifecycle      → ACTIVE | ARCHIVED
autoLoad       → durable startup preference
```

`LOADED`, `UNLOADED`, `LOADING`, and `UNLOADING` are runtime state and must not be stored as durable registry lifecycle metadata.

The folder name is not renamed by a normal metadata update. Any future filesystem rename must be an explicit file/lifecycle operation so registry metadata can never get ahead of disk state. Folder uniqueness is case-insensitive to avoid ambiguous cross-platform world ownership.

The in-memory registry remains the canonical runtime owner. Durable metadata is persisted to `plugins/LazyBuilder/world/registry.yml` through one persistence boundary. Startup performs one bounded read; there is no registry polling or filesystem watcher. Writes publish through a temporary file and atomic move where supported.

## Create World — Confirmed V1

```text
Create World
├── Flat World
└── Void World
```

Creation intentionally exposes only the world name and type. Advanced settings belong to World Settings.

Both world kinds use one `WorldCreationService` path:

```text
validate identity
→ create through Paper runtime adapter
→ apply BUILD_READY
→ register
→ persist registry
→ publish success
```

If registry publication fails after runtime creation, the new runtime world is rolled back instead of reporting a partially managed world.

### Flat World

- vanilla `WorldType.FLAT` generation;
- default vanilla flat layers;
- structures disabled through the approved policy;
- automatic `BUILD_READY` profile;
- no separate custom flat generator.

### Void World

- one minimal all-air `ChunkGenerator`;
- vanilla noise, surface, caves, decorations, mobs, and structures disabled;
- centered 5×5 stone spawn platform at Y=64;
- world spawn at Y=65;
- automatic `BUILD_READY` profile.

## BUILD_READY

Every created world is immediately suitable for map building.

`BuildReadyPolicy` is the single source owner for the initial builder-safe defaults. It is applied during world creation or by an explicit Reset to Build Ready action; it is not a background enforcement loop. Later World Settings changes remain authoritative until the user explicitly resets them.

Default policy:

```text
structures             OFF
natural mob spawning   OFF
default game mode      CREATIVE
difficulty             NORMAL
PVP                     OFF
weather                 CLEAR
weather cycle           OFF
daylight cycle          OFF
time                    DAY (6000 ticks)
fire tick               OFF
mob griefing            OFF
random tick speed       0
patrol spawning         OFF
wandering trader        OFF
insomnia/phantom        OFF
warden spawning         OFF
raids                   OFF
spawn-chunk persistence OFF when safe through target API
```

Do not override unrelated vanilla gamerules merely for completeness. World Settings owns explicit later overrides. Domain policy stays independent from Bukkit/Paper enum types; the Paper adapter maps it to runtime APIs.

`default game mode = CREATIVE` is a World Manager entry policy rather than a native per-world Paper property. World Settings will become the durable owner for this preference; the teleport path must consume that value rather than changing the global server default game mode.

## Load / Unload

Runtime state has one ephemeral owner:

```text
UNLOADED → LOADING → LOADED → UNLOADING → UNLOADED
```

Stable endpoints are idempotent: loading an already-loaded world and unloading an already-unloaded world are no-ops. A failed load returns state to `UNLOADED`; a failed unload returns state to `LOADED`.

Startup initializes runtime state from Paper once and auto-loads only records that are both `ACTIVE` and `autoLoad=true`. This is startup work, not periodic polling.

Unload safety is owned by the Paper runtime boundary:

- the global fallback world cannot be unloaded;
- players are moved to the fallback spawn before unload;
- failure to move any player cancels the unload;
- the target world is saved before Paper unloads it;
- blank fallback configuration resolves to the server's primary loaded world;
- an explicit fallback name must already be loaded.

## Teleport to World

`WorldTeleportService` owns the server-side use case:

```text
managed WorldId
→ auto-load through WorldRuntimeService when required
→ resolve the managed world's spawn
→ teleport the online player
```

The destination is intentionally the world spawn for V1. Last-location-per-world behavior is not part of the contract. Xaero `Teleport to Location` remains a separate later client-integration path and will reuse the same server authority instead of creating another teleport system.

## Confirmed Capability Surface

```text
Map Preview (Xaero integration)
Teleport to Location
World Browser / Teleport to World
Create World
Manage World
World Settings
Import / Export
Clone
Load / Unload
Archive / Delete
```

Import/export/conversion details are owned by `conversion.md`.

## Safety Rules

- Server plugin is authoritative.
- Paper world lifecycle calls execute on the primary server thread.
- Create never loads an existing folder as if it were a new world.
- Registry persistence is fail-closed; malformed metadata blocks startup instead of silently discarding ownership.
- Delete/archive/clone/export validate current world state.
- Destructive operations require explicit confirmation.
- Players must not be stranded in an unloading/deleting world.
- File operations must not run unsafely against live world writes.
- Runtime claims require LIVE_SERVER proof.

## Implementation Order

Current implementation proceeds from stable ownership outward:

```text
world identity + registry          ✅ source + CI
BUILD_READY policy                 ✅ source + CI
Flat / Void creation               ✅ source + CI
Load / Unload                      ✅ source + CI
Teleport to World                  ✅ source + CI
→ World Settings
→ file operations
→ internal conversion runtime
→ Import / Export
→ client mod + Xaero integration
```

Remote CI proves compilation and targeted unit behavior for the current source slices. Actual Paper generation, gamerule application, player evacuation, world load/unload, teleport, rollback, and persistence behavior remain LIVE_SERVER concerns.
