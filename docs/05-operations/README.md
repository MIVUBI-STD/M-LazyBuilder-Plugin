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
- Export now separates safe snapshot capture from conversion/package processing: a previously loaded source is restored immediately after the snapshot and remains available while the owned snapshot is converted/packaged;
- the EXPORT lease remains held until the operation finishes, so early source reload does not permit conflicting World Manager mutations;
- Export Area reuses `WorldExportService`, request-local pruning, existing export artifacts, and the existing transfer channel rather than creating parallel systems;
- map Export tracks active tasks; shutdown stops accepting new map work and releases active Export leases without starting world loads during Paper teardown;
- `TransferSessionService` uses one seekable `FileChannel` per active file and positional reads/writes, removing reopen-and-skip-from-zero behavior for large files;
- transfer state uses per-session synchronization rather than one global I/O lock, so unrelated clients are not intentionally serialized;
- transfer protocol version 2 keeps 24 KiB data chunks but uses a bounded four-chunk credit window over the existing Minecraft play connection;
- `PaperTransferPayloadAdapter` owns a bounded ordered request lane per player, preserving application order while allowing the client to pipeline a small chunk window;
- protocol failures that identify an active transfer session clean that stale server session;
- the official Fabric client allows one active file transfer total at a time and mirrors the same four-chunk pipeline with positional local file I/O;
- LazyBuilder client/server application networking is self-owned through `lazybuilder:transfer` and `lazybuilder:map`; it does not require a third-party VPN, SaaS relay, HTTP gateway, WebSocket service, cloud queue, or object store;
- optional Tailscale/ZeroTier/tunnel products are deployment routing only and are outside LazyBuilder protocol ownership;
- the Chunker GitHub release request remains isolated maintenance/bootstrap egress, not client/server data-plane traffic; an already verified local converter remains usable when update-network access is unavailable;
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
long export source-world downtime       reduced to snapshot window
network semantic dependency on tunnel   explicitly prohibited
```

No new idle/background subsystem was introduced by these changes.

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

Do not build a custom NAT traversal/relay stack into World Manager without a future explicit requirement and evidence; it would add a second network plane, persistent infrastructure, and avoidable attack surface.

## Next Action

Do not add another backend subsystem before runtime evidence exists. The meaningful remaining boundary is `LOCAL_CODE` / `LIVE_SERVER` validation with Paper 1.21.4 + Fabric client + pinned Xaero.

The first live pass should verify plugin enable/disable, real world creation/load/unload, fallback/player evacuation behavior, settings persistence, safe-surface teleport, Xaero fullscreen control placement and coordinate transform, two-corner Export Area, immediate post-snapshot world restoration, native file dialogs, large pipelined upload/download, disconnect/error recovery, converter bootstrap/update, Java↔Bedrock conversion, and `.mcworld` opening.

Measure transfer throughput/CPU/RAM/disk behavior under LAN and representative remote latency before changing the current 24 KiB × 4 window. Runtime evidence, not speculation, should drive any further window tuning.

## Proof State

`REMOTE_GITHUB` is green for the efficient-flow source lineage: Paper Maven verification/tests and Fabric Gradle compilation pass with protocol v2, seekable transfer channels, bounded per-player request lanes, client four-chunk pipelining, and split Export snapshot/processing phases.

This does **not** prove actual running-server behavior, real remote throughput, packet behavior under latency/loss, Xaero mixin/runtime transforms, native OS dialogs, multi-gigabyte filesystem behavior, live Chunker conversion quality, NAT reachability, or Java↔Bedrock fidelity. Those remain `LOCAL_CODE` / `LIVE_SERVER` proof.
