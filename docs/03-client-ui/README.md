# Client UI / World Map

Canonical owner for LazyBuilder client-side interaction and map presentation. Network ownership remains canonical in [`../04-system/networking.md`](../04-system/networking.md). World Manager navigation and operation flow is canonical in [`world-manager-flow.md`](world-manager-flow.md).

## UI Direction

LazyBuilder uses a dedicated Minecraft 1.21.4 Fabric client for the in-game experience. The primary entry is map-first:

```text
M
→ LazyBuilder World Map
   ├── pan / zoom / coordinates
   ├── player position
   ├── Teleport Here
   ├── Export Area
   └── Worlds
       → World Manager
```

Xaero World Map 1.21.4 is the mandatory interaction-quality reference for the fullscreen map. The acceptance target is behavioural/interaction parity for the map experience: the same mental model, control expectations, camera feel, contextual-menu flow, explored-map persistence and unobtrusive fullscreen presentation. LazyBuilder does not copy Xaero source code, textures, icons or branding and does not require Xaero at runtime.

## Xaero-Parity Lock

For functionality shared by LazyBuilder and Xaero, deviation is not accepted without a platform limitation or a LazyBuilder-specific server-safety requirement.

Required parity behaviour:

```text
M                     open fullscreen world map directly
left mouse drag       pan continuously
mouse wheel           cursor-anchored animated zoom
CTRL + wheel          precise/fine zoom
+ / -                 alternative zoom controls
right click           "Choose an Option" contextual menu at cursor
ESC                    close context first, then selection, then map
player marker          directional arrow, not a generic square
hover                  live X/Z map coordinates
exploration            discovered terrain remains mapped after reopen/restart
unexplored terrain     visually distinct and non-authoritative
map camera             preserved while visiting LazyBuilder Worlds UI
area selection         visible overlay + explicit confirmation
```

LazyBuilder-specific context options currently replace Xaero-only waypoint/player-radar actions:

```text
Teleport Here
Export Area
Center Map Here
Copy Coordinates
```

This is intentional product substitution, not a different interaction model. Unsupported Xaero ecosystems (waypoints, minimap radar, claims, cave-map layers) must not be represented by dead buttons.

## Responsibility Boundary

Client owns screen/layout, keybind/open-close behaviour, map camera/input, explored-map presentation cache, map-location input, native file picker/save dialog, and local presentation state.

Server owns permissions, managed-world existence/state, safe teleport resolution, lifecycle operations, settings persistence/mutation, import/export validation, transfer ordering/limits/checksums, and final filesystem publication.

The client is never authoritative for server world state.

## Implemented Fabric Client Surface

```text
Fabric client
├── WorldMapScreen
├── ClientMapSurfaceCache
├── WorldManagerScreen
├── AddWorldScreen
├── CreateWorldScreen
├── ImportWorldScreen
├── WorldSettingsScreen
├── CloneWorldScreen
├── ExportWorldScreen
├── DeleteWorldScreen
├── ClientWorldController
├── ClientMapController
├── ClientTransferController
├── ClientFileDialogs
├── lazybuilder:world payload
├── lazybuilder:map payload
└── lazybuilder:transfer payload
```

The old `MapPreviewScreen` / `MapPreviewClientUi` path was removed so there is one map entry and one map presentation owner.

## World Map Behaviour

The fullscreen map follows the familiar Xaero control model:

```text
left-drag          pan camera
mouse wheel        cursor-anchored smooth zoom
CTRL + wheel       precise zoom increments
+ / -              alternative stepped zoom
right-click        cursor-local contextual menu
Teleport Here      server resolves safe Y and teleports
Export Area        select corner 1 + corner 2 + confirm
Center Map Here    move map camera to selected location
Copy Coordinates   copy selected X/Z to clipboard
Worlds             open secondary World Manager without losing map camera
```

The player marker is directional. Hovered map coordinates and zoom are shown unobtrusively. Dimension and managed-world identity remain visible without turning the screen into a dashboard.

