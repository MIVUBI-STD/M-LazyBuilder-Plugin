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
- native whole-world Java 1.21.4 export bypasses the converter, packages directly as ZIP, and adds an internal transfer marker only to the snapshot artifact;
- cross-version/cross-edition export lazily checks the converter runtime, validates target support, and invokes one conversion job;
- Bedrock conversion output is packaged as `.mcworld`; Java output is packaged as `.zip`;
- completed export files are bounded to `plugins/LazyBuilder/world/exports`; request work remains under `world/work`;
- Import World is source-implemented with bounded `.zip`/`.mcworld` extraction under owned workspace paths and fresh registry identity;
- trusted LazyBuilder Java 1.21.4 exports use native import; unknown external Java and Bedrock input normalize through the verified converter runtime to `JAVA_1_21_4`;
- imported worlds publish as `IMPORTED + ACTIVE + UNLOADED + Auto Load OFF`; `BUILD_READY` is not applied automatically;
- client/server file transfer uses one `TransferSessionService` with ordered chunks, per-client limits, declared-size enforcement, SHA-256 verification, and atomic upload publication;
- `PaperTransferPayloadAdapter` maps Minecraft plugin/custom payload traffic on `lazybuilder:transfer` directly onto that transfer owner;
- transfer payloads are protocol-versioned and bounded to 30 KiB with a 24 KiB maximum data chunk;
- `lazybuilder.world.manage` gates upload/download payload use;
- exactly one protocol request per player is processed at a time, file/hash work runs asynchronously, and server responses return on the Paper thread;
- disconnect aborts that player's active sessions and plugin disable unregisters the channel and cleans tracked session state;
- transfer partials live under `world/transfer`; no transfer daemon, polling loop, background socket, or persistent worker exists while idle;
- `WorldAreaSelection` now converts Xaero/client block-corner selections into inclusive chunk bounds with correct negative-coordinate floor division;
- `WorldExportService.prepareArea(...)` reuses the canonical Export path and emits request-local include pruning for overworld, Nether, and End rather than creating a second export system;
- Export Area always uses the verified conversion runtime, including Java 1.21.4 targets, because pruning must be applied; whole-world Java 1.21.4 retains its native fast path;
- `WorldLocationTeleportService` now owns server-authoritative Teleport to Location: load target world on demand, then resolve safe X/Z destination through a narrow Paper location gateway;
- the Paper location resolver checks world border, safe solid floor, passable feet/head space, and applies the managed world's default game mode; Void worlds resolve to their existing spawn/platform instead of generating terrain at the selected coordinate;
- Xaero remains presentation/input only and does not own world state, Y resolution, map caches, or export state.

## Next Action

Implement the **client-mod/Xaero adapter** that turns actual Xaero map clicks and rectangle selections into these existing server intents. Keep the adapter thin: `WorldId + X/Z` for Teleport to Location and `WorldId + two X/Z corners + export options` for Export Area.

After the client adapter, audit protocol permissions and add end-to-end LOCAL/LIVE tests for real Xaero hooks, teleport safety, area conversion output, large file transfer, and Java↔Bedrock conversion.

Do not create a second terrain renderer, map cache, export service, teleport authority, transfer registry, or background watcher.

## Proof State

GitHub Actions `mvn verify` was green through the transfer wire protocol/Paper payload adapter source slice. Export Area and Teleport to Location server contracts have source plus targeted unit coverage in the current change and require their own green CI run before compile/test proof is promoted. Real safe-surface teleport behavior, Xaero hooks, real pruning output, live client-mod registration, network transfer, large-world conversion, `.mcworld` opening, and Paper runtime behavior remain LOCAL_CODE/LIVE_SERVER proof.
