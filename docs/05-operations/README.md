# Current Operations

This directory owns current continuation/proof only. Durable product and architecture rules live in their domain docs.

## Current State

- plugin-stack audit remains open for the broader legacy stack;
- Multiverse is the first confirmed rebuild target;
- native LazyBuilder World Manager source implementation has started;
- World Manager runtime bootstrap is intentionally idle when unused;
- conversion/update policy is source-defined as on-demand, bounded, stable-only, checksum/probe guarded, and rollback-capable;
- World Manager world identity/registry foundation is source-implemented;
- canonical `BUILD_READY` initialization policy is source-implemented and deliberately not a background enforcement loop;
- Create World V1 remains Flat World + Void World with automatic `BUILD_READY` defaults;
- Xaero World Map remains limited to Map Preview, location interaction, and the approved Export Area presentation boundary.

## Next Action

Implement Flat World and Void World creation through one Paper adapter path that consumes the existing registry and `BuildReadyPolicy`. Do not duplicate policy values inside generators or UI adapters.

## Proof State

Repository/source structure and targeted unit tests can be proven in REMOTE_GITHUB when CI runs. No claim is made yet for actual Paper world creation, world lifecycle, Xaero interaction, client file transfer, or Java↔Bedrock conversion; those require the appropriate local/live runtime proof.
