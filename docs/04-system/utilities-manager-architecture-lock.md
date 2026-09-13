# Utilities-Manager Architecture Lock

Utilities-Manager is the Paper-side owner for small builder conveniences that do not belong to world lifecycle, desktop process management, plugin installation, or performance tuning.

## Locked scope

Current supported feature families:

```text
Utilities-Manager
├── World Safety
│   ├── explosion block-damage protection
│   ├── leaves decay protection
│   ├── farmland trample protection
│   └── dragon egg interaction protection
├── Movement
│   ├── Advanced Fly
│   ├── spectator-based Noclip
│   └── Night Vision
└── Build Helpers
    ├── Iron Door Toggle
    ├── Double Slab Break
    └── Glazed Terracotta Rotate
```

Creation Tools and a second custom Spectator family are intentionally excluded. Spectator movement already belongs to Movement/Noclip and normal Minecraft/Paper spectator behavior remains available without another authority.

## Canonical runtime shape

```text
UtilitiesManagerPlugin
↓
UtilityFeatureRegistry
├── WorldSafetyFeature
├── MovementFeature
└── BuildHelpersFeature
```

`UtilitiesManagerPlugin` owns bootstrap/config wiring only. `UtilityFeatureRegistry` owns feature lifecycle ordering and failure isolation. Each feature family owns only its own Paper listeners, commands, reversible player state, and configuration model.

## Configuration ownership

Each family has one configuration model and one section under `features`:

```text
features.world-safety
features.movement
features.build-helpers
```

A family-level `enabled` switch controls lifecycle. Behavior-level switches stay inside the owning family. Do not create a second global settings registry or cross-module configuration owner.

## Lifecycle contract

- register features once during plugin startup;
- mark a feature enabled only after `enable()` succeeds;
- disable enabled features in reverse activation order;
- continue cleanup when one feature fails to disable, then report the combined failure;
- no background worker, watcher, or polling loop is allowed unless a future feature has a concrete active-runtime requirement;
- player state modified by Movement must be restored on feature shutdown or player exit where applicable.

## Dependency boundary

Utilities-Manager must remain independently deployable.

It must not import World-Manager internals, desktop implementation code, Fabric client code, or third-party build-tool internals. Stable Paper/Bukkit APIs are the preferred runtime boundary. No NMS is allowed without a proven requirement that cannot be satisfied through Paper/Bukkit.

## Scope exclusions

Do not add the following without a new explicit requirement:

- world lifecycle, world storage, import/export, backup, conversion;
- plugin install/update/disable/remove;
- server process control or JVM management;
- performance optimizer behavior;
- WorldEdit aliases or duplicate build-tool behavior;
- Banner Creator;
- Armor Color Creator;
- Special Builder Items;
- a duplicate spectator/game-mode subsystem.

## Proof boundary

A green repository verification establishes source/build/test proof only. Live behavior such as actual block interaction, player state restoration, Paper event ordering, and plugin interoperability still requires later LOCAL_CODE / LIVE_SERVER validation.

Any future Utilities feature should first demonstrate that it belongs to one existing responsibility family. Add a new family only when the responsibility is distinct, durable, and cannot be represented cleanly by World Safety, Movement, or Build Helpers.
