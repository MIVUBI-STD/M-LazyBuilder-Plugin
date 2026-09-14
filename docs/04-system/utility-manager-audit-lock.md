# Utility Manager Audit Lock

## Purpose

This document closes the current Utility Manager concept pass before Performance Manager work begins.

Utility Manager is the passive, non-building client convenience layer. It must improve day-to-day Minecraft use without teaching builders a replacement workflow and without absorbing building or performance ownership.

## Ownership lock

```text
Utility Manager
├── Chat convenience
├── Connection convenience
├── Window presentation
├── Reload completion UX
├── Shared utility notifications
├── Screenshot naming convenience
└── Contextual clipboard actions
```

Explicitly outside Utility Manager:

```text
Axiom / WorldEdit
└── building and editing

Map Manager
└── world, project, map and transfer ownership

Performance Manager
└── FPS, CPU, GPU, memory and rendering-resource policy
```

## Keep

The current implemented feature set is sufficient for the first stable Utility Manager scope:

| Area | Feature | Decision | Default |
| --- | --- | --- | --- |
| Chat | Extended Chat History | Keep | On |
| Chat | Keep Chat Draft | Keep | On |
| Connection | Reconnect Button | Keep | On |
| Connection | Copy Connection Details | Keep | Contextual |
| Window | Borderless Window | Keep | Off |
| Interface | Shared Notifications | Keep | Internal service |
| Reload | Resource Reload Completion Notice | Keep | Event-driven |
| Screenshot | Contextual Screenshot Names | Keep | Off |
| Clipboard | Small contextual copy helper | Keep | No keybind |

## Deferred, not part of the active product surface

These ideas are not implemented and must not appear as active preferences:

- Auto Reconnect: can become surprising or loop against intentionally closed/unavailable servers. Reconsider only with a clear failure/cancellation policy.
- Chat Timestamps: low-value visual modification for the current builder workflow. Reconsider only if actual usage proves a need.
- Compact Info: overlaps with F3/BetterF3-style information and risks creating another permanent HUD. Keep out unless a concrete non-overlapping requirement appears.
- Message Filtering: can hide errors or server information. Requires a concrete allowlist/visibility contract before implementation.
- Dark/Clean Loading replacement: visual replacement is not currently justified. Keep the safer reload-completion notice instead.

## Rejected overlap

Do not add the following to Utility Manager:

- block/material search;
- measurement;
- palette management;
- placement helpers;
- freecam/zoom/build camera tools;
- hotbar/build profiles;
- NBT/build clipboard tooling;
- command/build macro systems;
- FPS throttling or idle-resource management;
- renderer, shader, culling, memory, or chunk optimization engines;
- generic clipboard history;
- project/world authority owned by Map Manager.

## Preference surface

Only implemented behavior is configurable:

```properties
window.borderless=false
chat.extended_history=true
chat.keep_draft=true
connection.reconnect_button=true
screenshots.contextual_names=false
```

Legacy migration support may read an old key, but new saves must write only the current canonical keys.

## Interaction rules

Utility Manager should remain close to zero-interaction:

- no mandatory keybinds;
- no radial menu;
- no permanent manager HUD;
- no background polling unless a future feature proves it is unavoidable;
- prefer vanilla screens, controls, events, and toast surfaces;
- add contextual actions only where the user already expects that information.

## Exit criteria

The current Utility Manager concept is considered sufficiently scoped for the architecture phase when:

1. every active preference maps to implemented behavior;
2. no feature duplicates Vanilla, Axiom, Map Manager, or Performance Manager ownership;
3. there is still one Utility Manager Fabric mod and one output JAR;
4. no subfeature introduces a separate runtime component;
5. deferred ideas remain out of the active config/UI until independently justified.

With these rules locked, additional Utility features should no longer be added by default. New requests must first pass the ownership/overlap filter. The next client architecture phase is Performance Manager.
