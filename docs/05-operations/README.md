# Current Operations

This directory owns current continuation/proof only. Durable product and architecture rules live in their domain docs.

## Current State

- `Local` remains the working authority; `main` is untouched unless explicitly promoted;
- World Manager uses one durable registry owner plus separate ephemeral runtime state;
- Create, load/unload, teleport, settings/spawning, Archive/Restore, Clone, Delete, Import, Export, and Export Area are source-implemented;
- file operations remain bounded to managed world/import/export/work roots with traversal/symlink protections where applicable;
- conversion uses one verified current/previous/candidate runtime store and one globally single conversion job;
- conversion workers and release checks are request-bound; there is no idle converter process, update poller, filesystem watcher, transfer daemon, or LazyBuilder relay;
- fresh-install converter bootstrap retries on the next explicit conversion request after transient release/network failure instead of being throttled for 24 hours without a usable runtime;
- Java native-import eligibility comes from bounded `level.dat` `DataVersion` parsing (`4189` for Java 1.21.4); transfer-marker metadata is not trusted as version authority;
- failed conversion/import paths track and clean allocated workspaces before returning failure;
- `BUILD_READY` reset persists its durable entry-mode preference before mutating Paper runtime and attempts metadata rollback if runtime reset fails;
- native whole-world Java 1.21.4 export keeps the direct ZIP fast path; cross-version/cross-edition and Export Area use the verified converter path;
- Export separates safe snapshot capture from conversion/package processing: a previously loaded source is restored immediately after the snapshot and remains available while the owned snapshot is converted/packaged;
- the EXPORT lease remains held until the operation finishes, so early source reload does not permit conflicting World Manager mutations;
- Export Area reuses `WorldExportService`, request-local pruning, existing export artifacts, and the existing transfer channel rather than creating parallel systems;
- map Export tracks active tasks; shutdown stops accepting new map work and releases active Export leases without starting world loads during Paper teardown;
- `TransferSessionService` uses one seekable `FileChannel` per active file and positional reads/writes, removing reopen-and-skip-from-zero behavior for large files;
- upload SHA-256 is accumulated while ordered chunks are written, avoiding a second full read of the completed upload before publication;
- transfer state uses per-session synchronization rather than one global I/O lock, so unrelated clients are not intentionally serialized;
- transfer protocol version 2 keeps 24 KiB data chunks but uses a bounded four-chunk credit window over the existing Minecraft play connection;
- `PaperTransferPayloadAdapter` owns a bounded ordered request lane per player, preserving application order while allowing the client to pipeline a small chunk window;
- protocol failures that identify an active transfer session clean that stale server session;
- transfer sessions carry a configurable idle timeout (default 300s, minimum configured value 30s); stale owner sessions are reclaimed opportunistically on the next owner request without a polling task;
- upload admission checks current usable space on the transfer filesystem before accepting the declared file size, in addition to the configured upload-size ceiling;
- the official Fabric client allows one active file transfer total at a time and mirrors the same four-chunk pipeline with positional local file I/O;
- LazyBuilder client/server application networking is self-owned through `lazybuilder:transfer` and `lazybuilder:map`; it does not require a third-party VPN, SaaS relay, HTTP gateway, WebSocket service, cloud queue, or object store;
- optional Tailscale/ZeroTier/tunnel products are deployment routing only and are outside LazyBuilder protocol ownership;
- the Chunker GitHub release request remains isolated maintenance/bootstrap egress, not client/server data-plane traffic; an already verified local converter remains usable when update-network access is unavailable;
- import extraction and export archive writes use bounded 64 KiB buffering to reduce syscall overhead during large archive operations;
- failed import cleanup walks/deletes incrementally instead of materializing every path into an in-memory list;
- import single-root normalization falls back cleanly when atomic directory moves are unsupported;
- normal world unload relies on Paper's `unloadWorld(..., true)` as the single save boundary instead of forcing an additional explicit synchronous world save first;
- `PaperTransferPayloadAdapter` and `PaperMapActionPayloadAdapter` remain adapters over canonical transfer/map/application owners;
- optional Xaero integration remains presentation/input only and has one version-pinned fullscreen-map accessor;
- Xaero `Teleport Here` and `Export Area` controls plus P/O fallbacks compile against the pinned 1.21.4 client target.

