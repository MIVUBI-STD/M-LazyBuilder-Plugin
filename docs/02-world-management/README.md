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

The current registry is the canonical in-memory owner. Persistent registry storage will be added only when the first create/import lifecycle requires durable publication; it must not become a second source of truth.

## Create World — Confirmed V1

```text
Create World
├── Flat World
└── Void World
```

Creation intentionally exposes only the world name and type. Advanced settings belong to World Settings.

### Flat World

- vanilla-compatible flat generation;
- simple build surface;
- structures disabled;
- automatic `BUILD_READY` profile.

### Void World

- empty terrain;
- safe spawn platform by default;
- automatic `BUILD_READY` profile.

## BUILD_READY

Every created world is immediately suitable for map building.

Default policy:

```text
structures             OFF
natural mob spawning   OFF
default game mode      CREATIVE
difficulty             NORMAL
weather                 CLEAR
weather cycle           OFF
daylight cycle          OFF
time                    DAY
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

Do not override unrelated vanilla gamerules merely for completeness. World Settings owns explicit later overrides.

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
- Delete/archive/clone/export validate current world state.
- Destructive operations require explicit confirmation.
- Players must not be stranded in an unloading/deleting world.
- File operations must not run unsafely against live world writes.
- Runtime claims require LIVE_SERVER proof.

## Implementation Order

Current implementation proceeds from stable ownership outward:

```text
world identity + registry
→ BUILD_READY policy
→ Flat / Void creation
→ Load / Unload
→ Teleport
→ World Settings
→ file operations
→ internal conversion runtime
→ Import / Export
→ client mod + Xaero integration
```

The registry foundation is source-level work only until compile/CI evidence is green; Paper behavior remains unproven until LIVE_SERVER verification.
