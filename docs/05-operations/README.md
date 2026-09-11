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
- Void World uses a minimal all-air generator plus a 5×5 safe spawn platform;
- runtime `LOADED/UNLOADED/LOADING/UNLOADING` state is a separate ephemeral owner;
- auto-load consumes persisted `ACTIVE + autoLoad` metadata once at startup rather than polling;
- unload moves players to a protected global fallback world before saving/unloading;
- Teleport to World uses the same runtime service and auto-loads an unloaded target before teleporting to world spawn;
- World Settings has one application owner for General, dynamic gamerules, Environment, Spawning, and explicit Reset to Build Ready;
- runtime settings remain Paper-authoritative while LazyBuilder-only Auto Load and Default Game Mode preferences remain durable registry metadata;
- file operations now have one request-bound operation coordinator plus one path-safe local repository boundary;
- staged file work lives under `plugins/LazyBuilder/world/work`; there is no background file watcher or idle worker;
- snapshot copies omit `session.lock`; clone copies additionally omit `uid.dat`, `playerdata`, `advancements`, and `stats`;
- filesystem ownership checks require world/work directories to stay directly under their configured roots and reject unsafe symbolic-link staging;
- Xaero World Map remains limited to Map Preview, location interaction, and the approved Export Area presentation boundary.

## Next Action

Implement **Manage World lifecycle operations** over the existing registry/runtime/file foundations: Archive/Restore first, then Clone and Delete. Use `WorldOperationCoordinator` for conflict exclusion. Heavy filesystem copy/delete work must be dispatched away from the Paper main thread while Paper load/unload/player movement remains on the primary thread. Do not start background queues or watchers.

## Proof State

GitHub Actions `mvn verify` is green through World Settings Spawning. The file-operations foundation has source and targeted tests but must receive its own green CI result before compile/test success is claimed. Actual Paper generation, gamerule/settings/spawning mutation, filesystem behavior on the live host, player evacuation, live load/unload/teleport, rollback, persistence semantics, Xaero interaction, client file transfer, and Java↔Bedrock conversion still require the appropriate LOCAL_CODE/LIVE_SERVER proof.
