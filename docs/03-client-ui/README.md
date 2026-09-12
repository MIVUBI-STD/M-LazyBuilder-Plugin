# Client UI / Xaero Integration

Canonical owner for LazyBuilder client-side interaction and map integration.

## UI Direction

LazyBuilder uses a dedicated client mod for modern UI rather than relying on Bukkit inventory GUIs as the primary experience.

The UI should remain simple:

```text
open LazyBuilder
→ map/world surface
→ choose bounded action
→ close back to normal gameplay
```

Do not reproduce Axiom's full editor UI. Use its clarity only where useful. Do not clone Xaero World Map.

## Xaero Scope

Xaero World Map is retained specifically because its map preview is already mature.

Required integration surface:

```text
Map Preview
Teleport to Location
Export Area selection
```

Features such as minimap, waypoint ecosystems, entity radar, route planning, or a new map renderer are outside LazyBuilder ownership unless a later requirement explicitly changes scope.

## Responsibility Boundary

Client owns:

- screen/layout;
- keybind/open-close behavior;
- world/map selection presentation;
- user input for map-location teleport;
- native file picker/save dialog;
- local presentation preferences.

Server owns:

- permissions;
- world existence/state;
- safe teleport resolution;
- create/load/unload/clone/archive/delete;
- settings persistence and mutation;
- export/import validation;
- transfer session limits, ordering, size validation, and checksum verification.

The client must not become authoritative for server state.

## Xaero Action Contracts

Xaero integration is an input/presentation adapter only. It must never directly mutate world files or resolve teleport height on the client.

Server transport for map actions is now separated from file transfer:

```text
Xaero / LazyBuilder client
→ lazybuilder:map
→ PaperMapActionPayloadAdapter
→ application services
```

`MapActionWireProtocol` is version `1` and bounded to 4 KiB because it carries only action intent and small responses. Teleport requests require `lazybuilder.world.teleport`; Export Area requests require `lazybuilder.world.manage`.

### Teleport to Location

The client sends only the managed `WorldId` plus the selected block-space X/Z coordinate. `WorldLocationTeleportService` loads the world on demand and delegates final resolution to the Paper server.

```text
Xaero click
→ WorldId + blockX + blockZ
→ permission gate
→ WorldLocationTeleportService
→ load world if needed
→ Paper safe-surface resolver
→ teleport
```

For normal/flat/imported terrain the server resolves a safe standing location from X/Z, checks the world border, requires solid non-dangerous floor plus passable feet/head space, and applies the managed world's default game mode after successful teleport. Void worlds intentionally resolve to the managed world spawn/platform rather than generating or inventing terrain at the clicked coordinate.

The client must not send or choose Y for this flow.

### Export Area

The client sends two block-space corners plus the normal Export target/options. `WorldAreaSelection` normalizes the rectangle and converts inclusive block coordinates to inclusive chunk bounds using floor division, including negative coordinates.

```text
Xaero rectangle
→ WorldId + x1,z1 + x2,z2 + export target
→ WorldAreaSelection
→ WorldExportService.prepareArea(...)
→ safe world snapshot
→ internal include-region pruning
→ normal Export packaging/transfer flow
```

Export Area is not a second export system. It uses the same `WorldExportService`, operation lease, converter runtime, artifact store, and client download path as whole-world export.

Whole-world native Java 1.21.4 export keeps its direct ZIP fast path. Any Export Area request uses the verified conversion runtime even when the target remains Java 1.21.4 because pruning must be applied. The generated pruning file is request-local and never shown in the UI.

The internal pruning document applies the selected chunk rectangle to overworld, Nether, and End. This avoids Chunker's missing-config behavior from accidentally retaining an entire secondary dimension while the user asked for an area-only export.

The Paper map adapter returns `EXPORT_ACCEPTED` immediately after the main-thread prepare phase, performs snapshot/conversion work asynchronously, restores the source world through the normal finish phase, then returns `EXPORT_COMPLETE` with the export artifact file name. The client downloads that artifact through the existing transfer channel; no second file-transfer path is introduced.

## File Transfer Protocol

World file transfer is request/event driven and uses bounded chunks. The server protocol core remains separate from the Paper transport adapter so there is only one upload/download session owner.

```text
Client Mod
→ Minecraft custom/plugin payload
→ lazybuilder:transfer
→ PaperTransferPayloadAdapter
→ TransferSessionService
→ world/imports or world/exports
```

The Paper adapter is intentionally thin:

- channel: `lazybuilder:transfer`;
- protocol version: `1`;
- permission gate: `lazybuilder.world.manage`;
- maximum wire payload: 30 KiB;
- maximum file-data chunk: 24 KiB;
- one in-flight protocol request per player;
- file/hash work is dispatched off the Paper main thread;
- server responses are sent back on the Paper thread;
- disconnect aborts all transfer sessions for that player;
- plugin disable unregisters the channel and cleans tracked sessions;
- malformed, oversized, out-of-order, or unauthorized requests fail closed.

Upload uses stop-and-wait semantics:

```text
client file picker
→ BEGIN_UPLOAD(fileName, size, sha256)
→ UPLOAD_ACCEPTED(sessionId, chunk size, total chunks)
→ UPLOAD_CHUNK(index, bytes)
→ UPLOAD_PROGRESS
→ repeat only after progress response
→ FINISH_UPLOAD
→ checksum verify
→ atomic publish into world/imports
```

Download uses the same request/response pattern:

```text
request export artifact
→ BEGIN_DOWNLOAD(fileName)
→ DOWNLOAD_ACCEPTED(sessionId, size, sha256, chunk size)
→ DOWNLOAD_CHUNK(sessionId, index)
→ DOWNLOAD_CHUNK_DATA
→ repeat only after chunk response
→ FINISH_DOWNLOAD
→ client native save dialog writes local file
```

The binary frame is versioned and length-prefixed; UUIDs use two 64-bit values and strings are UTF-8. The client mod must mirror `TransferWireProtocol` rather than inventing a second framing scheme.

Current server defaults:

```text
chunk size             24 KiB
upload sessions/client 1
download sessions/client 2
max upload             configurable, default 16 GiB
```

A partial upload lives only under `plugins/LazyBuilder/world/transfer` and is never visible to Import World until size and SHA-256 validation pass. Upload names are limited to `.zip` and `.mcworld`. Existing import artifacts are not overwritten.

## Efficiency

- request summaries first, details on demand;
- do not duplicate Xaero map caches/data when integration can reuse them safely;
- keep network payloads bounded and action-specific;
- no continuous polling when event/delta-based updates are sufficient;
- no permanent transfer worker or socket loop beyond the normal Minecraft connection;
- large-file hashing and chunk I/O run only in response to transfer requests;
- map teleport and Export Area perform no work until the player explicitly acts.
