# Map Manager Migration Audit

Status: C1 source-ownership audit for the `Local` branch.

## Goal

Convert the current generic `client/fabric` identity into one clear client mod: **LazyBuilder Map Manager**, without changing runtime behavior and without creating unnecessary shared/core modules.

Product rule:

```text
1 Manager = 1 Fabric mod = 1 JAR
```

Technical identifiers such as Fabric mod IDs and Gradle artifact names are implementation details of that one mod, not separate plugins.

## Current finding

The current `client/fabric` source is overwhelmingly Map Manager-owned. Its entrypoint constructs only world, map, and transfer controllers; its UI entrypoint opens the world map; and its networking is the World-Manager/map/transfer bridge.

Therefore the migration should be a controlled **identity/package cleanup**, not a functional split of the existing mod.

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

### Generic-looking classes — keep inside Map Manager

The following names look generic, but their current implementation is not a reusable client framework:

- `LazyBuilderClient`
- `LazyBuilderClientUi`
- `LbUi`
- `LbButtonWidget`

Decision: **keep them in Map Manager for C1**.

Reasoning:

- `LazyBuilderClient` constructs only `ClientWorldController`, `ClientMapController`, and `ClientTransferController` and registers their networking/UI.
- `LazyBuilderClientUi` registers the `M` key to open `WorldMapScreen` and primes World-Manager capabilities.
- `LbUi` and `LbButtonWidget` are currently only the visual primitives used by the existing Fabric map/world screens.
- There is no proven second client manager consuming these classes yet.

Do not create `client-core`, `client-common`, `shared-ui`, or another mandatory runtime dependency during C1.

If Utility Manager or Performance Manager later needs a genuinely shared UI contract, extract the smallest stable contract only after a real second consumer exists.

## C1 migration boundary

C1 should change identity and organization only. It must not add Utility Manager or Performance Manager features.

Allowed C1 changes:

1. Rename the Fabric mod display identity from generic `LazyBuilder Client` to `LazyBuilder Map Manager`.
2. Rename the build artifact to `lazybuilder-map-manager.jar`.
3. Rename the Fabric mod ID to the Map Manager-specific ID as a coordinated compatibility migration.
4. Rename the root Gradle project accordingly.
5. Rename client bootstrap/UI classes where doing so improves ownership clarity.
6. Update documentation and resource keys that explicitly refer to the old generic client identity.
7. Preserve the existing default map key behavior unless a conflict is found.

Not allowed during C1:

- no Utility Manager implementation
- no Performance Manager implementation
- no build/helper tools
- no Axiom duplication
- no new generic client framework
- no renderer/performance engine work
- no feature redesign of world/map/transfer behavior

## Recommended package direction

Do not perform a repository-wide cosmetic package rewrite merely to make paths look new.

Preferred end state is an ownership-explicit package, for example:

```text
com.halokaryamedia.lazybuilder.mapmanager
```

with subpackages introduced only where they improve clarity, such as:

```text
mapmanager/
├── ui/
├── map/
├── world/
├── transfer/
└── net/
```

However package movement should be done in a dedicated mechanical migration after identity changes are validated. Avoid mixing large package moves with behavior changes.

## Compatibility considerations

Changing the Fabric mod ID can affect:

- user config/keybinding category identifiers
- any external mod that checks for the old ID
- launcher/modpack metadata
- saved mod configuration paths if the ID is used there

Before deleting the transitional identity, C1 implementation must search the repository for `lazybuilder_client`, `lazybuilder-client`, `LazyBuilder Client`, and generic key/category identifiers and update them as one coordinated migration.

World-Manager wire/protocol identifiers must remain unchanged unless they explicitly encode the client mod identity. Protocol behavior is not part of this rename.

## Expected C1 result

From the user's perspective there is exactly one existing LazyBuilder Fabric mod after migration:

```text
LazyBuilder Map Manager
```

It produces exactly one deployable JAR and retains all current world/map/transfer functionality.

Utility Manager and Performance Manager remain future independent mods and are not bundled into this JAR during C1.

## Next implementation checkpoint

Before modifying source files:

1. inventory every old generic client identity occurrence;
2. classify each occurrence as display name, Fabric mod ID, artifact/build name, Java class/package, resource key, documentation, or protocol identifier;
3. prepare one coordinated rename set;
4. perform the rename without behavior changes;
5. run the repository's exact-HEAD verification workflow after the rename.
