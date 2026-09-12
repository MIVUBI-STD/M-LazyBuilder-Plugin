# Current Operations

This directory owns current continuation/proof only. Durable product and architecture rules live in their domain docs.

## Current State

- plugin-stack audit remains open for the broader legacy stack;
- Multiverse is the first confirmed rebuild target;
- native LazyBuilder World Manager source implementation has started;
- World Manager runtime bootstrap is intentionally idle when unused;
- World Manager world identity/registry foundation is source-implemented;
- registry metadata has one bounded YAML persistence path with atomic publication where supported;
- canonical `BUILD_READY` initialization policy is source-implemented and deliberately not a background enforcement loop;
- Flat World and Void World creation share one application path and one Paper runtime adapter;
- runtime `LOADED/UNLOADED/LOADING/UNLOADING` state is a separate ephemeral owner;
- Auto Load consumes persisted metadata once at startup rather than polling;
- Teleport, World Settings, and Spawning share the same Paper authority;
- Archive/Restore, Clone, and Delete are source-implemented with rollback-oriented ownership;
- conversion runtime uses a bounded current/previous/candidate store, stable CLI release source, checksum/compatibility gates, and a globally single conversion job;
- child-process execution remains request-bound; no converter process exists while idle;
- Export World is phased around a quiescent SNAPSHOT copy and one per-world EXPORT lease;
- native Java 1.21.4 export bypasses the converter, packages directly as ZIP, and adds an internal transfer marker only to the snapshot artifact;
- cross-version/cross-edition export lazily checks the converter runtime, validates target support, and invokes one conversion job;
- Bedrock conversion output is packaged as `.mcworld`; Java output is packaged as `.zip`;
- completed export files are bounded to `plugins/LazyBuilder/world/exports`; request work remains under `world/work`;
- Import World is source-implemented with bounded `.zip`/`.mcworld` extraction under owned workspace paths and fresh registry identity;
- trusted LazyBuilder Java 1.21.4 exports use native import; unknown external Java and Bedrock input normalize through the verified converter runtime to `JAVA_1_21_4`;
- imported worlds publish as `IMPORTED + ACTIVE + UNLOADED + Auto Load OFF`; `BUILD_READY` is not applied automatically;
- client/server file transfer uses one `TransferSessionService` with ordered chunks, per-client limits, declared-size enforcement, SHA-256 verification, and atomic upload publication;
- `PaperTransferPayloadAdapter` now maps Minecraft plugin/custom payload traffic on `lazybuilder:transfer` directly onto that transfer owner;
- transfer payloads are protocol-versioned and bounded to 30 KiB with a 24 KiB maximum data chunk;
- `lazybuilder.world.manage` gates upload/download payload use;
- exactly one protocol request per player is processed at a time, file/hash work runs asynchronously, and server responses return on the Paper thread;
- disconnect aborts that player's active sessions and plugin disable unregisters the channel and cleans tracked session state;
- transfer partials live under `world/transfer`; no transfer daemon, polling loop, background socket, or persistent worker exists while idle;
- whole-world export supplies no pruning configuration; Export Area remains a Xaero/client input layer over the same export service;
- Xaero World Map remains limited to Map Preview, location interaction, and the approved Export Area presentation boundary.

## Next Action

Implement the **Xaero Export Area selection bridge** and **Teleport to Location** coordinate flow. The client should supply only validated world/coordinate or two-corner selection intent; the server remains authoritative for permissions, world state, safe-Y resolution, and conversion of block-space selection into export chunk bounds.

Do not create a second terrain renderer, map cache, export service, or teleport authority. Reuse the existing World Export and Teleport services, and keep Xaero-specific integration isolated behind one client adapter boundary.

## Proof State

GitHub Actions `mvn verify` is green through the transfer wire protocol/Paper payload adapter source slice. This proves remote compile and unit behavior only. Real client-mod registration, network payload transport, large-file transfer, disconnect behavior, live CLI conversion, `.mcworld` opening, Xaero hooks, and live Paper behavior remain LOCAL_CODE/LIVE_SERVER proof.
