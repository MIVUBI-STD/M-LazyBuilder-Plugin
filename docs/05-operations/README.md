# Current Operations

This directory owns current continuation/proof only. Durable product and architecture rules live in their domain docs.

## Current State

- plugin-stack audit remains open for the broader legacy stack;
- Multiverse is the first confirmed rebuild target;
- native LazyBuilder World Manager source implementation has started;
- World Manager runtime bootstrap is intentionally idle when unused;
- conversion/update policy is source-defined as on-demand, bounded, stable-only, checksum/probe guarded, and rollback-capable;
- World Manager world identity/registry foundation is source-implemented;
- registry metadata now has one bounded YAML persistence path with atomic publication where supported;
- canonical `BUILD_READY` initialization policy is source-implemented and deliberately not a background enforcement loop;
- Flat World and Void World creation now share one application path and one Paper runtime adapter;
- Void World uses a minimal all-air generator plus a 5×5 safe spawn platform;
- Create World publishes registry metadata only after runtime creation succeeds and rolls back the new world when publication fails;
- Xaero World Map remains limited to Map Preview, location interaction, and the approved Export Area presentation boundary.

## Next Action

Implement runtime **Load / Unload** states and fallback-player safety without duplicating creation or registry ownership. Auto-load should consume persisted registry metadata; no periodic world polling should be introduced.

## Proof State

Source structure and unit-test intent exist in REMOTE_GITHUB. The current GitHub connection has not yet surfaced a green compile/test result for this branch, so compile success is not claimed. Actual Paper generation, gamerule application, rollback, persistence behavior, world lifecycle, Xaero interaction, client file transfer, and Java↔Bedrock conversion still require the appropriate LOCAL_CODE/LIVE_SERVER proof.
