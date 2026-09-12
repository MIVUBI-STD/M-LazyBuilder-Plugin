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

## File Transfer Protocol Core

World file transfer is request/event driven and uses bounded chunks. The server protocol core is transport-agnostic so the eventual client-mod/Paper payload adapter does not own transfer state.

```text
Upload
client file picker
→ BEGIN_UPLOAD(fileName, size, sha256)
→ server returns sessionId + chunk size
→ CHUNK(index, bytes) in strict order
→ FINISH_UPLOAD
→ checksum verify
→ atomic publish into world/imports

Download
request export artifact
→ server returns sessionId + size + sha256 + chunk size
→ client explicitly requests/accepts ordered chunks
→ FINISH_DOWNLOAD
→ native save dialog writes local file
```

Current server defaults:

```text
chunk size             24 KiB
upload sessions/client 1
download sessions/client 2
max upload             configurable, default 16 GiB
```

A partial upload lives only under `plugins/LazyBuilder/world/transfer` and is never visible to Import World until size and SHA-256 validation pass. Upload names are limited to `.zip` and `.mcworld`. Existing import artifacts are not overwritten.

Disconnect/cancel handling must call the same session owner to abort the player's active sessions and remove partial upload data. No heartbeat or polling loop is required for transfer correctness.

The next transport adapter must map client packets/custom payloads onto this existing service rather than creating another upload/download registry.

## Efficiency

- request summaries first, details on demand;
- do not duplicate Xaero map caches/data when integration can reuse them safely;
- keep network payloads bounded and action-specific;
- no continuous polling when event/delta-based updates are sufficient;
- no permanent transfer worker or socket loop beyond the normal Minecraft connection;
- large-file hashing and chunk I/O must run from request-appropriate worker/network contexts, not as an idle background task.
