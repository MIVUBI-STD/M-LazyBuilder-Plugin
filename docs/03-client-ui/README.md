# Client UI / World Map

Canonical owner for LazyBuilder's in-game Fabric presentation. Network ownership remains in [`../04-system/networking.md`](../04-system/networking.md); the detailed World Manager contract is in [`world-manager-flow.md`](world-manager-flow.md).

## Product direction

The in-game experience is map-first:

```text
M
→ World Map
   ├── pan / zoom / coordinates
   ├── player position
   ├── Teleport Here
   ├── Export Area
   └── Worlds
       → World Manager
```

World Manager is a builder workspace, not a server administration panel. Runtime loading, operation leases, registry identity, conversion workers, transfer sessions, and filesystem paths stay behind the UI.

Xaero World Map 1.21.4 remains the interaction-quality reference for fullscreen map behavior. LazyBuilder follows the familiar mental model without copying Xaero source, assets, icons, branding, or proprietary implementation.

## Native LazyBuilder area-selection language

Area selection is implemented entirely inside LazyBuilder. The UX intentionally follows familiar Minecraft chunk-selection conventions so builders who already know advanced world-conversion tools do not need to learn a new spatial model, but LazyBuilder does not embed, invoke, import, copy, or depend on another application's UI, source, assets, branding, selection component, or runtime for this feature.

Canonical ownership:

```text
LazyBuilder WorldMapScreen
├── chunk grid presentation
├── region boundary presentation
├── selection rectangle
├── move / edge-resize / corner-resize interaction
├── chunk snapping
├── coordinate + size HUD
└── transient selected-area state
```

The selection UI is therefore a first-party LazyBuilder world feature. External conversion tooling remains isolated behind the server conversion adapter and has no ownership of map interaction or area-selection presentation.

The familiar spatial language is:

```text
thin grid       = 16×16 block chunk boundaries
stronger grid   = 32×32 chunk / 512×512 block region boundaries
highlight       = selected export area
inside drag     = move selection
edge drag       = resize one axis
corner drag     = resize two axes
empty-map drag  = pan map
wheel           = zoom
ESC             = cancel selection
ENTER           = continue to export review
```

Chunk grid visibility is zoom-dependent so the map remains readable at wide scales. Selected-area state is transient, bound to the current managed world, and cleared when the world changes or the operation finishes.

## Map behavior

Required behavior:

```text
left mouse drag       continuous pan
mouse wheel           cursor-anchored animated zoom
CTRL + wheel          precise zoom
+ / -                 stepped zoom
middle mouse          recenter on player
right click           contextual actions
ESC                    close context, then selection, then map
hover                  live X/Z coordinates
player marker          directional marker
area selection         editable chunk-aligned rectangle + explicit Continue
```

LazyBuilder-specific map actions are:

```text
Teleport Here
Export Area
Center Map Here
Copy Coordinates
```

Unsupported waypoint/radar/claim/cave-map ecosystems do not appear as dead controls.

`ClientMapSurfaceCache` owns presentation-only explored terrain. It uses sparse 128x128-block regional persistence, bounded resident memory, asynchronous regional I/O, and one canonical coordinate transform. It never force-loads server chunks and never becomes world authority.

## Current Fabric surface

```text
Fabric client
├── LazyBuilderClientUi
├── LbUi / LbButtonWidget
├── WorldMapScreen
├── ClientMapSurfaceCache
├── WorldManagerScreen
├── WorldNavigationPreferences
├── AddWorldScreen
├── CreateWorldScreen
├── WorldTransferScreen
├── WorldTransferPreferences
├── DuplicateWorldScreen
├── WorldSettingsScreen
├── DeleteWorldScreen
├── ConfirmWorldActionScreen
├── ClientWorldController
├── ClientMapController
├── ClientTransferController
├── ClientFileDialogs
├── lazybuilder:world
├── lazybuilder:map
└── lazybuilder:transfer
```

There is no standalone Import screen, standalone Export screen, Clone screen, or manual Load/Unload screen. Import and Export share `WorldTransferScreen`; Duplicate is the only user-facing copy terminology.

## World Manager

World Manager is reached from the map through `Worlds`.

```text
WORLDS
├── Search
├── Pinned
├── Recent
├── All Worlds
├── Archived Worlds
└── + Add World
    ├── Create World
    └── Import World
```

Normal active rows prioritize navigation:

```text
★ Tana Samawa                     [Teleport] [Manage]
```

The current managed world is derived from server-observed player location, not click history. `Recent` records worlds the player was actually observed inside. `Pinned` and `Recent` are user-scoped, server-scoped client preferences and never keep worlds loaded.

