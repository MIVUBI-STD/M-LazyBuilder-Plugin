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
- `WorldAreaSelection` converts map block-corner selections into inclusive chunk bounds with correct negative-coordinate floor division;
- `WorldExportService.prepareArea(...)` reuses the canonical Export path and emits request-local include pruning for overworld, Nether, and End rather than creating a second export system;
- Export Area always uses the verified conversion runtime, including Java 1.21.4 targets, because pruning must be applied; whole-world Java 1.21.4 retains its native fast path;
- `WorldLocationTeleportService` owns server-authoritative Teleport to Location: load target world on demand, then resolve safe X/Z destination through a narrow Paper location gateway;
- the Paper location resolver checks world border, safe solid floor, passable feet/head space, and applies the managed world's default game mode; Void worlds resolve to their existing spawn/platform instead of generating terrain at the selected coordinate;
- `MapActionWireProtocol` and `PaperMapActionPayloadAdapter` now expose those two map intents over `lazybuilder:map` without creating new world/export authority;
- map protocol version 1 is bounded to 4 KiB; `lazybuilder.world.teleport` gates map teleport and `lazybuilder.world.manage` gates Export Area;
- Export Area sends an immediate accepted response, executes snapshot/conversion off-thread, then returns the finished artifact name for download through the existing transfer channel;
- Xaero remains presentation/input only and does not own world state, Y resolution, map caches, export state, or file transfer state.

## Next Action

The remaining remote-code boundary is the actual **client mod / Xaero hook implementation** that feeds `lazybuilder:map` and `lazybuilder:transfer`. This plugin repository now defines the server protocol and authority contracts; do not invent a second client/server protocol when the client module is created.

Before live rollout, add end-to-end LOCAL/LIVE tests for real client registration, Xaero hook availability, safe-surface teleport, Export Area conversion output, large file transfer, disconnect behavior, and Java↔Bedrock conversion.

Do not create a second terrain renderer, map cache, export service, teleport authority, transfer registry, or background watcher.

## Proof State

GitHub Actions `mvn verify` was green through the Transfer/Paper payload slice and the Export Area + Teleport to Location server-contract slice. The new `lazybuilder:map` wire/Paper adapter has targeted protocol tests in the current change and requires its own green CI result before compile/test proof is promoted. Real Xaero hooks, client-mod registration, safe-surface teleport behavior, real pruning output, network transfer, large-world conversion, `.mcworld` opening, and live Paper behavior remain LOCAL_CODE/LIVE_SERVER proof.
