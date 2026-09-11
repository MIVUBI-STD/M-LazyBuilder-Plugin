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
- Xaero World Map remains limited to Map Preview, location interaction, and the approved Export Area presentation boundary.

## Next Action

Implement **World Settings** as the next canonical owner. Keep vanilla/Paper state authoritative for runtime settings, keep LazyBuilder-only preferences durable in the registry/settings metadata, expose all vanilla gamerules from the target API instead of hardcoding a static version list, and preserve `Reset to Build Ready` as an explicit action rather than a background enforcer.

## Proof State

GitHub Actions `mvn verify` is green through the Teleport to World source slice and its targeted unit tests. This proves remote compilation and unit behavior only. Actual Paper generation, gamerule application, player evacuation, live load/unload/teleport, rollback, persistence semantics, Xaero interaction, client file transfer, and Java↔Bedrock conversion still require the appropriate LOCAL_CODE/LIVE_SERVER proof.