### Map memory

`ClientMapSurfaceCache` stores presentation-only terrain samples per managed-world + dimension scope.

Rules:

- never force-load chunks;
- only sample terrain already available to the client;
- queue missing visible samples and process them with a per-frame budget;
- retain bounded in-memory map data;
- persist compressed sampled map memory under the LazyBuilder client data folder;
- separate every managed world and dimension;
- use Minecraft map colours plus lightweight relief shading;
- persisted map data is never server authority.

This prevents the previous synchronous full-visible-map sampling behaviour and allows explored terrain to remain visible after closing/reopening the map or restarting the client.

## World Manager

World Manager is secondary to the map and is reached through `Worlds`.

```text
Worlds
├── + Add World
│   ├── Create New World
│   └── Import Existing World
├── managed world list
└── selected world
    ├── Teleport
    ├── Load / Unload
    ├── Export World
    ├── Settings
    ├── Clone
    ├── Archive / Restore
    └── Delete
```

Returning from World Manager restores the existing map screen rather than constructing a new one, preserving camera/zoom/selection presentation state.

## Import

Import is file-first:

```text
Import Existing World
→ optional display name
→ native .zip/.mcworld picker
→ prepare/hash off render thread
→ bounded transfer upload + visible progress
→ derive internal destination folder automatically
→ server validation / optional conversion
→ publish managed world
```

Internal destination-folder naming is not exposed as a normal user decision. Duplicate names are resolved by the server-side import path.

## Whole-world Export

```text
Export World
→ artifact name
→ server snapshot/package
→ ExportReady
→ existing transfer download
→ native Save As dialog
→ checksum/finalize local file
```

`ExportReady` is connected to the same client transfer controller used by area exports; there is no second download system.

## Channel Ownership

```text
lazybuilder:world     canonical world list/create/manage/settings intents
lazybuilder:map       spatial map intents only
lazybuilder:transfer  file bytes only
```

`lazybuilder:world` delegates to World Manager services and must not implement duplicate lifecycle, settings, locking, conversion, or filesystem logic.

`lazybuilder:map` remains spatial only. `Export Area` reuses canonical export and transfer owners.

`lazybuilder:transfer` remains transport only. It does not learn Import/Export business semantics.

## File Transfer Contract

```text
Client Mod
→ lazybuilder:transfer
→ PaperTransferPayloadAdapter
→ TransferSessionService
→ managed import/export storage
```

Contract:

- protocol version 2;
- permission `lazybuilder.world.manage`;
- maximum wire payload 30 KiB;
- maximum file-data chunk 24 KiB;
- bounded four-chunk client pipeline;
- ordered per-player server request lane;
- one seekable file channel per active transfer;
- file/hash work off the Paper main thread;
- malformed/oversized/out-of-order/unauthorized requests fail closed;
- failed requests tied to an active session clean stale server state;
- disconnect aborts the player's sessions;
- idle sessions expire opportunistically without a polling thread;
- plugin disable unregisters the channel and cleans tracked transfer state.

## Efficiency

- one fullscreen map owner and one map entry path;
- bounded map sampling per frame;
- persistent map memory only for observed client terrain;
- no background world-list/settings polling;
- no client-side shadow world registry;
- no permanent transfer worker or custom socket loop;
- hashing/chunk I/O exists only for explicit requests;
- list/settings refresh on screen open, explicit refresh, or relevant mutation;
- expensive file/conversion work remains server-side and request-bound.

## Proof Boundary

The current development pass intentionally defers CI/runtime validation until implementation is complete. Final validation must cover Fabric compilation, actual map rendering/input, cursor-anchored normal and precise zoom, context-menu ordering, player-arrow orientation, persistent map memory, native Windows dialogs, real upload/download, server permissions, teleport resolution, area export, whole-world export, and World Manager navigation on a live 1.21.4 client/server pair.
