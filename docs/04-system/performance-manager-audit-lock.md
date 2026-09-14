# Performance Manager Audit Lock

## Purpose

This document closes the current Performance Manager concept pass before any additional UI, profile, renderer, or tuning work is considered.

Performance Manager is a coordination and resource-policy layer. It must remain small, predictable, and compatible with specialist optimization mods instead of becoming another optimizer stack.

## Ownership lock

```text
Performance Manager
├── capability detection
├── on-demand performance state
└── lightweight background FPS policy
```

External specialist ownership remains:

```text
Sodium            -> renderer foundation
Iris              -> shader pipeline
ImmediatelyFast   -> immediate rendering optimization
FerriteCore       -> memory optimization
EntityCulling     -> entity occlusion/culling
MoreCulling       -> additional block/entity culling
```

Performance Manager may detect those capabilities but must not duplicate their algorithms or settings pages.

## Keep

| Area | Feature | Decision |
| --- | --- | --- |
| Capability | Optimizer/mod detection | Keep |
| State | FPS | Keep |
| State | Approximate frame time | Keep |
| State | JVM used/max memory | Keep |
| State | Render distance | Keep |
| State | Simulation distance | Keep |
| State | Focus/minimized state | Keep |
| Resource policy | Unfocused FPS limit | Keep |
| Resource policy | Minimized FPS limit | Keep |
| Compatibility | Dynamic FPS handoff | Keep |

The current background policy is intentionally narrow. It only changes the temporary window framerate limit and never rewrites the user's configured video-option value.

When Dynamic FPS is installed, LazyBuilder's native background FPS policy must remain inactive. Two background-FPS owners must never run at the same time.

## Deferred, not active product scope

The following are not justified for the current implementation and must not be added by default:

- performance profiles such as Balanced, Large Map, Visual Review, or Custom;
- permanent performance HUD/overlay;
- automatic render-distance or simulation-distance tuning;
- automatic graphics-quality changes;
- Sodium setting mirroring or replacement UI;
- Iris setting mirroring or shader management;
- automatic shader enable/disable policy;
- GPU telemetry loops;
- FPS/frame-time history storage;
- memory cleanup/GC controls;
- chunk cache or renderer optimization engines;
- entity/block culling implementations;
- an additional renderer abstraction.

Profiles may only return if there is a concrete group of settings that Performance Manager itself legitimately owns. A profile must not become a wrapper that silently changes settings owned by Minecraft, Sodium, Iris, or other mods.

## Dynamic FPS relationship

Dynamic FPS is treated as an optional external provider rather than a required dependency.

```text
Dynamic FPS installed
└── LazyBuilder background FPS policy = no-op

Dynamic FPS absent
└── LazyBuilder may provide the minimal native fallback
```

The native fallback exists only to cover the small unfocused/minimized FPS use case. It is not intended to reproduce Dynamic FPS feature-for-feature.

## External mod decisions

- **Sodium**: keep external; never reimplement renderer work.
- **Iris**: keep external; do not own shaders.
- **ImmediatelyFast**: keep external.
- **FerriteCore**: keep external.
- **EntityCulling**: keep external.
- **MoreCulling**: keep external; it does not duplicate EntityCulling enough to justify native replacement.
- **Sodium Extra**: optional external convenience; audit only if a concrete setting overlap appears.
- **Reese's Sodium Options**: optional UI convenience; removable later if users do not need it, but not a Performance Manager responsibility.
- **Dynamic FPS**: optional external provider; native fallback yields to it automatically.
- **Chunks Fade In**: cosmetic rather than performance ownership; do not absorb into Performance Manager.

## Interaction rules

Performance Manager should require almost no interaction:

- no mandatory keybinds;
- no radial menu;
- no permanent HUD;
- no notification spam;
- no hidden graphics changes;
- no duplicate settings pages;
- no imports from Map Manager or Utility Manager implementation packages;
- no direct dependency on specialist optimizer implementation packages.

## Current configuration surface

Only the implemented native fallback policy is configurable:

```properties
background.enabled=true
background.unfocused_fps=30
background.minimized_fps=10
```

These settings are ignored while an external background-FPS provider such as Dynamic FPS owns that responsibility.

## Exit criteria

The first Performance Manager scope is considered sufficiently complete when:

1. capability detection remains passive;
2. state reads are on-demand and do not create a metrics collection subsystem;
3. background FPS policy is the only continuous hook;
4. Dynamic FPS suppresses the native fallback;
5. specialist optimizers remain external;
6. no profile or UI exists without a proven non-overlapping need;
7. Performance Manager remains one Fabric mod and one output JAR.

Additional Performance Manager features should now require a separate ownership/overlap review rather than being added by default.
