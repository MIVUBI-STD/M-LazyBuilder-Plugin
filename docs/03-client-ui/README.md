# Client UI / Xaero Integration

Canonical owner for LazyBuilder client-side interaction and map integration. Network ownership itself is canonical in [`../04-system/networking.md`](../04-system/networking.md).

## UI Direction

LazyBuilder uses a dedicated Minecraft 1.21.4 Fabric client for modern UI rather than Bukkit inventory GUIs as the primary experience.

```text
open LazyBuilder
→ map/world surface
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
→ source world restored immediately
→ request-local include pruning / conversion / packaging
→ normal transfer download
```

Export Area is not a second export subsystem. It reuses the canonical Export service, operation lease, converter runtime, artifact store, and transfer path. The source world only needs to remain quiescent during safe snapshot capture; conversion and ZIP packaging consume the owned snapshot after the source has been restored.

During plugin/server shutdown the adapter stops accepting new map work and releases tracked Export leases without trying to load worlds while Paper is tearing down.

## File Transfer Protocol

```text
Client Mod
→ existing Minecraft play connection
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
- bounded pipeline window: 4 chunks;
- one ordered application-processing lane per player;
- one seekable `FileChannel` per active local/server file;
- file/hash work off the Paper main thread;
- responses returned on the Paper thread;
- malformed/oversized/out-of-order/unauthorized requests fail closed;
- a failed request that identifies an active session cleans that stale server session;
- disconnect aborts that player's sessions and closes channels;
- plugin disable unregisters the channel and cleans tracked transfer state.

Upload:

```text
BEGIN_UPLOAD(fileName, size, sha256)
→ UPLOAD_ACCEPTED
→ read up to 4 chunks by absolute file position
→ send ordered chunk window
→ cumulative UPLOAD_PROGRESS responses
→ next bounded window
→ FINISH_UPLOAD
→ checksum verify
→ atomic publish into world/imports
```

Download:

```text
BEGIN_DOWNLOAD(fileName)
→ DOWNLOAD_ACCEPTED
→ request up to 4 ordered chunks
→ positional writes into one .part FileChannel
→ next bounded window after current writes finish
→ FINISH_DOWNLOAD
→ local checksum verify
→ final local file
```

The four-chunk credit window reduces round-trip sensitivity without creating an unbounded queue or another transport. `PaperTransferPayloadAdapter` serializes the requests for each player, while independent players can progress independently.

`TransferSessionService` uses per-session synchronization rather than one global monitor. File channels remain open only for the lifetime of an explicit transfer and close on finish, abort, disconnect, or shutdown. There is no permanent transfer worker, maintenance timer, relay, HTTP service, or background socket beyond the normal Minecraft connection.

Current server defaults:

```text
chunk size             24 KiB
pipeline window        4 chunks
upload sessions/client 1
download sessions/client 2
max upload             configurable, default 16 GiB
```

The official LazyBuilder client is stricter than the server capacity and runs only one transfer total at a time.

A partial upload lives only under `plugins/LazyBuilder/world/transfer` and is never visible to Import until size and SHA-256 validation pass. Existing import artifacts are not overwritten.

## Efficiency

- no duplicate Xaero map cache or renderer;
- no file reopen + skip-from-zero loop per chunk;
- bounded four-chunk pipeline instead of one RTT per chunk;
- bounded, action-specific network payloads;
- no continuous polling when event-driven state is sufficient;
- no permanent transfer worker or custom socket loop;
- hashing/chunk I/O exists only for explicit requests;
- Teleport and Export Area perform no work until the user acts.

## Proof Boundary

CI can prove the Paper source/tests and Fabric/Xaero compile surface. Actual Xaero mixin application, button placement, mouse→world transform, native Windows dialogs, real throughput under latency/loss, large-file behavior, and gameplay behavior still require local/live validation.
