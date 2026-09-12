# Utilities-Manager

Utilities-Manager owns small builder/server convenience features that do not belong to world lifecycle or server process management.

## Feature ownership rule

Utilities are grouped by **real responsibility**, not by one package/class/plugin per command.

```text
com.halokaryamedia.lazybuilder.utilities
  feature/
    movement/
    buildhelpers/
    creationtools/
    spectator/
    worldsafety/
```

Each responsibility package may contain the listeners, commands, services, small state/config objects, and tests required by that feature family. A package must not reach into another package's implementation internals.

`UtilityFeature` is the small lifecycle boundary registered by `UtilityFeatureRegistry`. One registered feature may expose several closely related commands/listeners when they share the same responsibility and lifecycle.

Do not create a new framework layer, executor, registry, or Paper plugin merely because another command is added.

## Planned slices

```text
Movement
  Advanced Fly
  Noclip
  Night Vision

Build Helpers
  Iron Door Toggle
  Double Slab Break
  Glazed Terracotta Rotate

Creation Tools
  Banner Creator
  Armor Color Creator
  Special Items

Spectator
  Builder spectator utilities

World Safety
  Explosion protection
  Leaves decay protection
  Farmland protection
  Dragon egg teleport protection
```

WorldEdit aliases, global physics disabling, redstone disabling, world lifecycle, performance optimization, and generic server administration are explicitly out of scope.

## Lifecycle contract

Feature lifecycle is fail-isolated:

- a feature is marked enabled only after `enable()` succeeds;
- a feature remains marked enabled when its `disable()` fails, so registry state does not falsely report successful cleanup;
- `disableAll()` attempts every enabled feature in reverse enable order even when one cleanup fails;
- cleanup failures are aggregated and reported by the plugin bootstrap rather than preventing unrelated feature cleanup.

## Maintenance constraints

- no background worker unless a feature genuinely requires work while active;
- no dependency on World-Manager internals;
- Paper API first, no NMS unless a proven requirement exists;
- each responsibility feature can be enabled/disabled independently through its own config key when implemented;
- disabling one feature must not disable the whole plugin;
- feature tests live inside Utilities-Manager and do not require World-Manager tests to pass;
- version bumps are independent from World-Manager;
- prefer one cohesive feature family over many command-sized micro-features.
