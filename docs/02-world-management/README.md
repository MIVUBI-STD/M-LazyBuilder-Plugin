# World Management

Canonical owner for LazyBuilder world lifecycle and world-setting behavior.

## Ownership

LazyBuilder World Manager is the intended native replacement for Multiverse-Core for the required builder workflow. It talks directly to Paper/Bukkit APIs; Multiverse is not part of the target runtime architecture.

## World Identity / Registry

Each managed world has one stable LazyBuilder identity that is independent from its presentation name and runtime load state.

```text
WorldId          → internal UUID identity
folderName       → canonical filesystem identity
displayName      → user-facing mutable name
kind             → FLAT | VOID | IMPORTED
lifecycle        → ACTIVE | ARCHIVED
autoLoad         → durable startup preference
defaultGameMode  → durable world-entry preference
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

`default game mode = CREATIVE` is a LazyBuilder world-entry preference rather than a global server default. Teleport to World applies the target world's durable preference after a successful teleport.

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

## World Settings

World Settings uses one `WorldSettingsService`. Opening a settings snapshot is an explicit request and may load an unloaded target once; there is no settings polling.

Ownership is split by the real source of truth:

```text
LazyBuilder registry
├── Auto Load
└── Default Game Mode / world-entry preference

Paper world state
├── Difficulty
├── PVP
├── Time
├── Weather
├── Spawn Location
└── Gamerules
```

Runtime settings are read back from Paper after load. The UI/protocol must display the canonical values returned by the server rather than assuming a requested mutation succeeded.

### General

Implemented source contract:

- Default Game Mode;
- Difficulty;
- PVP;
- Auto Load;
- Spawn Location;
- Set Current Position as Spawn.

`Default Game Mode` is persisted with the managed-world registry and consumed by Teleport to World. `Auto Load` remains the existing durable startup preference. Other General values are Paper-owned world state.

### Gamerules

The full gamerule list is discovered from `GameRule.values()` in the active target API. LazyBuilder does not maintain a second static version list. Each returned rule carries its runtime type (`BOOLEAN` or `INTEGER`) and canonical current value.

Common-rule presentation such as Daylight Cycle, Weather Cycle, Mob Griefing, Fire Tick, Mob Spawning, Keep Inventory, and Random Tick Speed is a client presentation concern over the same canonical gamerules. `Show All Gamerules` must use this discovered list rather than a hardcoded copy.

### Environment

Time and Weather mutations write directly to Paper. Time Lock, Weather Lock, Random Tick, Fire Spread, and Mob Griefing are presentations of the same canonical gamerules where applicable; do not create duplicate state for the Environment tab.

### Reset to Build Ready

`Reset to Build Ready` is explicit. It reapplies the existing `BuildReadyPolicy` to the loaded Paper world and restores the durable Default Game Mode preference to the BUILD_READY value. It does not start a daemon or future enforcement loop.

### Spawning

Natural spawning and vanilla gamerules already share the runtime boundary. Category controls for Animals, Monsters, Ambient, and Water remain the next settings slice and must use vanilla/Paper spawn controls rather than introducing a custom spawn engine.

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
- Paper world lifecycle and settings calls execute on the primary server thread.
- Create never loads an existing folder as if it were a new world.
- Registry persistence is fail-closed; malformed metadata blocks startup instead of silently discarding ownership.
- LazyBuilder-only settings are rolled back in memory if registry persistence fails.
- Gamerule writes validate the actual Paper rule type before applying values.
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
World Settings core                ✅ source, CI pending
→ spawning category controls
→ file operations
→ internal conversion runtime
→ Import / Export
→ client mod + Xaero integration
```

Remote CI proves compilation and unit-test behavior only when the corresponding run is green. Actual Paper generation, gamerule application, settings mutation, player evacuation, world load/unload/teleport, rollback, and persistence behavior remain LIVE_SERVER concerns.
