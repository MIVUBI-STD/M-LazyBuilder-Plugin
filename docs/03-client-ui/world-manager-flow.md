# World Manager Client Flow

Canonical owner for the user-facing World Manager navigation and operation flow.

## Goal

The World Manager UI exposes server/application capabilities through one predictable client surface. The client is presentation/input only; it must not create a second lifecycle, settings, transfer, registry, or filesystem authority.

## Primary navigation

```text
Open LazyBuilder
→ World Manager
   ├── Worlds
   ├── Create World
   ├── Import World
   └── Map / Xaero
```

`Worlds` is the default landing surface.

## Worlds

Each row/card represents one canonical managed `WorldId` and shows decision-relevant state:

```text
Display Name
folder name
ACTIVE / ARCHIVED
LOADED / UNLOADED
kind
```

Primary actions come from canonical server state. Secondary actions open the existing management screens rather than creating per-command subsystems.

## Manage World

```text
Manage World
├── Teleport
├── Settings
├── Clone
├── Export
├── Load / Unload
├── Archive / Restore
└── Delete
```

Rules:

- show actions from canonical server state;
- do not infer success locally;
- destructive actions require explicit confirmation;
- Delete confirmation must satisfy the server's exact-name guard;
- long operations use canonical operation/task state;
- completion refreshes affected state from the server.

## Create World

```text
Create World
→ World Folder
→ Display Name
→ Type: Flat | Void
→ Create
```

Advanced settings are intentionally not duplicated into Create. New worlds use canonical BUILD_READY defaults.

## Import World

```text
Import World
→ destination folder + display name
→ native file picker (.zip / .mcworld)
→ bounded transfer upload
→ server validation / optional conversion
→ publish as managed world
→ return canonical world state
```

The picker and upload are transport details of one Import flow. There is no separate Upload Manager.

## Export World

Whole-world Export is reached from the managed-world surface.

```text
Export
→ artifact name
→ native Java 1.21.4 target in current UI
→ safe server snapshot/package
→ existing transfer download
→ native save dialog
```

Additional target formats should only be surfaced when the client receives a verified supported-format catalog. `Export Area` remains a map/Xaero action because spatial selection belongs to map presentation, while still reusing the same Export and transfer owners.

## Settings

Settings use one server snapshot and one canonical mutation path. The compact current surface includes:

```text
Auto Load
Default Game Mode
Difficulty
PVP
Set Current Position as Spawn
Reset to BUILD_READY
```

The client displays server-returned state after mutation. It does not maintain a second settings store or polling loop.

## Map / Xaero

```text
Xaero fullscreen map
├── Teleport Here
└── Export Area
```

Map actions remain contextual and spatial. They do not replace the general World Manager surface.

## Operation feedback

Use consistent bounded states:

```text
idle
requesting / running
success
error
```

Do not fake progress percentages when the server has no authoritative progress value. Errors should identify the failed action and recovery step without exposing host filesystem paths or stack traces.

## Channel / protocol shape

```text
lazybuilder:world     World Manager list/create/manage/settings intents
lazybuilder:map       spatial map intents only
lazybuilder:transfer  file bytes only
```

`lazybuilder:world` delegates to existing World-Manager services. It does not own duplicate business logic, registry state, operation locking, conversion, or file operations.

## Current implementation boundary

The first-party Fabric World Manager is source-implemented for:

```text
list / refresh
create Flat / Void
teleport
load / unload
archive / restore
clone
settings
permanent delete
import publication
native Java 1.21.4 whole-world export
```

Native file dialogs, transfer controllers, Xaero actions, and the dedicated `lazybuilder:world` control path are also source-implemented. Remote CI proves compilation/tests only; runtime UI and Paper behavior still require live validation.

## Efficiency rules

- list worlds only on screen open, explicit refresh, or after relevant mutation;
- no world-list polling;
- no settings polling;
- no client-side shadow registry;
- no directory scanning on the client;
- reuse server-returned snapshots;
- expensive file/conversion work remains server-side and request-bound.

## Proof boundary

Remote CI can prove protocol encoding, source wiring, state mapping, navigation compilation, and server delegation. Layout quality, actual input behavior, native dialogs, Xaero placement/transforms, real transfer behavior, and perceived responsiveness require `LOCAL_CODE` / `LIVE_SERVER` testing.
