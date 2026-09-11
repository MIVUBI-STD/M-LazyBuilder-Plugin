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
- snapshot copies omit `session.lock`; clone copies additionally omit `uid.dat`, `playerdata`, `advancements`, and `stats`;
- Archive/Restore is now a metadata lifecycle operation: Archive unloads, disables Auto Load, and marks `ARCHIVED` without moving the world folder; Restore marks `ACTIVE`, remains unloaded, and keeps Auto Load OFF;
- archive persistence failure rolls registry metadata back and restores the previous loaded state when possible;
- all Archive operations use the same per-world conflict lease used by future Clone/Delete/Import/Export/Conversion;
- Xaero World Map remains limited to Map Preview, location interaction, and the approved Export Area presentation boundary.

## Next Action

Implement **Clone**, then **Delete**, over the existing file repository and operation coordinator. Clone should stage a sanitized copy, publish only after copy success, register a fresh world identity with Auto Load OFF, and avoid playerdata/advancements/stats. Delete must require an unloaded target, delete only the registry-owned direct world folder, and remove registry metadata only after filesystem deletion succeeds. Heavy copy/delete I/O must stay off the Paper main thread in the eventual command/protocol execution layer.

## Proof State

GitHub Actions `mvn verify` is green through the file-operations foundation. The Archive/Restore lifecycle slice has source and targeted tests but must receive its own green CI result before compile/test success is claimed. Actual Paper generation, live archive player evacuation/unload, filesystem behavior on the live host, Xaero interaction, client file transfer, and Java↔Bedrock conversion still require the appropriate LOCAL_CODE/LIVE_SERVER proof.
