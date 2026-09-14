# Utilities-Manager Architecture Lock

Utilities-Manager is the Paper-side owner for small builder conveniences that do not belong to world lifecycle, desktop process management, plugin installation, or performance tuning.

## Locked scope

```text
Utilities-Manager
├── Movement
│   ├── familiar gamemode shortcuts
│   ├── Fly
│   ├── spectator-based Noclip
│   └── Night Vision
├── Build Helpers
│   ├── Iron Door Toggle
│   ├── Double Slab Break
│   └── Glazed Terracotta Rotate
└── World Safety
    ├── explosion block-damage protection
    ├── leaves decay protection
    ├── farmland trample protection
    └── dragon egg interaction protection
```

`/lb` is a lightweight discovery/diagnostic adapter. It must not become a second command language or a replacement for familiar direct Minecraft, WorldEdit/FAWE, or Utilities commands.

## Canonical runtime shape

```text
UtilitiesManagerPlugin
↓
UtilityFeatureRegistry
├── WorldSafetyFeature
├── MovementFeature
└── BuildHelpersFeature

UtilitiesCommand
└── presents current capabilities/help/diagnostics only
```

`UtilitiesManagerPlugin` owns bootstrap, config activation, command-health reporting, and reload coordination. `UtilityFeatureRegistry` owns feature lifecycle ordering and failure isolation. Each feature family owns its own Paper behavior and configuration model.

Movement owns `/gmc`, `/gms`, `/gma`, `/gmsp`, `/fly`, `/noclip`, and `/nightvision` so gamemode and temporary movement state have one authority.

## Familiarity contract

- preserve familiar short commands;
- do not force daily builder actions through `/lb ...`;
- reuse Vanilla commands when Vanilla already owns the capability;
- reuse WorldEdit/FAWE commands when those tools already own the capability;
- only add a new root command for a real, frequent builder gap;
- keep normal help concise and permission-aware; keep diagnostics admin-oriented.

## Configuration ownership

Each family owns one section:

```text
features.world-safety
features.movement
features.build-helpers
```

World Safety may scope protections by world name without importing World-Manager internals. The supported scope modes are `all` and `include`, with include/exclude lists owned by World Safety itself.

Missing canonical family sections are configuration errors; do not silently create a typo'd replacement section at runtime.

## Lifecycle contract

- register features once per activation;
- mark a feature enabled only after `enable()` succeeds;
- one feature activation failure must not prevent unrelated families from attempting activation;
- disable enabled features in reverse activation order;
- continue cleanup when one feature fails to disable and report combined failure;
- restore transient Movement player state on shutdown/quit;
- validate a reload candidate before disabling the current runtime;
- no background worker, watcher, or polling loop without a concrete active-runtime requirement.

## Dependency boundary

Utilities-Manager remains independently deployable. It must not import World-Manager internals, desktop implementation code, Fabric client code, or third-party build-tool internals. Stable Paper/Bukkit APIs are the preferred runtime boundary. No NMS is allowed without a proven requirement.

## Scope exclusions

Do not add without a new explicit requirement:

- world lifecycle/storage/import/export/backup/conversion;
- plugin install/update/disable/remove;
- server process/JVM management;
- performance optimizer behavior;
- duplicate Vanilla or WorldEdit aliases;
- Banner Creator, Armor Color Creator, Special Builder Items;
- a second spectator/game-mode subsystem;
- an inventory GUI or custom client UI when concise chat interaction is sufficient.

## Proof boundary

A green repository verification establishes source/build/test proof only. Actual block interaction, command visibility for real permission setups, player state restoration, Paper event ordering, plugin interoperability, and multi-world gameplay behavior still require LOCAL_CODE/LIVE_SERVER validation.
