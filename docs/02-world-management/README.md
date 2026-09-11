# World Management

Canonical owner for LazyBuilder world lifecycle and world-setting behavior.

## Ownership

LazyBuilder World Manager is the intended native replacement for Multiverse-Core for the required builder workflow. It talks directly to Paper/Bukkit APIs; Multiverse is not part of the target runtime architecture.

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

## Safety Rules

- Server plugin is authoritative.
- Delete/archive/clone/export validate current world state.
- Destructive operations require explicit confirmation.
- Players must not be stranded in an unloading/deleting world.
- File operations must not run unsafely against live world writes.
- Runtime claims require LIVE_SERVER proof.

## Current Design Order

Feature contracts are discussed sequentially before source implementation. `Create World` and `BUILD_READY` are approved. The next detailed design topic is `World Settings` unless the user changes priority.