Search is one deduplicated result list. Archived worlds stay outside the daily list and are restored explicitly.

### Manage World

```text
Teleport
Import / Export
Duplicate
World Settings
Archive
Delete
```

Current-world Teleport is omitted. Archive is reversible; Delete is permanent and confirms using the visible display name while the server acts on immutable WorldId.

Permission presentation is capability-driven. Teleport-only users receive a navigation-focused UI and do not see management actions they cannot use.

## Import / Export workspace

Import and Export use one final workspace:

```text
IMPORT / EXPORT
[ Export ] [ Import ]
```

Entry behavior:

```text
Manage World → Import / Export → Export tab
Add World → Import World       → Import tab
Map → Export Area              → Export tab + transient selected area
```

There is no intermediate Export menu and no second area-export UI.

### Export

Normal daily use shows the saved user/server-scoped default:

```text
USING DEFAULT SETTINGS
Java Edition · 1.21.4
Entire world

[ Export World ]
Advanced options ▸
```

Advanced options are inline. Target editions/versions come only from the server's verified capability catalog. A temporary override does not change the daily default unless the user explicitly chooses `Use These as Default`.

Selected map rectangles and literal file names are transient and are never saved as defaults. Java exports use `.zip`; Bedrock exports use `.mcworld`; extension selection is system-owned.

### Import

Import is file-first:

```text
[ Choose World File ]
.zip / .mcworld
```

Source edition/version detection and canonical conversion are automatic. The managed target remains Java Edition 1.21.4. Import creates a new managed world and does not silently overwrite an existing one.

## Runtime and lifecycle presentation

Persistent product lifecycle is only:

```text
ACTIVE
ARCHIVED
```

Loaded/unloaded/loading/unloading are not builder-facing lifecycle values. Teleport and settings load worlds when required; empty idle worlds unload automatically. Operations that require a consistent filesystem snapshot are blocked while builders remain inside the target world rather than silently ejecting them.

## Current-world updates

`lazybuilder:map` uses protocol V2. Paper pushes current managed-world changes from actual player world transitions, including changes caused by commands, portals, or other plugins. Entering an unmanaged world explicitly clears client current-world state.

This keeps map identity, `You are here`, and Recent navigation synchronized without world-list polling.

## Permissions and world protocol

`lazybuilder:world` uses protocol V3. The authoritative WorldList response includes presentation capabilities:

```text
canManage
canTeleport
```

The client uses these only to shape UI. Paper still performs final authorization for every request.

## Transfer contract

```text
Client Mod
→ lazybuilder:transfer
→ PaperTransferPayloadAdapter
→ TransferSessionService
→ managed import/export storage
```

Transfer rules:

- bounded chunk protocol and pipeline;
- one active client transfer flow;
- checksum validation;
- client and server partial-file cleanup;
- server upload storage check;
- client export save-location storage check once authoritative size is known;
- disconnect aborts active transfer sessions;
- idle sessions expire opportunistically;
- no custom HTTP/WebSocket/cloud transfer path.

Leaving an Import/Export screen does not imply cancellation. The UI says `Continue in Background` while work is active. Cancellation is not exposed until the full operation supports safe cancellation end-to-end.

## Recovery

Heavy world operations are request-bound and single-flight per player. If a player disconnects after a heavy result becomes ready, the Paper adapter can retain one bounded pending completion and deliver it on a later World Manager request after reconnect. Transfer sessions themselves fail closed and clean up on disconnect.

## Visual system

All first-party screens use `LbUi` / `LbButtonWidget` rather than vanilla gray button chrome.

```text
Primary    everyday main action
Secondary  supporting action
Ghost      navigation / low emphasis
Danger     destructive action
```

Required states include loading, empty, search-empty, permission-limited, operation-running, recoverable error, occupied-world guard, and storage failure. User-facing copy must remain actionable and must not expose converter/runtime/artifact/lease terminology.

## Efficiency

- one fullscreen map owner;
- one World Manager list owner;
- one Import / Export workspace;
- one whole-world/area export service path;
- no world-list or settings polling;
- no client-side shadow world registry;
- no converter idle daemon;
- bounded regional map memory;
- expensive file/conversion work remains request-bound;
- runtime load/unload remains automatic and server-owned.

## Proof boundary

Source review proves ownership, terminology, navigation, and static contracts only. Final proof still requires local Fabric/Paper compilation and a live Java 1.21.4 client/server test covering GUI scales, map behavior, current-world push, permissions, large lists, native dialogs, upload/download, insufficient storage, reconnects, conversion targets, occupied-world safeguards, archive/delete behavior, and automatic idle unloading.
