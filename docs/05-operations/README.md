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
- Export World is now phased around a quiescent SNAPSHOT copy and one per-world EXPORT lease;
- native Java 1.21.4 export bypasses the converter and packages directly as ZIP;
- cross-version/cross-edition export lazily checks the converter runtime, continues with the already verified current runtime if an update check fails, validates target support, and invokes one conversion job;
- Bedrock conversion output is packaged as `.mcworld`; Java output is packaged as `.zip`;
- completed export files are bounded to `plugins/LazyBuilder/world/exports`; request work remains under `world/work`;
- whole-world export supplies no pruning configuration; Export Area remains a later Xaero/client input layer over the same export service;
- Xaero World Map remains limited to Map Preview, location interaction, and the approved Export Area presentation boundary.

## Next Action

Implement **Import World** around validated temporary input. Same-format Java 1.21.4 input should publish natively; older Java and Bedrock input should go through the same verified conversion runtime. Register only after validation/publish succeeds, never overwrite an existing world, and default imported worlds to ACTIVE + UNLOADED + Auto Load OFF.

After Import, add the protocol/client transfer layer and Export Area coordinate-to-pruning bridge. Do not expose Chunker as a separate product surface and do not add an idle daemon, periodic updater, file watcher, or persistent conversion worker.

## Proof State

GitHub Actions was green through the concrete converter adapter/update source. The Export World source slice has targeted unit coverage in the current change and requires its own green CI result before compile/test proof is promoted. Real runtime download, live CLI conversion, large-world snapshot/package behavior, client transfer, `.mcworld` opening, and live Paper behavior remain LOCAL_CODE/LIVE_SERVER proof.
