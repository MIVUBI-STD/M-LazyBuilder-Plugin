# Networking Ownership

Canonical owner for LazyBuilder client/server networking boundaries.

## Product rule

LazyBuilder owns its application protocol, transfer state, validation, permissions, and recovery. Normal LazyBuilder feature traffic must not depend on a third-party networking service, VPN product, SaaS relay, cloud queue, object store, HTTP API, or WebSocket gateway.

```text
LazyBuilder Fabric Client
        │
        │ existing Minecraft play connection
        │
        ├── lazybuilder:map
        └── lazybuilder:transfer
                    │
                    ▼
           LazyBuilder Paper Plugin
```

V1 does not open a second listening port and does not create another long-lived socket. It reuses the already-established Minecraft connection and adds only versioned, bounded LazyBuilder payloads.

## Authority

The server remains authoritative.

```text
client owns      presentation, selection, local file dialog
protocol owns    typed framing, version, payload bounds
server owns      permissions, sessions, ordering, checksums, world state
filesystem owns  safe staging/publication under LazyBuilder roots
```

A network provider must never become a semantic authority for worlds, transfers, permissions, conversion, or recovery.

## Transport independence

LazyBuilder application behavior is independent from how the Minecraft server becomes reachable.

Direct LAN, normal public-IP hosting, IPv6, router port forwarding, a self-hosted VPN, or an optional third-party tunnel can all carry the same Minecraft connection without changing LazyBuilder's protocol or feature flow.

Products such as Tailscale, ZeroTier, Cloudflare Tunnel, or similar are therefore optional deployment infrastructure only. LazyBuilder does not detect them, call their APIs, store their identity, or require them to function once the client can reach the Minecraft server.

A server behind CGNAT or a firewall with no inbound route still needs some network-level route for a remote player. That cannot be solved purely by the plugin without introducing a relay, NAT-traversal service, VPN, or another externally reachable endpoint. Building such infrastructure into LazyBuilder is outside V1 because it would create a second network stack, more attack surface, persistent background work, and a new operational dependency.

## File transfer

World-file transfer remains inside the Minecraft play connection.

```text
BEGIN
→ bounded session
→ 24 KiB data chunks
→ up to 4 chunks in the active credit window
→ ordered server lane
→ SHA-256 validation
→ atomic publication
→ FINISH / ABORT
```

The transfer protocol uses one seekable file channel per active local/server file. Chunks are read/written by absolute position rather than repeatedly reopening the file and skipping from byte zero. The server serializes application requests per player while allowing unrelated players to progress independently.

The four-chunk window is intentionally bounded. It reduces round-trip latency sensitivity without introducing an unbounded queue, permanent transfer worker, or custom TCP implementation.

## Idle requirements

When no player requests work:

- no LazyBuilder transfer worker;
- no transfer polling;
- no heartbeat loop;
- no relay connection;
- no additional socket listener;
- no file watcher;
- no converter process;
- no periodic network update check.

All active work is request/event driven.

## Security and failure behavior

- all management transfer actions require `lazybuilder.world.manage`;
- map teleport requires `lazybuilder.world.teleport`;
- protocol version mismatch fails closed;
- payload and chunk sizes are bounded;
- transfer sessions are bound to the initiating player UUID;
- filenames cannot escape owned roots;
- uploads publish only after declared size and SHA-256 match;
- downloads are finalized client-side only after SHA-256 validation;
- malformed/out-of-order requests discard affected session state rather than leaving a stale session;
- disconnect and plugin shutdown close active file channels and request-owned state.

Minecraft's established connection supplies the underlying ordered/reliable transport. LazyBuilder does not attempt to replace TCP, encryption at the deployment layer, authentication performed by the Minecraft server, or Internet routing.

## External network egress

The Chunker runtime updater is not part of the LazyBuilder client/server data plane. It is a bounded, user-triggered maintenance/bootstrap dependency:

```text
conversion requested
→ verified current runtime exists?
   ├── yes → current runtime can continue without update-network availability
   └── no  → runtime source is needed before conversion can run
```

The current automatic source uses the official Chunker GitHub release endpoint, with release digest and compatibility verification. Network failure never invalidates an already verified local runtime.

If deployment later requires **zero external egress**, solve that at the converter adapter boundary with local/manual verified runtime provisioning. Do not add a relay, proxy, or alternate LazyBuilder network plane merely to fetch the converter.

## External software boundaries

"Independent networking" does not mean LazyBuilder reimplements every software dependency. Paper/Fabric provide the Minecraft runtime APIs, Xaero is an optional map UI integration, and Chunker is an isolated conversion engine. None of those should own LazyBuilder's network protocol or client/server transfer state.

Third-party version changes must remain absorbed at their adapter boundary whenever the LazyBuilder product contract has not changed.

## Proof boundary

Remote CI can prove protocol codecs, bounds, session ownership, ordered application processing, cleanup semantics, and both Paper/Fabric compilation. Real throughput, packet behavior under latency/loss, disconnect timing, large-file memory/IO characteristics, NAT routing, and Internet reachability require local/live measurement.
