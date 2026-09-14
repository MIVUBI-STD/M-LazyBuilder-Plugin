# Map Manager Migration Audit

Status: C1 identity migration applied on the `Local` branch; source-path/package move intentionally deferred.

## Goal

Convert the current generic `client/fabric` identity into one clear client mod: **LazyBuilder Map Manager**, without changing runtime behavior and without creating unnecessary shared/core modules.

Product rule:

```text
1 Manager = 1 Fabric mod = 1 JAR
```

Technical identifiers such as Fabric mod IDs and Gradle artifact names are implementation details of that one mod, not separate plugins.

## Ownership finding

The current `client/fabric` source is overwhelmingly Map Manager-owned. Its entrypoint constructs only world, map, and transfer controllers; its UI entrypoint opens the world map; and its networking is the World-Manager/map/transfer bridge.

Therefore C1 is an identity/package cleanup, not a functional split of the existing mod.

### Map Manager-owned source

Current world/map/transfer screens, controllers, preferences, payload adapters, networking, `LazyBuilderClient`, `LazyBuilderClientUi`, `LbUi`, and `LbButtonWidget` remain inside the same Map Manager mod for C1.

Do not create `client-core`, `client-common`, `shared-ui`, or another mandatory runtime dependency. A shared client contract may be extracted later only after another Manager has a proven need for the same stable contract.

## Identity inventory and applied rename

The old generic identity had three technical surfaces belonging to the same single mod:

```text
Display name: LazyBuilder Client
Fabric mod id: lazybuilder_client
Gradle/artifact: lazybuilder-client
```

They have now been coordinated to the Map Manager identity:

```text
Display name: LazyBuilder Map Manager
Fabric mod id: lazybuilder_map_manager
Gradle/artifact: lazybuilder-map-manager
```

This is still exactly **one Fabric mod and one output JAR**.

### Files changed for identity migration

- `client/fabric/src/main/resources/fabric.mod.json`
  - display name changed to `LazyBuilder Map Manager`
  - Fabric mod id changed to `lazybuilder_map_manager`
  - description narrowed to Map Manager ownership
- `client/fabric/gradle.properties`
  - artifact base name changed to `lazybuilder-map-manager`
- `client/fabric/settings.gradle`
  - Gradle root project changed to `lazybuilder-map-manager`
- `client/README.md`
  - current Fabric component documented as Map Manager
- root `README.md`
  - client Manager architecture and current implementation status documented

## Intentionally unchanged during this step

The following remain unchanged to avoid mixing identity migration with a large mechanical source move:

- source directory: `client/fabric/`
- Java package: `com.halokaryamedia.lazybuilder.client`
- entrypoint class: `LazyBuilderClient`
- existing `M` map key behavior
- language namespace `assets/lazybuilder`
- key IDs such as `key.lazybuilder.open_world_map`
- World-Manager wire/protocol identifiers
- world/map/transfer behavior

The `lazybuilder` resource/key namespace is a suite namespace rather than the old Fabric mod ID, so there is no requirement to rename it merely because the mod ID changed.

## Compatibility considerations

Changing the Fabric mod ID can affect external launchers/modpacks or third-party code that explicitly checks `lazybuilder_client`. No repository-owned runtime dependency currently requires preserving the old ID. Protocol identifiers remain unchanged because they represent World-Manager transport, not the client mod identity.

## C1 boundary

Not part of C1:

- Utility Manager implementation
- Performance Manager implementation
- build/helper utilities
- Axiom duplication
- renderer/performance engine work
- world/map/transfer feature redesign
- cosmetic repository-wide package rewrites

## Verification checkpoint

The exact current `Local` HEAD must pass the repository `Verify` workflow after the identity rename. Do not treat older green runs as proof for the renamed identity.

After identity verification is green, the next mechanical decision is whether moving `client/fabric` to `client/map-manager` materially improves maintenance. That path move must be performed separately and must include CI/scripts that currently reference `client/fabric`.
