# Current Operations

This directory owns current continuation/proof only. Durable product and architecture rules live in their domain docs.

## Current State

- plugin-stack audit remains open for the broader legacy stack;
- Multiverse is the first confirmed rebuild target;
- native LazyBuilder World Manager source implementation has started;
- World Manager runtime bootstrap is intentionally idle when unused;
- conversion/update policy is source-defined as on-demand, bounded, stable-only, checksum/probe guarded, and rollback-capable;
- World Manager world identity/registry foundation is the current implementation slice;
- Create World V1 remains Flat World + Void World with automatic `BUILD_READY` defaults;
- Xaero World Map remains limited to Map Preview, location interaction, and the approved Export Area presentation boundary.

## Next Action

After the registry slice has relevant source/CI proof, implement the canonical `BUILD_READY` policy owner before Paper world creation. Do not skip directly to UI, file transfer, or converter integration.

## Proof State

Repository/source structure and targeted unit tests can be proven in REMOTE_GITHUB when CI runs. No claim is made yet for actual Paper world lifecycle, live world creation, Xaero interaction, client file transfer, or Java↔Bedrock conversion; those require the appropriate local/live runtime proof.
