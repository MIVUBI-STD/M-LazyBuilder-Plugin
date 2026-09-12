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
- Import World is source-implemented as one request-bound import slot with bounded `.zip`/`.mcworld` extraction under owned workspace paths;
- import rejects traversal, excessive entry counts, and excessive uncompressed size, requires `level.dat`, detects Java vs Bedrock layout, strips `session.lock`/`uid.dat`, and never overwrites an existing managed world;
- trusted LazyBuilder Java 1.21.4 exports use the native import path; unknown external Java and Bedrock input are conservatively normalized through the verified converter runtime to `JAVA_1_21_4`;
- imported worlds receive fresh identity and publish as `IMPORTED + ACTIVE + UNLOADED + Auto Load OFF`; `BUILD_READY` is not applied automatically;
- client/server file transfer now has one transport-agnostic `TransferSessionService` with strict ordered chunks, per-client concurrency limits, declared-size enforcement, SHA-256 verification, and atomic upload publication into `world/imports`;
- downloads are explicit request/response sessions over existing artifacts in `world/exports`; the server computes immutable session metadata and sends no data until the client requests the next ordered chunk;
- transfer partials live under `world/transfer`; no transfer daemon, polling loop, background socket, or persistent worker exists while idle;
- whole-world export supplies no pruning configuration; Export Area remains a later Xaero/client input layer over the same export service;
- Xaero World Map remains limited to Map Preview, location interaction, and the approved Export Area presentation boundary.

## Next Action

Implement the thin **Minecraft client-mod/Paper payload adapter** over the existing transfer session owner, including disconnect/cancel cleanup and permission gates. Do not create another transfer registry or file state owner.

After transport wiring, add the Xaero Export Area selection bridge and Teleport to Location coordinate flow. Keep all transfer behavior event-driven and identical over LAN/Tailscale/remote connections.

Do not expose Chunker as a separate product surface. Do not add an idle daemon, periodic updater, file watcher, or persistent conversion worker.

## Proof State

GitHub Actions `mvn verify` is green through the Import World source slice. The transfer protocol core has source and targeted tests in the current change and requires its own green CI run before compile/test proof is promoted. Real network payload transport, large-file transfer, disconnect behavior, live CLI conversion, `.mcworld` opening, Xaero integration, and live Paper behavior remain LOCAL_CODE/LIVE_SERVER proof.
