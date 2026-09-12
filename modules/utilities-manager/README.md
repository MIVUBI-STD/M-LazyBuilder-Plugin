# Utilities-Manager

Utilities-Manager owns small builder/server convenience features that do not belong to world lifecycle or server process management.

## Feature ownership rule

Utilities are grouped by **real responsibility**, not by one package/class/plugin per command.

```text
com.halokaryamedia.lazybuilder.utilities
  feature/
    movement/
    buildhelpers/
    spectator/
    worldsafety/
```

Each responsibility package may contain the listeners, commands, services, small state/config objects, and tests required by that feature family. A package must not reach into another package's implementation internals.

`UtilityFeature` is the small lifecycle boundary registered by `UtilityFeatureRegistry`. One registered feature may expose several closely related commands/listeners when they share the same responsibility and lifecycle.

Do not create a new framework layer, executor, registry, or Paper plugin merely because another command is added.

## Feature families

```text
Movement — implemented
  Advanced Fly
  Noclip
  Night Vision

Build Helpers — implemented
  Iron Door Toggle
  Double Slab Break
  Glazed Terracotta Rotate

Spectator — planned
  Builder spectator utilities

World Safety — implemented
  Explosion block-damage protection
  Leaves decay protection
  Farmland trample protection
  Dragon egg interaction/teleport protection
```

Banner Creator, Armor Color Creator, and Special Builder Items are intentionally **out of scope** because they are not used in the current builder-server workflow. Do not add a `creationtools` feature family unless a concrete requirement appears later.

### Movement

Movement is one cohesive lifecycle owner with independent ability switches and per-player reversible state.

Commands:

```text
/fly [speed]      toggle Advanced Fly; optional multiplier 0.1–10.0
/noclip           toggle stable spectator-based noclip
/nightvision      toggle persistent builder night vision
/nv               alias for /nightvision
```

Noclip intentionally uses Bukkit spectator mode rather than NMS collision manipulation. The player's previous game mode is restored when noclip is turned off or the feature shuts down. Advanced Fly stores and restores the previous allow-flight/flying/fly-speed state. Night Vision restores any pre-existing night-vision effect instead of deleting it permanently.

### Build Helpers

Build Helpers is one listener-based lifecycle owner with independent behavior switches.

- Iron Door Toggle: right-clicking an iron door toggles its open state directly for builders, without introducing a redstone-control subsystem.
- Double Slab Break: breaking a double slab while sneaking removes one slab, leaves a bottom slab in place, and drops one matching slab item. The sneak guard is configurable and enabled by default to avoid surprising normal block breaks.
- Glazed Terracotta Rotate: sneak-right-clicking glazed terracotta rotates its facing clockwise by 90 degrees. The sneak guard is independently configurable.

The helpers intentionally use stable Bukkit block-data APIs and do not depend on WorldEdit, NMS, or a background worker.

### World Safety

World Safety is one cohesive lifecycle owner with independently configurable protections. Explosion protection preserves the explosion itself while clearing its block-destruction list, so the feature does not become a generic entity-damage or gameplay authority. Farmland protection covers both player physical interaction and entity conversion to dirt. Dragon egg protection denies vanilla block interaction that would move the egg.

WorldEdit aliases, global physics disabling, redstone disabling, world lifecycle, performance optimization, and generic server administration are explicitly out of scope.

## Lifecycle contract

Feature lifecycle is fail-isolated:

- a feature is marked enabled only after `enable()` succeeds;
- a feature remains marked enabled when its `disable()` fails, so registry state does not falsely report successful cleanup;
- `disableAll()` attempts every enabled feature in reverse enable order even when one cleanup fails;
- cleanup failures are aggregated and reported by the plugin bootstrap rather than preventing unrelated feature cleanup.

## Configuration

```yaml
features:
  world-safety:
    enabled: true
    protections:
      explosions: true
      leaves-decay: true
      farmland-trample: true
      dragon-egg-teleport: true

  movement:
    enabled: true
    abilities:
      advanced-fly: true
      noclip: true
      night-vision: true

  build-helpers:
    enabled: true
    helpers:
      iron-door-toggle: true
      double-slab-break: true
      glazed-terracotta-rotate: true
    interaction:
      require-sneak-for-slab: true
      require-sneak-for-rotate: true
```

Feature families are enabled by default for the builder-server baseline, while each contained behavior can be disabled independently.

## Maintenance constraints

- no background worker unless a feature genuinely requires work while active;
- no dependency on World-Manager internals;
- Paper API first, no NMS unless a proven requirement exists;
- each responsibility feature can be enabled/disabled independently through its own config key when implemented;
- disabling one feature must not disable the whole plugin;
- feature tests live inside Utilities-Manager and do not require World-Manager tests to pass;
- version bumps are independent from World-Manager;
- prefer one cohesive feature family over many command-sized micro-features.
