# Client UI / Xaero Integration

Canonical owner for LazyBuilder client-side interaction and map integration. Network ownership itself is canonical in [`../04-system/networking.md`](../04-system/networking.md). World Manager navigation and operation flow is canonical in [`world-manager-flow.md`](world-manager-flow.md).

## UI Direction

LazyBuilder uses a dedicated Minecraft 1.21.4 Fabric client for modern UI rather than Bukkit inventory GUIs as the primary experience.

```text
open LazyBuilder
→ World Manager
→ choose bounded action
→ close back to normal gameplay
```

Do not reproduce Axiom's full editor UI and do not clone Xaero World Map. Reuse Xaero only for mature map presentation/input.

## Responsibility Boundary

Client owns screen/layout, keybind/open-close behavior, world/map selection presentation, map-location input, native file picker/save dialog, and local presentation preferences.

Server owns permissions, managed-world existence/state, safe teleport resolution, lifecycle operations, settings persistence/mutation, import/export validation, transfer ordering/limits/checksums, and final filesystem publication.

The client is never authoritative for server state.

## Implemented Fabric Client Surface

```text
Fabric client
├── WorldManagerScreen
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
├── lazybuilder:transfer payload
└── optional Xaero adapter
```

The first-party World Manager surface is connected for canonical world list/refresh, Create, Teleport, Load/Unload, Archive/Restore, Clone, Settings, permanent Delete, Import publication, and native Java 1.21.4 whole-world Export. The client does not maintain a second world registry and does not own world files.

Import opens a native `.zip`/`.mcworld` picker, computes SHA-256 off the render thread, uploads through the bounded transfer pipeline, then asks the canonical world-control path to publish the validated import. Export reuses the existing transfer download and native save dialog rather than creating a second download subsystem.

The official client permits **one active file transfer total** at a time: one upload or one download. This intentionally keeps recovery deterministic.

## Xaero Scope

Required integration surface:

```text
Map Preview
Teleport to Location
Export Area selection
```

Minimap features, waypoint ecosystems, entity radar, route planning, and a second terrain renderer remain outside LazyBuilder ownership.

Xaero integration is optional at runtime. One version-pinned mixin accessor reads fullscreen-map `cameraX`, `cameraZ`, and `scale`; missing Xaero must not disable non-map LazyBuilder functionality.

The fullscreen map adds:

```text
Teleport Here
→ arm selection
→ click map location
→ server resolves safe Y and teleports

Export Area
→ arm selection
→ click corner 1
→ click corner 2
→ server exports selected area
→ existing transfer path downloads result
```

`P` and `O` remain fallback shortcuts. LazyBuilder consumes map clicks only while one of its actions is armed.

## Channel Ownership

```text
lazybuilder:world     canonical world list/create/manage/settings intents
lazybuilder:map       spatial map intents only
lazybuilder:transfer  file bytes only
```

`lazybuilder:world` delegates to existing World-Manager services and must not implement duplicate lifecycle, settings, locking, conversion, or filesystem logic.

`lazybuilder:map` remains spatial only. `Export Area` reuses the canonical Export service, operation lease, converter runtime, artifact store, and transfer path.

`lazybuilder:transfer` remains file transport only. It does not learn Import/Export business semantics.

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

- no duplicate Xaero map cache or renderer;
- no background world-list or settings polling;
- no client-side shadow registry;
- no permanent transfer worker or custom socket loop;
- hashing/chunk I/O exists only for explicit requests;
- list/settings refresh on screen open, explicit refresh, or relevant mutation;
- one user action maps to one canonical request path;
- expensive file/conversion work remains server-side and request-bound.

## Proof Boundary

Remote CI proves Paper source/tests plus Fabric protocol/screen compilation. It does **not** prove runtime screen behavior, button placement, Xaero mixin application, mouse→world transforms, native Windows dialogs, real file transfer, converter quality, or perceived responsiveness. Those require `LOCAL_CODE` / `LIVE_SERVER` validation.
