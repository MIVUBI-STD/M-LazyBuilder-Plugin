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

`default game mode = CREATIVE` is a World Manager entry policy rather than a native per-world Paper property. The future teleport/world-entry path will apply that preference; creation does not mutate the global server default game mode.

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
world identity + registry          ✅ source
BUILD_READY policy                 ✅ source
Flat / Void creation               ✅ source
→ Load / Unload
→ Teleport
→ World Settings
→ file operations
→ internal conversion runtime
→ Import / Export
→ client mod + Xaero integration
```

The current creation path is source-level work until compile/CI evidence is green; actual Paper generation, gamerule application, rollback, and persistence behavior remain unproven until LOCAL_CODE/LIVE_SERVER verification.
