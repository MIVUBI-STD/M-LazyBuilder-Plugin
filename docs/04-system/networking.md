# Networking Ownership

Canonical owner for LazyBuilder client/server networking boundaries.

## Product rule

LazyBuilder owns its application protocol, transfer state, validation, permissions, and recovery. Normal in-game feature traffic must not depend on a third-party networking service, SaaS relay, cloud queue, object store, HTTP API, or WebSocket gateway.

```text
LazyBuilder Fabric Client
        │
        │ existing Minecraft play connection
        │
        ├── lazybuilder:world
        ├── lazybuilder:map
        └── lazybuilder:transfer
                    │
                    ▼
           LazyBuilder Paper Plugin
```

The in-game data plane reuses the already-established Minecraft connection. It does not create a second long-lived socket or a second world-management network plane.

## Authority

```text
client owns      presentation, local navigation preferences, selection, native file dialog
protocol owns    typed framing, version, payload bounds, neutral request/result shapes
server owns      permissions, world semantics, sessions, ordering, checksums, runtime truth
filesystem owns  safe staging/publication under LazyBuilder roots
```

A transport/provider must never become semantic authority for worlds, permissions, conversion, lifecycle, or recovery.

## Channel ownership

### `lazybuilder:world`

General managed-world/product intents.

Current shared contract is **World Control V3**.

Includes:

```text
world list + canManage/canTeleport presentation capabilities
create
teleport to managed world
archive / restore
duplicate
delete
world settings
import / export requests
verified export-format catalog
```

Intentionally excludes:

```text
manual LoadWorld / UnloadWorld
SetAutoLoad
runtimeState product metadata
CloneWorld
```

Paper remains final authorization/domain authority even though capability flags are sent so Fabric can avoid showing irrelevant controls.

### `lazybuilder:map`

Spatial map intents plus current managed-world presentation state.

Current shared contract is **Map Action V2**.

Includes:

```text
TeleportLocation
ExportArea
CurrentWorldRequest
CurrentWorldResult
CurrentWorldCleared
```

Paper may push current-world changes from actual `PlayerChangedWorldEvent` transitions. Entering an unmanaged world explicitly clears prior managed-world state on the client.

Map payloads do not own general world settings/lifecycle or file bytes.

### `lazybuilder:transfer`

File bytes only.

Import/export UI and world semantics do not create a second transfer implementation.

## Transport independence

LazyBuilder application behavior is independent from how the Minecraft server becomes reachable.

LAN, public-IP hosting, IPv6, router forwarding, self-hosted VPN, or an optional third-party tunnel can carry the same Minecraft connection without changing LazyBuilder's application protocol.

Tailscale, ZeroTier, Cloudflare Tunnel, or similar products are optional deployment infrastructure only. LazyBuilder does not make them world/domain authorities.

A server behind CGNAT/firewall still requires some network-level route. Solving Internet routing inside the plugin would require a relay/NAT-traversal/VPN layer and is outside this protocol architecture.

## File transfer

World-file transfer stays inside the Minecraft play connection.

```text
BEGIN
→ bounded session
→ bounded data chunks
→ bounded active credit window
→ ordered server lane
→ SHA-256 validation
→ atomic publication/finalization
→ FINISH / ABORT
```

The implementation uses one seekable file channel per active local/server file. Chunk I/O uses absolute positions rather than reopening and skipping from byte zero.

The bounded window reduces round-trip sensitivity without introducing an unbounded queue or permanent worker.

## Transfer resilience

The Minecraft connection supplies ordered/reliable transport. LazyBuilder handles failures at the application/session boundary:

```text
malformed / out-of-order request
→ fail closed
→ clean affected session

player disconnect
→ abort active transfer sessions for that player
→ close file channels
→ remove partial transfer files

inactive session
→ reclaim opportunistically after configured idle timeout
→ no polling cleanup daemon
```

There is intentionally no cross-connection byte-transfer resume token. If the connection drops during an active byte transfer, that transfer restarts rather than maintaining a second resumable transport/security layer.

Heavy world-operation **completion** is separate from byte-transfer resume: the world-control adapter may retain one bounded pending completion per player so an already-finished Duplicate/Delete/Import/Export result can be surfaced after reconnect. This does not preserve partial transfer bytes.

## Storage preflight

Upload/import:

```text
declared upload size
→ validate configured maximum
→ verify server transfer filesystem usable space
→ begin session
```

Download/export:

```text
server returns authoritative TransferDescriptor size
→ client checks selected save-location usable space
→ begin local file creation/download
```

Insufficient storage fails early with actionable user-facing feedback. Partial files are cleaned when the operation fails.

## Idle requirements

When no request is active:

- no transfer worker loop;
- no transfer polling;
- no heartbeat loop;
- no relay connection;
- no extra socket listener;
- no file watcher;
- no converter process;
- no periodic client/server capability polling.

World idle-unload is a separate Paper runtime-maintenance concern, not network polling.

## Security and failure behavior

- management mutations require `lazybuilder.world.manage`;
- map/world teleport requires `lazybuilder.world.teleport` where applicable;
- protocol version mismatch fails closed;
- payload/chunk sizes are bounded;
- transfer sessions bind to initiating player UUID;
- filenames cannot escape owned roots;
- uploads publish only after declared size and SHA-256 match;
- downloads finalize locally only after SHA-256 validation;
- malformed/out-of-order requests clean affected state;
- disconnect/plugin shutdown closes active transfer channels and request-owned state;
- server never trusts Fabric permission presentation as authorization.

## Conversion updater egress

The conversion-runtime updater is not part of the Minecraft client/server data plane.

```text
conversion requested
→ verified current runtime exists?
   ├── yes → use it even if update network is unavailable
   └── no  → verified runtime acquisition is required before conversion
```

The current runtime source uses the approved Chunker release source with verification. A network failure must never invalidate an already verified installed runtime.

If a deployment requires zero external egress, solve that at the converter adapter/runtime provisioning boundary. Do not add a new LazyBuilder relay/proxy/network plane.

## External software boundaries

Paper and Fabric provide runtime APIs. Xaero may be used only as an interaction-quality reference for the fullscreen map; it is not required as LazyBuilder's network owner or runtime dependency. Chunker remains an isolated conversion implementation detail.

Third-party changes should be absorbed at adapter boundaries whenever the LazyBuilder product contract has not changed.

## Proof boundary

Source/static review can prove codecs, bounds, ownership, cleanup paths, and version alignment. Fresh local/live proof is still required for current `Local`:

```text
World Control V3 Paper/Fabric interoperability
Map Action V2 current-world push
permission behavior
large upload/download throughput
native save/picker flow
insufficient client/server storage
disconnect timing
pending heavy completion after reconnect
checksum/failure cleanup
multi-gigabyte filesystem behavior
```

Do not treat the older historical CI run as proof of these current protocol revisions.
