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

## Implemented Fabric Client Slice

```text
Fabric client
├── ClientMapController
├── ClientTransferController
├── ClientFileDialogs
├── lazybuilder:map payload
├── lazybuilder:transfer payload
└── optional Xaero adapter
```

The client reuses `MapActionWireProtocol` and `TransferWireProtocol`; no parallel wire contract exists. After join it resolves the managed current world from the server.

Import opens a native `.zip`/`.mcworld` picker, computes SHA-256 off the render thread, then uploads through the bounded transfer pipeline. Export receives a server artifact name, asks for a native save destination, writes to `.part`, validates the final SHA-256, and only then publishes the local file.

The official client permits **one active file transfer total** at a time: one upload or one download. This intentionally keeps recovery deterministic.

A general World Manager browser/screen and its world-control protocol are **not implemented yet**. Do not treat the existing map/transfer slice as the complete product UI. The next client-facing implementation should add one `lazybuilder:world` control surface that delegates to existing server application services instead of adding more specialized channels or duplicating world logic.

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

## Map Action Contract

```text
Xaero / LazyBuilder client
→ lazybuilder:map
→ PaperMapActionPayloadAdapter
→ application services
```

`MapActionWireProtocol` version 1 is bounded to 4 KiB. Teleport requires `lazybuilder.world.teleport`; Export Area requires `lazybuilder.world.manage`.

### Teleport to Location

Client sends only `WorldId + blockX + blockZ`.

```text
Xaero click
→ server permission gate
→ WorldLocationTeleportService
→ load world if needed
→ Paper safe-surface resolver
→ teleport
```

The server checks world border, solid/non-dangerous floor and passable feet/head space. Void worlds resolve to their existing managed spawn/platform instead of inventing terrain. Client never chooses Y.

### Export Area

Client sends two block-space corners plus normal export target/options. `WorldAreaSelection` normalizes inclusive bounds with correct negative-coordinate floor division.

```text
Xaero rectangle
→ WorldId + x1,z1 + x2,z2 + target
→ WorldAreaSelection
→ WorldExportService.prepareArea(...)
→ safe snapshot
→ request-local include pruning
→ normal Export packaging
→ normal transfer download
```

Export Area is not a second export subsystem. It reuses the canonical Export service, operation lease, converter runtime, artifact store, and transfer path.

The Paper adapter acknowledges accepted work after the main-thread prepare phase, captures the quiescent snapshot asynchronously, restores the source world immediately after snapshot capture, then runs conversion/package work from the owned snapshot. During plugin/server shutdown it stops accepting new map work and releases tracked Export leases without trying to load worlds while Paper is tearing down.

## File Transfer Protocol

```text
Client Mod
→ lazybuilder:transfer
→ PaperTransferPayloadAdapter
→ TransferSessionService
→ world/imports or world/exports
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
- a failed request that identifies an active session cleans that stale server session;
- disconnect aborts that player's sessions;
- idle sessions expire opportunistically without a polling thread;
- plugin disable unregisters the channel and cleans tracked transfer state.

The official LazyBuilder client is stricter than server capacity and runs only one transfer total at a time.

A partial upload lives only under `plugins/LazyBuilder/world/transfer` and is never visible to Import until size and SHA-256 validation pass. Existing import artifacts are not overwritten.

## Efficiency

- no duplicate Xaero map cache or renderer;
- bounded, action-specific network payloads;
- no continuous polling when event-driven state is sufficient;
- no permanent transfer worker or custom socket loop;
- hashing/chunk I/O exists only for explicit requests;
- Teleport and Export Area perform no work until the user acts;
- future World Manager screens must refresh on open/explicit mutation rather than poll server state.

## Proof Boundary

CI proves the Paper source/tests and Fabric/Xaero compile surface. Actual World Manager screen layout, Xaero mixin application, button placement, mouse→world transform, native Windows dialogs, real network transfer, and perceived responsiveness require local/live validation.