## Audit / Efficiency Hardening Completed

```text
converter bootstrap retry lockout       fixed
forgeable native-import marker trust    fixed via level.dat DataVersion
failed conversion workspace leak        fixed
BUILD_READY persistence ordering        hardened
client transfer ambiguity               one active client transfer
failed server transfer session state    fail-closed cleanup
Export Area shutdown lease handling     hardened
multi-client global transfer lock       removed
per-chunk reopen/skip filesystem cost   replaced by seekable FileChannel
one-RTT-per-chunk transfer flow         replaced by bounded 4-chunk pipeline
upload post-transfer full-file rehash   replaced by incremental SHA-256
long export source-world downtime       reduced to snapshot window
stalled transfer slot/file handle       bounded by idle-session reclamation
oversized disk admission risk           preflighted against usable transfer storage
archive stream syscall overhead         reduced by bounded buffering
large failed-import cleanup list        replaced by streaming tree walk
unload duplicate synchronous save       removed
network semantic dependency on tunnel   explicitly prohibited
```

No new idle/background subsystem was introduced by these changes.

## UX Audit

Canonical user flow now lives in `docs/03-client-ui/world-manager-flow.md`.

The audit found one important product-boundary gap: the server/application capabilities are much more complete than the current Fabric presentation. The existing client source implements file transfer, native dialogs, managed-current-world lookup, and Xaero map actions, but it does **not** yet implement the general World Manager browser/screen promised by the product flow.

The next client slice must therefore be one `lazybuilder:world` control protocol plus one minimal World Manager navigation shell:

```text
Worlds (default)
→ Manage World
→ Create World
→ Import World
→ Map / Xaero
```

Do not create separate channels for Create, Settings, Clone, Delete, or Archive. One world-control adapter must delegate to the existing application services and return canonical server state. `lazybuilder:map` remains spatial intents only; `lazybuilder:transfer` remains file bytes only.

The UX contract also removes duplicate product surfaces: Import owns its upload step, Export owns its download step, Export Area remains contextual to Xaero, advanced settings do not leak into Create World, and destructive actions use one confirmation path.

## Networking Boundary

Canonical networking ownership is `docs/04-system/networking.md`.

The product path is:

```text
Fabric client
→ existing Minecraft connection
→ LazyBuilder versioned payload protocol
→ Paper adapter
→ application/session owners
```

LazyBuilder does not open a second port or require a third-party application relay. If the Minecraft server is not reachable because of CGNAT/firewall topology, deployment still needs a route such as direct port forwarding, IPv6, self-hosted routing, or an optional tunnel. That infrastructure does not change the LazyBuilder protocol.

## Next Action

Before live testing, implement the **minimal World Manager control surface** rather than adding another backend subsystem:

```text
lazybuilder:world protocol
→ list canonical worlds in one snapshot
→ create Flat/Void
→ load/unload
→ teleport to world
→ archive/restore
→ minimal Fabric Worlds screen/navigation
```

After that slice is source/CI green, extend the same channel/screen hierarchy to Settings, Clone, Export, Delete, and Import publication actions. Heavy filesystem work must keep the existing phased async boundaries.

Then move to `LOCAL_CODE` / `LIVE_SERVER` validation with Paper 1.21.4 + Fabric client + pinned Xaero.

Measure transfer throughput/CPU/RAM/disk behavior under LAN and representative remote latency before changing the current 24 KiB × 4 window. Runtime evidence, not speculation, should drive any further window tuning, ZIP-compression tradeoffs, or resumable-transfer support.

## Proof State

`REMOTE_GITHUB` is green through the previous transfer-resilience lineage. UX-flow documents are source-only contracts; they do not prove a World Manager screen because that general control UI is not implemented yet.

This does **not** prove actual running-server behavior, World Manager screen usability, real remote throughput, packet behavior under latency/loss, Xaero mixin/runtime transforms, native OS dialogs, multi-gigabyte filesystem behavior, live Chunker conversion quality, NAT reachability, or Java↔Bedrock fidelity. Those remain `LOCAL_CODE` / `LIVE_SERVER` proof.
