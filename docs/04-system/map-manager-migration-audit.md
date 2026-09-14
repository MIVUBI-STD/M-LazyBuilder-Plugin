# Map Manager Migration Audit

Status: C1 identity and source-path migration applied on the `Local` branch; exact-HEAD CI verification remains required after structural changes.

## Goal

Maintain one clear client mod: **LazyBuilder Map Manager**, without changing runtime behavior and without creating unnecessary shared/core modules.

Product rule:

```text
1 Manager = 1 Fabric mod = 1 JAR
```

Technical identifiers such as Fabric mod IDs and Gradle artifact names are implementation details of that one mod, not separate plugins.

## Current result

The former generic `client/fabric` project has been identified as Map Manager-owned and its identity has already been migrated to **LazyBuilder Map Manager**.

The canonical source path is now:

```text
client/map-manager/
```

The source-path move is mechanical: Java packages and runtime behavior are intentionally unchanged during this step. CI, version synchronization, and repository verification paths are updated together so there is no second active client source authority.

## Ownership classification

### Map Manager — definite ownership

These classes belong directly to Map Manager because they implement world/map/transfer behavior:

- `AddWorldScreen`
- `ClientFileDialogs`
- `ClientMapController`
- `ClientMapSurfaceCache`
- `ClientTransferController`
- `ClientWorldController`
- `ConfirmWorldActionScreen`
- `CreateWorldScreen`
- `DeleteWorldScreen`
- `DuplicateWorldScreen`
- `WorldManagerScreen`
- `WorldMapScreen`
- `WorldNavigationPreferences`
- `WorldSettingsScreen`
- `WorldTransferPreferences`
- `WorldTransferScreen`
- `net/MapPayload`
- `net/TransferPayload`
- `net/WorldPayload`
- `LazyBuilderClientNetworking`

### Generic-looking classes — remain inside Map Manager

The following names look generic, but their current implementation is not a reusable client framework:

- `LazyBuilderClient`
- `LazyBuilderClientUi`
- `LbUi`
- `LbButtonWidget`

Decision: **keep them in Map Manager during C1**.

Reasoning:

- `LazyBuilderClient` constructs only `ClientWorldController`, `ClientMapController`, and `ClientTransferController` and registers their networking/UI.
- `LazyBuilderClientUi` registers the map entry key and opens `WorldMapScreen`.
- `LbUi` and `LbButtonWidget` are currently visual primitives used by the existing Map Manager screens.
- There is no proven second client manager consuming these classes yet.

Do not create `client-core`, `client-common`, `shared-ui`, or another mandatory runtime dependency during C1.

## Applied identity

User-facing component:

```text
LazyBuilder Map Manager
```

One deployable output:

```text
lazybuilder-map-manager.jar
```

The Fabric mod ID is internal implementation metadata for this same mod and is not a separate component.

## C1 migration boundary

C1 changes identity and organization only. It does not add Utility Manager or Performance Manager features.

Applied/allowed C1 changes:

1. Fabric display identity changed from generic LazyBuilder Client to LazyBuilder Map Manager.
2. Build artifact renamed to `lazybuilder-map-manager`.
3. Fabric mod ID migrated to the Map Manager-specific ID.
4. Gradle root project renamed accordingly.
5. Source authority moved mechanically from `client/fabric/` to `client/map-manager/`.
6. CI/version scripts updated to the canonical Map Manager path.
7. Existing map/world/transfer behavior and protocol remain unchanged.

Not allowed during C1:

- no Utility Manager implementation
- no Performance Manager implementation
- no build/helper tools
- no Axiom duplication
- no generic client framework
- no renderer/performance engine work
- no feature redesign of world/map/transfer behavior

## Package direction

Java package movement is deliberately deferred. The existing package path can remain while C1 structural changes are validated. A future package cleanup should be a dedicated mechanical change and must not be mixed with new functionality.

Possible future ownership-explicit package:

```text
com.halokaryamedia.lazybuilder.mapmanager
```

Do not perform this rename merely for cosmetic consistency before a clean verification baseline exists.

## Compatibility rules

- World-Manager wire/protocol identifiers remain unchanged.
- Vanilla key behavior remains unchanged unless a real conflict is proven.
- There is exactly one active Map Manager source authority: `client/map-manager/`.
- The old `client/fabric/` source path must not remain as a duplicate compatibility copy.

## Verification checkpoint

C1 is considered complete only after the repository `Verify` workflow is green for the exact `Local` HEAD containing the canonical path move.
