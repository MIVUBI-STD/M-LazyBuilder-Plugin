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
- spawning controls are Paper/vanilla-backed: natural spawning and patrol/trader/insomnia/warden/raid rules use gamerules, animals/monsters use Paper spawn flags, and ambient/water use `SpawnCategory` tick controls;
- enabling Ambient or Water resets the relevant Paper category interval to its server/Minecraft default (`-1`); disabling uses `0`; LazyBuilder does not persist a second category-spawn state;
- runtime settings remain Paper-authoritative while LazyBuilder-only Auto Load and Default Game Mode preferences remain durable registry metadata;
- the gamerule catalog is discovered from the active Paper API rather than maintained as a second hardcoded version list;
- no World Settings or spawning background polling/monitoring exists;
- Xaero World Map remains limited to Map Preview, location interaction, and the approved Export Area presentation boundary.

## Next Action

Continue with **file operations** needed by World Manager lifecycle and transfer flows. Keep filesystem work request-bound, path-safe, and off the Paper main thread where appropriate. Do not start converter/file watchers in idle runtime.

## Proof State

GitHub Actions `mvn verify` is green through the World Settings Spawning slice and targeted unit tests. This proves remote compilation and unit behavior only. Actual Paper generation, gamerule/settings/spawning mutation, player evacuation, live load/unload/teleport, rollback, persistence semantics, Xaero interaction, client file transfer, and Java↔Bedrock conversion still require the appropriate LOCAL_CODE/LIVE_SERVER proof.
