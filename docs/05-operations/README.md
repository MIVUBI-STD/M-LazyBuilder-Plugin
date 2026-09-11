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
- Create World publishes registry metadata only after runtime creation succeeds and rolls back the new world when publication fails;
- runtime `LOADED/UNLOADED/LOADING/UNLOADING` state is a separate ephemeral owner;
- auto-load consumes persisted `ACTIVE + autoLoad` metadata once at startup rather than polling;
- unload moves players to a protected global fallback world before saving/unloading;
- Teleport to World is source-implemented over the same runtime service and auto-loads an unloaded target before teleporting to world spawn;
- World Settings core now has one application owner for Auto Load, Default Game Mode, Difficulty, PVP, Time, Weather, Spawn, dynamic gamerules, and explicit Reset to Build Ready;
- runtime settings remain Paper-authoritative while LazyBuilder-only preferences remain durable registry metadata;
- the gamerule catalog is discovered from the active Paper API rather than maintained as a second hardcoded version list;
- Xaero World Map remains limited to Map Preview, location interaction, and the approved Export Area presentation boundary.

## Next Action

Finish the **Spawning** subsection of World Settings using Paper/vanilla spawn controls for Animals, Monsters, Ambient, and Water. Keep this on-demand and do not introduce a custom spawn engine or periodic spawn monitor. After that, continue to file operations.

## Proof State

GitHub Actions `mvn verify` is green through Teleport to World. The current World Settings core change has source and targeted unit-test coverage but must receive its own green CI result before compile/test success is claimed. Actual Paper generation, gamerule/settings mutation, player evacuation, live load/unload/teleport, rollback, persistence semantics, Xaero interaction, client file transfer, and Java↔Bedrock conversion still require the appropriate LOCAL_CODE/LIVE_SERVER proof.
