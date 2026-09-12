# World Manager Client Flow

Canonical owner for the user-facing World Manager navigation and operation flow.

## Goal

The World Manager UI must expose the existing server/application capabilities through one predictable client surface. The client is presentation/input only; it must not create a second lifecycle, settings, transfer, or filesystem authority.

## Primary navigation

```text
Open LazyBuilder
→ World Manager
   ├── Worlds
   ├── Create World
   ├── Import World
   └── Map / Xaero
```

`Worlds` is the default landing surface. Do not require the user to choose between separate command, file, conversion, or runtime products.

## Worlds

Each row/card represents one canonical managed `WorldId` and should show only decision-relevant state:

```text
Display Name
folder name (secondary)
ACTIVE / ARCHIVED
LOADED / UNLOADED
kind
```

Primary row action:

```text
ACTIVE + LOADED/UNLOADED → Teleport
ARCHIVED                 → Restore
```

Secondary actions open one `Manage World` surface rather than placing every operation in the world list.

## Manage World

```text
Manage World
├── Open / Teleport
├── Settings
├── Clone
├── Export
├── Load / Unload
├── Archive / Restore
└── Delete
```

Rules:

- show actions from canonical server state; do not infer success locally;
- disable or hide impossible actions instead of allowing a request that is known to be invalid;
- destructive actions (`Archive`, `Delete`) require explicit confirmation;
- `Delete` confirmation must use the canonical folder name because the server already requires exact confirmation;
- long operations return to one operation/progress state rather than opening a second subsystem screen;
- completion refreshes the affected world from the server.

## Create World

Creation remains intentionally small:

```text
Create World
→ Name
→ Type: Flat | Void
→ Create
```

No advanced world settings are duplicated here. Successful creation returns the new canonical world, which is already loaded and BUILD_READY.

## Import World

```text
Import World
→ native file picker (.zip / .mcworld)
→ upload
→ server validation / conversion if required
→ choose destination world identity/name when required by product flow
→ publish as managed world
→ show result
```

The file picker and upload are transport details of one Import operation. Do not expose a separate "Upload Manager" product surface.

## Export World

Whole-world export is reached from `Manage World → Export`.

```text
Export
→ choose target format/version
→ optional artifact name
→ server export
→ existing transfer download
→ native save dialog
```

`Export Area` remains a map/Xaero action because spatial selection belongs to map presentation. It must still use the same export service and transfer path as whole-world export.

## Settings

Settings use one server snapshot and one canonical mutation path.

```text
World Settings
├── General
├── Environment
├── Spawning
└── Gamerules
```

The client must display the server-returned value after a mutation. Common toggles are presentations of the same Paper/registry values; they are not additional client state.

`Reset to Build Ready` is one explicit action with confirmation. It is not a mode or background enforcement toggle.

## Map / Xaero

Map remains a contextual tool, not the root management UI.

```text
Xaero fullscreen map
├── Teleport Here
└── Export Area
```

A map action should not require the user to reopen World Manager when the managed current world is already resolved by the server.

## Operation feedback

Use four UI states consistently:

```text
idle
requesting
success
error
```

For long operations add bounded progress/status when the server can provide meaningful progress. Do not fake percentages for filesystem copy or converter work when no authoritative progress exists.

Errors should identify the action and recovery step without exposing host filesystem paths or stack traces.

## Channel / protocol shape

The dedicated World Manager UI should use one first-party world-control protocol surface for lifecycle/settings/listing operations rather than one channel per button.

Existing specialized channels remain specialized:

```text
lazybuilder:map       spatial map intents only
lazybuilder:transfer  file bytes only
lazybuilder:world     World Manager list/create/manage/settings intents
```

`lazybuilder:world` must delegate to the existing `WorldManager` application services. It must not implement duplicate business logic, registry state, operation locking, or file operations.

## Current implementation boundary

The current Fabric source already implements networking, native transfer dialogs, transfer state, and Xaero map actions. A general-purpose World Manager screen/control channel is not yet implemented. Therefore the next client-development slice should be `lazybuilder:world` plus the minimal world browser/navigation shell above, not additional map or transfer infrastructure.

## Efficiency rules

- list worlds only on screen open/explicit refresh or after a relevant mutation;
- no world-list polling;
- no settings polling;
- no client-side shadow registry;
- no directory scanning on the client;
- one request for one user action;
- reuse server-returned snapshots instead of issuing multiple per-row detail requests;
- expensive file/conversion work remains server-side and request-bound.

## Proof boundary

Remote CI can prove protocol encoding, state mapping, navigation compilation, and server delegation. Layout quality, input behavior, native file dialogs, Xaero placement, and perceived responsiveness require live client testing.
