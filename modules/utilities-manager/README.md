# Utilities-Manager

Utilities-Manager owns small builder/server convenience features that do not belong to world lifecycle or server process management.

## Feature package rule

Every feature is isolated under its own package:

```text
com.halokaryamedia.lazybuilder.utilities
  feature/
    movement/
    buildhelpers/
    creationtools/
    spectator/
    worldsafety/
```

A feature package may contain its own listener, command, service, state/config mapper, and tests. Features must not reach into another feature's implementation package.

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

## Maintenance constraints

- no background worker unless a feature genuinely requires work while active;
- no dependency on World-Manager internals;
- Paper API first, no NMS unless a proven requirement exists;
- each feature can be enabled/disabled independently through its own config key when implemented;
- disabling one feature must not disable the whole plugin;
- feature tests live beside Utilities-Manager and do not require World-Manager tests to pass;
- version bumps are independent from World-Manager.
