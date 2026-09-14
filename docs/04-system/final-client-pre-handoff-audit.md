# Final Client Pre-Handoff Audit

## Purpose

This audit closes the current client architecture/concept pass before implementation is handed to a local/Codex verification workflow.

It covers the three Fabric Managers plus the post-C4 builder-facing review addition:

```text
LazyBuilder Client Suite
├── Map Manager
├── Utility Manager
└── Performance Manager

Post-C4 approved addition
└── Map Manager: Copy Review Reference
```

The goal is to stop scope growth, remove obvious static technical debt, and identify the remaining proof that must happen in a build/live environment rather than inventing more client features.

## Architecture result

The client architecture remains exactly three Fabric mods / three output JARs:

```text
Map Manager         -> world / map / transfer workflow
Utility Manager     -> passive non-build client convenience
Performance Manager -> performance status / lightweight resource policy
```

There is no Builder Utilities Manager, Recovery Manager, Session Manager, Project Context Manager, or shared client implementation module.

Building/editing remains owned by Vanilla, Axiom, WorldEdit/WorldEditCUI, MetaBrushes, and other specialist tools.

## Map Manager audit

### Keep

- world/map navigation and presentation;
- current managed-world authority through the existing map protocol/controller path;
- world settings and lifecycle presentation;
- import/export and transfer presentation;
- map surface cache owned by the map experience;
- contextual `Copy Review Reference` action.

### Review Reference result

`Copy Review Reference` is correctly local and contextual:

```text
World: <display name>
World ID: <world id>
Location: <x> <y> <z>
Dimension: <dimension id>
```

It uses existing authoritative managed-world state plus the local player's current position/dimension. It does not add protocol state, persistence, a keybind, a HUD, a collaboration backend, or a dependency on Utility Manager.

### No further Map expansion approved

Do not add issue tracking, review markers, saved cameras, coordinate comments, project inference, duplicate block/material search, measurement, palette, brush, selection, or other build/edit features without a new ownership review.

## Utility Manager audit

Current locked scope remains:

- borderless window presentation;
- extended chat history;
- session-only unsent chat draft;
- reconnect button;
- copy disconnect details;
- resource-reload completion notice;
- native toast notification surface;
- contextual screenshot naming;
- preference persistence for implemented behavior only.

### Pre-handoff cleanup completed

Three static issues were corrected during this audit:

1. **Contextual screenshot collision risk**
   - previous names used second-resolution timestamps while passing an explicit filename to vanilla screenshot save;
   - rapid screenshots could therefore request the same explicit filename;
   - naming now uses a process-monotonic millisecond timestamp so sequential captures cannot collide inside one client process.

2. **Reload notification double scheduling**
   - the reload listener scheduled onto the client and then called a notification helper that scheduled again;
   - the outer scheduling layer was removed;
   - the notification helper remains the single client-thread dispatch owner.

3. **Dormant Utility concepts remain out of active config**
   - Auto Reconnect, Chat Timestamps, Compact Info, filtering, and loading-screen replacement remain deferred rather than appearing as unfinished active product options.

No new Utility scope is approved by this audit.

## Performance Manager audit

Current locked scope remains:

- optional optimizer capability detection;
- on-demand FPS/frame-time/JVM-memory/render-distance/simulation-distance/window-state snapshot;
- minimal native unfocused/minimized FPS fallback;
- automatic ownership handoff to Dynamic FPS.

### Pre-handoff cleanup completed

The Dynamic FPS handoff is now a literal no-op boundary:

```text
external background-FPS provider detected
→ BackgroundFpsController returns without writing a window FPS limit
```

This is stricter than merely skipping the LazyBuilder throttling branch and prevents Performance Manager from performing an unnecessary initial framerate write when another provider owns the behavior.

No profiles, performance HUD, graphics auto-tuning, Sodium/Iris clone UI, renderer hooks, GC controls, culling engine, or metrics history are approved.

## Cross-manager result

The ownership lock remains valid:

| Concern | Owner |
| --- | --- |
| world/map/transfer | Map Manager |
| client convenience | Utility Manager |
| FPS/resource policy | Performance Manager |
| build/edit operations | external specialist tools |
| renderer/shader/culling engines | external optimization stack |

No Manager needs another Manager's implementation package.

No generic shared client module is justified yet.

## Remaining proof boundary

This audit is source/static architecture work. It does **not** claim current local or live runtime verification.

The following should be verified when the project moves into the local/Codex implementation-proof stage:

### Map Manager

- `WorldMapScreen` context menu layout with the sixth action;
- `Copy Review Reference` clipboard output and dimension string;
- current-world transition/clear behavior;
- map selection/export UI;
- transfer error/retry presentation;
- current World Control V5 / Map Action V2 interoperability.

### Utility Manager

- `ScreenshotRecorderMixin` target/signature on Minecraft 1.21.4;
- rapid F2 contextual screenshot naming;
- `DisconnectedScreenMixin` shadow/injection bindings on Minecraft 1.21.4;
- reconnect and copy-details layout;
- borderless startup and F11 interaction;
- resource-reload toast timing;
- chat history/draft mixins.

### Performance Manager

- Minecraft 1.21.4 `Window#setFramerateLimit` behavior;
- focused → unfocused → minimized → focused transitions;
- restoration to the user's configured FPS limit;
- strict no-write behavior when Dynamic FPS is installed;
- capability detection against the actual chosen modpack.

These are proof tasks, not reasons to expand product scope.

## Handoff decision

From the current source/architecture perspective, the client concept is sufficiently defined for handoff.

```text
Map Manager          scope locked
Utility Manager      scope locked
Performance Manager  scope locked
Post-C4 builder audit closed
Review Reference     implemented
Further client scope STOP
```

The next phase should prioritize compile/runtime proof and concrete defect correction. New features should not be added merely because the client is entering implementation/testing.
