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

Xaero World Map 1.21.4 is the mandatory interaction-quality reference for the fullscreen map. The acceptance target is behavioural/interaction parity for the map experience: the same mental model, control expectations, camera feel, contextual-menu flow, explored-map persistence, multi-scale terrain readability and unobtrusive fullscreen presentation. LazyBuilder does not copy Xaero source code, textures, icons or branding and does not require Xaero at runtime.

## Xaero-Parity Lock

For functionality shared by LazyBuilder and Xaero, deviation is not accepted without a platform limitation or a LazyBuilder-specific server-safety requirement.

Required parity behaviour:

```text
M                     open fullscreen world map directly
left mouse drag       pan continuously
mouse wheel           cursor-anchored animated zoom
CTRL + wheel          precise/fine zoom
+ / -                 alternative zoom controls
middle mouse          recenter on player
right click           contextual menu at cursor
ESC                    close context first, then selection, then map
player marker          directional arrow, not a generic square
hover                  live X/Z map coordinates
exploration            discovered terrain remains mapped after reopen/restart
unexplored terrain     visually distinct and non-authoritative
map camera             preserved while visiting LazyBuilder Worlds UI
area selection         visible overlay + explicit confirmation
far zoom               aggregate multiple terrain samples; never one isolated block per large cell
close zoom             increase screen-pixel density so roads/buildings do not become coarse square cells
coordinate transform   use the same continuous camera scale for pan, hover, selection and player marker
large worlds            old explored regions remain available without retaining the whole map in RAM
```

LazyBuilder-specific context options currently replace Xaero-only waypoint/player-radar actions:

```text
Teleport Here
Export Area
Center Map Here
Copy Coordinates
```

Unsupported Xaero ecosystems such as waypoint/radar/claims/cave-map features must not appear as dead controls.

## Responsibility Boundary

Client owns screen/layout, keybind/open-close behaviour, map camera/input, explored-map presentation cache, map-location input, native file picker/save dialog, and local presentation state.

Server owns permissions, managed-world existence/state, safe teleport resolution, lifecycle operations, settings persistence/mutation, import/export validation, transfer ordering/limits/checksums, and final filesystem publication.

The client is never authoritative for server world state.

## Implemented Fabric Client Surface

```text
Fabric client
├── LazyBuilderClientUi
├── LbUi / LbButtonWidget
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

The old preview path and old WorldManager-named client entry class were removed so there is one map entry and one current UI owner.

## World Map Behaviour

```text
left-drag          pan camera
mouse wheel        cursor-anchored smooth zoom
CTRL + wheel       precise zoom increments
+ / -              alternative stepped zoom
middle-click       recenter on player
right-click        cursor-local contextual menu
Teleport Here      server resolves safe Y and teleports
Export Area        select corner 1 + corner 2 + confirm
Center Map Here    move map camera to selected location
Copy Coordinates   copy selected X/Z to clipboard
Worlds             open secondary World Manager without losing map camera
```

The player marker is directional. Hovered map coordinates and zoom are shown unobtrusively. Dimension and managed-world identity remain visible without turning the screen into a dashboard. The map and World Manager use the first-party LazyBuilder visual system instead of vanilla button chrome.

### Map rendering density

`WorldMapScreen` decouples map camera scale from raw render-cell size. Close zoom renders with smaller screen cells, medium zoom uses an intermediate cell size, and wide zoom increases the cell footprint while retaining the same continuous world-coordinate transform.

All coordinate-sensitive operations use the same `blocksPerPixel` transform:

```text
camera pan
hover coordinates
cursor-anchored zoom
selection overlay
player marker
world-to-screen conversion
screen-to-world conversion
```

No action is allowed to maintain a second coordinate transform.

### Regional map memory and LOD

`ClientMapSurfaceCache` stores presentation-only terrain samples per managed-world + dimension scope. Persistent map memory is no longer one whole-scope snapshot. It is partitioned into sparse **128x128-block regional files**.

Rules:

- never force-load chunks;
- only sample terrain already available to the client;
- queue missing visible samples and process them with a per-frame budget;
- keep only a bounded LRU set of map regions resident in memory;
- load regional files asynchronously instead of blocking the render thread;
- flush dirty regional snapshots through one ordered async write lane;
- keep every managed world and dimension isolated;
- do not persist the temporary `unmanaged` identity;
- preserve live samples if an older disk load completes afterward;
- migrate the previous version-1 whole-scope snapshot into regional files once;
- use Minecraft map colours plus lightweight relief shading;
- at wider zoom levels blend a bounded multi-point footprint from the same canonical base-column cache;
- persisted map data remains presentation state and never server authority.

The regional model removes the previous failure mode where a very large explored world eventually lost its oldest remembered areas merely because one global in-memory LRU reached its column limit. Old regions can leave RAM and later load again from disk when the camera revisits them.

The multi-sample LOD path remains the only LOD source; there is no second map database or pyramid persistence system.

## World Manager

World Manager is secondary to the map and reached through `Worlds`. Wide screens use a two-pane list/detail workspace; narrow logical resolutions use a single-pane list → detail flow so GUI Scale does not push actions off-screen.

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

Builder-facing screens avoid internal folder/registry/transfer terminology except where a destructive safety guard genuinely requires it.

## Import

```text
Import Existing World
→ optional world name
→ native .zip/.mcworld picker
→ prepare/hash off render thread
→ bounded transfer upload + visible progress
→ server validation / optional conversion
→ publish managed world
```

Internal destination-folder naming is automatic.

## Whole-world Export

```text
Export World
→ user-facing file name
→ server snapshot/package
→ ExportReady
→ native Save As dialog
→ existing transfer download
→ checksum/finalize local file
```

`ExportReady` is connected to the same client transfer controller used by area exports; there is no second download system.

## Channel Ownership

```text
lazybuilder:world     canonical world list/create/manage/settings intents
lazybuilder:map       spatial map intents only
lazybuilder:transfer  file bytes only
```

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
- disconnect aborts the player's sessions;
- plugin disable cleans tracked transfer state.

## Efficiency

- one fullscreen map owner and one map entry path;
- bounded map sampling per frame;
- adaptive screen-pixel density;
- regional map persistence with bounded resident memory;
- asynchronous regional reads and ordered asynchronous writes;
- persistent map memory only for observed client terrain;
- no background world-list/settings polling;
- no client-side shadow world registry;
- no permanent transfer worker or custom socket loop;
- expensive file/conversion work remains server-side and request-bound.

## Proof Boundary

Implementation remains ahead of proof. Final validation must cover Fabric compilation, actual map rendering/input, regional cache migration/read/write/eviction, revisiting old explored regions, close-range and far-zoom terrain readability, cursor-anchored zoom, context-menu ordering, player-arrow orientation, native Windows dialogs, real upload/download, server permissions, teleport resolution, area export, whole-world export, responsive World Manager navigation, and the full flow on a live 1.21.4 client/server pair.
