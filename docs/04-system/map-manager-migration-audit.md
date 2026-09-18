# Map Manager Architecture Audit

Status: active source-of-truth for Map Manager ownership on the `Local` branch.

## Goal

Maintain one clear client mod: **LazyBuilder Map Manager**, without creating parallel client frameworks or duplicate runtime ownership.

Product rule:

```text
1 Manager = 1 Fabric mod = 1 JAR
```

The canonical Map Manager source path is:

```text
mods/map-manager/
```

The canonical shared protocol remains:

```text
shared/protocol/
```

Server authority for managed worlds, export execution, transfer, persistence, and permission checks remains under:

```text
plugins/world-manager/
```

## Current ownership

### Screen / client coordination

`WorldMapScreen` remains the top-level coordinator for the fullscreen map UI. It must not become the permanent owner for unrelated state.

State extracted from the screen is intentionally narrow:

- `MapViewportState` — map camera center, pan, and zoom state.
- `MapAreaSelectionState` — chunk-aligned selection identity, bounds, and drag/resize state.
- `MapRasterPresentationState` — raster presentation snapshot and texture lifecycle.
- `MapExportWorkspaceState` — export workspace configuration only.

Do not replace these with a generic state-management framework.

### Surface cache

`ClientMapSurfaceCache` owns resident explored-map memory, pending sampling, bounded region residency, and async I/O coordination.

Disk-format responsibilities are separate:

- `MapSurfaceRegionStore` — regional cache codec, atomic write, read validation, signed region naming.
- `MapSurfaceLegacyMigrator` — one-way migration from the old whole-map cache format.

The cache must never force-load chunks and never become authoritative for server world state.

### Networking and authority

- `ClientMapController` owns client request correlation and presentation state.
- `MapActionWireProtocol` owns the bounded wire contract.
- `PaperMapActionPayloadAdapter` and World Manager services remain server authority.
- stale request responses must remain rejectable by request ID.
- client-only presentation state must never bypass server permission or managed-world validation.

## Compatibility policy

Compatibility code is allowed only when a real persisted/runtime consumer still exists.

Current rule for legacy surface cache migration:

1. migration is **read-only from the legacy source**;
2. output is written into the current regional format;
3. a migration marker prevents repeated conversion;
4. new code must never produce the legacy format;
5. legacy migration should be removed only after the supported upgrade window is explicitly closed.

Do not add new compatibility overloads without a known consumer. The unused legacy area-export overload was removed after the active export workspace was confirmed to always send explicit export settings.

## Non-goals

Do not introduce:

- `client-core`
- `client-common`
- generic `shared-ui`
- a second map renderer
- a second persistence/cache authority
- a second export workflow
- a generic state framework
- duplicate Utility Manager or Performance Manager features

A new abstraction requires a demonstrated second consumer or a clearly independent responsibility.

## Quality gates

Every structural Map Manager change on `Local` must be proven on the exact resulting HEAD.

Required automated gates:

- `Verify` on direct `Local` pushes;
- Fabric compile/test/package verification;
- Paper compile/test and managed-world runtime proofs where shared/server code is affected;
- `Minecraft UI Preview` on direct `Local` pushes for Map Manager UI/runtime changes.

Visual proof must remain a real Minecraft 1.21.4 client run, not only screenshot-state injection.

## Remaining audit targets

The following remain valid improvement targets:

1. continue reducing `WorldMapScreen` coordination breadth where a clear state owner exists;
2. keep `ClientMapSurfaceCache` focused on resident cache/sampling rather than disk codecs;
3. reduce reflection/private-state injection in visual proof where real interaction can replace it;
4. prove reconnect, world/dimension change, negative-coordinate navigation, large cache pressure, and resource lifecycle behavior with runtime evidence;
5. keep documentation aligned with the canonical `mods/map-manager/` path;
6. remove obsolete development branches only after their work is confirmed reachable from `Local`.

## Current promotion rule

`Local` is the integration source of truth.

A Map Manager structural change is not considered complete until the exact `Local` HEAD has green automated proof. Historical green runs are supporting evidence only.
