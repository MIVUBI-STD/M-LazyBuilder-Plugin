# Current Operations

This directory owns current continuation/proof only. Durable product and architecture rules live in their domain docs.

## Current State

- plugin-stack audit remains open for the broader legacy stack;
- Multiverse is the first confirmed rebuild target;
- native LazyBuilder World Manager source implementation has started;
- World Manager runtime bootstrap is intentionally idle when unused;
- conversion/update policy is source-defined as on-demand, bounded, stable-only, checksum/probe guarded, and rollback-capable;
- World Manager world identity/registry foundation is source-implemented;
- registry metadata has one bounded YAML persistence path with atomic publication where supported;
- canonical `BUILD_READY` initialization policy is source-implemented and deliberately not a background enforcement loop;
- Flat World and Void World creation share one application path and one Paper runtime adapter;
- runtime `LOADED/UNLOADED/LOADING/UNLOADING` state is a separate ephemeral owner;
- auto-load consumes persisted `ACTIVE + autoLoad` metadata once at startup rather than polling;
- unload moves players to a protected global fallback world before saving/unloading;
- Teleport to World and World Settings use the same runtime authority;
- Spawning remains Paper/vanilla-backed without a custom spawn engine;
- file operations have one request-bound operation coordinator plus one path-safe local repository boundary;
- staged file work lives under `plugins/LazyBuilder/world/work`; there is no background file watcher or idle worker;
- Archive/Restore is implemented as metadata lifecycle without moving world folders;
- Clone is phased: prepare/unload on Paper, sanitized copy/publish on a request worker, then source load-state restoration; clones receive fresh identity and Auto Load OFF;
- Delete is phased and reversible before commit: the owned world folder is moved into an owned workspace, durable registry removal is committed, then the workspace is deleted;
- Clone/Delete use the same per-world conflict lease as Archive and future Import/Export/Conversion;
- Clone/Delete source and targeted unit tests pass `mvn verify` remotely;
- Xaero World Map remains limited to Map Preview, location interaction, and the approved Export Area presentation boundary.

## Next Action

Implement the **internal conversion runtime** inside World Manager: converter runtime store, stable-release update metadata, compatibility/checksum gate, on-demand process lifecycle, cancellation/timeout, and one-job-at-a-time ownership. Keep the converter process absent while idle. Do not expose Chunker as a separate user-facing product or menu.

After the runtime boundary is stable, connect Import / Export to the existing file-operation and conversion owners.

## Proof State

GitHub Actions `mvn verify` is green through Clone/Delete source and targeted unit tests. This proves remote compilation and unit behavior only. Actual Paper world generation/lifecycle, multi-gigabyte filesystem behavior, player evacuation, live clone/delete, Xaero interaction, client file transfer, converter execution, and Java↔Bedrock conversion still require the appropriate LOCAL_CODE/LIVE_SERVER proof.
