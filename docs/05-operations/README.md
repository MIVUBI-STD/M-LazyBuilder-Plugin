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
- `MapActionWireProtocol` and `PaperMapActionPayloadAdapter` expose map intents over `lazybuilder:map` without creating new world/export authority;
- the repository now contains a Java 21 / Minecraft 1.21.4 Fabric client module that compiles in CI alongside the Paper plugin;
- client networking mirrors the two server protocols instead of creating a second wire contract, and resolves the managed current world from the server after join;
- `ClientTransferController` implements native import-file selection, SHA-256 preparation, stop-and-wait upload, export save destination, `.part` download publication, and final checksum validation;
- the optional Xaero adapter is isolated behind one version-pinned accessor for fullscreen-map `cameraX`, `cameraZ`, and `scale`; non-map client functionality does not require Xaero at runtime;
- the Xaero fullscreen map receives `Teleport Here` and `Export Area` controls; selection clicks send only X/Z intent and two-corner intent to server authority, while P/O remain fallback shortcuts;
- all remote source slices through the current Fabric/Xaero control implementation have passed Paper Maven verification and Fabric Gradle compilation.

## Next Action

The meaningful remaining boundary is **LOCAL/LIVE client-server validation**, not another backend subsystem. Validate the built Paper plugin and Fabric client together on Minecraft 1.21.4 with the pinned Xaero World Map version.

The first live pass should verify client registration, current-world handshake, Xaero fullscreen control placement, map-coordinate transform, safe-surface teleport, two-corner Export Area, converter output, native save/open dialogs, upload/download checksums, disconnect cleanup, and Java↔Bedrock conversion on representative worlds.

Only runtime evidence should drive further Xaero UI positioning or compatibility changes. Do not add a second terrain renderer, map cache, export service, teleport authority, transfer registry, background watcher, or speculative compatibility layer.

## Proof State

`REMOTE_GITHUB` proof is green through both the Paper plugin and Fabric client compile path. The Paper suite reports 70 tests passing, and the Fabric module compiles with the current version-pinned Xaero adapter and screen controls.

This does **not** prove actual in-game button placement, map coordinate correctness on a running Xaero client, native OS dialog behavior, real network transfer of large worlds, live Chunker conversion, `.mcworld` opening, or Paper gameplay/runtime behavior. Those remain `LOCAL_CODE` / `LIVE_SERVER` work.
