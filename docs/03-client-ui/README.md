# Client UI / World Map

Canonical owner for LazyBuilder's in-game Fabric presentation. Network ownership lives in `../04-system/networking.md`; detailed World Manager flow lives in `world-manager-flow.md`.

## Product direction

The in-game experience is map-first:

```text
M
→ World Map
   ├── pan / zoom / coordinates
   ├── player position
   ├── Teleport Here
   ├── Export Area
   ├── Copy Review Reference
   └── Worlds
       → World Manager
```

The fullscreen map and area-selection experience are first-party LazyBuilder UI. Xaero may be used only as an interaction-quality reference; LazyBuilder does not import, embed, require, or delegate runtime ownership to Xaero.

## Native area selection

`WorldMapScreen` owns transient area-selection presentation:

```text
chunk grid
region grid
selection rectangle
move/edge/corner resize
chunk snapping
coordinate + size HUD
transient selected-area state
```

Selection is bound to the current managed world and clears when the world changes or the operation finishes. It is presentation/request context, not durable world metadata or a saved export preset.

## Map behavior

```text
left drag        pan
wheel            cursor-anchored zoom
CTRL + wheel     precise zoom
middle click     recenter on player
right click      contextual actions
hover            X/Z coordinates
selection        editable chunk-aligned Export Area
```

The map must not grow waypoint/radar/claim/cave-map ecosystems merely because other map mods support them.

`ClientMapSurfaceCache` is presentation-only explored-terrain storage. It never becomes server authority and never force-loads server chunks.

## Client ownership

```text
Map Manager
→ world/map UI, navigation, transfer UI, current managed-world presentation,
  world settings/lifecycle presentation, Import/Export, Map Export Area,
  Copy Review Reference, shared World-Manager protocol client

Utility Manager
→ passive non-building client convenience

Performance Manager
→ performance/resource observation and background-FPS policy
```

No Fabric Manager imports another Manager's implementation packages.

## World Manager surface

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

Pinned and Recent are client navigation preferences. They do not affect runtime loading.

Manage World:

```text
Teleport
Import / Export
Duplicate
World Settings
Archive
Delete
```

There is no user-facing Load/Unload action, `Load on Server Start`, Clone screen, standalone Import screen, or standalone Export screen.

Persistent product lifecycle is only:

```text
ACTIVE
ARCHIVED
```

Paper loads worlds automatically when required and unloads eligible empty worlds after the configured idle policy.

## Import / Export

Import and Export share one workspace:

```text
IMPORT / EXPORT
[ Export ] [ Import ]
```

Entry behavior:

```text
Manage World → Export tab
Add World    → Import tab
Map selection → Export tab with transient area
```

Whole-world and area export share the same canonical server export path.

Import is file-first. Upload is followed by bounded server inspection/review; final Import remains an explicit separate action and revalidates before publish. Closing/switching/changing source discards the requesting player's abandoned reviewed artifact through the canonical cleanup path.

## Current-world state

`lazybuilder:map` uses **Map Action V2**. Paper pushes the current managed-world state from actual world transitions. Entering an unmanaged world sends an explicit clear so `You are here`, Recent, map identity, and contextual actions cannot retain stale state.

## Permissions and world protocol

`lazybuilder:world` uses **World Control V5**.

The server may provide presentation capabilities such as:

```text
canManage
canTeleport
```

The client uses them only to shape UI. Paper remains final authorization authority for every request.

World Control V5 includes the current Import inspection/review/discard contract and excludes manual Load/Unload and autoLoad/runtimeState product machinery.

## Transfer

File bytes use only `lazybuilder:transfer`:

```text
ClientTransferController
→ bounded transfer protocol
→ PaperTransferPayloadAdapter
→ TransferSessionService
→ owned import/export storage
```

Required properties:

- bounded chunk/session behavior;
- ordered transfer;
- SHA-256 validation;
- storage preflight;
- partial-file cleanup;
- disconnect cleanup;
- no custom HTTP/WebSocket/cloud transfer path;
- no second Import/Export byte-transfer implementation.

## Failure presentation

User-facing failures should explain:

```text
what failed
why it failed
what the builder can safely do next
```

Do not expose converter manifests, leases, raw format ids, worker terminology, or internal filesystem paths for normal failures.

## Proof boundary

Remote source/build proof does not prove real Minecraft interaction. Local/live validation must cover GUI scales, map input, current-world push/clear, permissions, area selection, Import review cleanup, native dialogs, realistic transfers/storage pressure, reconnect behavior, protocol interoperability, and automatic idle unloading.
