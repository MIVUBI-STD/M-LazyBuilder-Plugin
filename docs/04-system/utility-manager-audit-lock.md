# Utility Manager Audit Lock

## Purpose

This document locks the current Utility Manager architecture after the performance/resource cleanup pass.

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
├── Creative-inventory convenience
└── Contextual clipboard actions
```

Explicitly outside Utility Manager:

```text
Build-specific systems
└── building and editing

Map Manager
└── world, project, map and transfer ownership

Performance Manager
└── FPS, CPU, GPU, memory and rendering-resource policy
```

## Keep

The current implemented feature set is sufficient for the stable Utility Manager scope:

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
| Inventory | Instant Creative Search | Keep | On |
| Clipboard | Small contextual copy helper | Keep | No keybind |

## Runtime/lifecycle lock

Utility Manager stays event/screen-driven:

- no client tick loop;
- no polling worker;
- no background executor;
- no permanent HUD;
- no separate runtime per subfeature.

Keep Chat Draft is connection-local within the Minecraft session. Closing/reopening chat preserves an unsent draft, but disconnect clears it so text from one server context is not restored in another.

Reconnect state intentionally survives the disconnect event for the current Minecraft session because the disconnect screen needs the previous multiplayer target. The address is never persisted to disk.

Borderless Window is startup-only. It is applied once after the client starts when enabled, and a changed preference takes effect on the next client start. Do not add a window watcher merely to make this live-switchable.

Resource Reload Notice ignores startup resource loading and uses Minecraft's native toast surface only for later reload completion.

Instant Creative Search is input-driven only. It runs inside the existing Creative Inventory `charTyped` path, switches to Vanilla's Search Items tab when needed, focuses Vanilla's existing search field, and lets Vanilla process the original character. It must not add a search worker, keybind, replacement inventory, or independent search index.

## Version-maintenance lock

Extended Chat History remains a minimal Vanilla patch rather than a replacement chat subsystem. The implementation modifies Vanilla retention constants in three ChatHud paths. These hooks are mapping/version-sensitive and must be reverified on Minecraft upgrades.

This maintenance sensitivity is accepted because the alternative—owning a replacement chat storage/UI subsystem—would create substantially more code, overlap, and runtime ownership.

Screenshot naming also remains a thin Vanilla interception: explicit filenames are preserved and only automatic names are changed when the preference is enabled.

Instant Creative Search targets `CreativeInventoryScreen.charTyped`, its existing `searchBox`, and Vanilla's private `setSelectedTab` path. These Yarn 1.21.4 targets are mapping/version-sensitive and must be reverified on Minecraft upgrades rather than replaced with a custom creative inventory controller.

## Deferred, not part of the active product surface

These ideas are not implemented and must not appear as active preferences:

- Auto Reconnect: can become surprising or loop against intentionally closed/unavailable servers. Reconsider only with a clear failure/cancellation policy.
- Chat Timestamps: low-value visual modification for the current builder workflow. Reconsider only if actual usage proves a need.
- Compact Info: overlaps with existing Minecraft debug information and risks creating another permanent HUD. Keep out unless a concrete non-overlapping requirement appears.
- Message Filtering: can hide errors or server information. Requires a concrete allowlist/visibility contract before implementation.
- Dark/Clean Loading replacement: visual replacement is not currently justified. Keep the safer reload-completion notice instead.

## Rejected overlap

Do not add the following to Utility Manager:

- replacement block/material search engines or build palettes;
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

Using Vanilla's existing Creative Search Items flow earlier is allowed because Utility Manager does not own or replace search semantics, indexing, results, or creative-inventory layout.

## Preference surface

Only implemented behavior is configurable:

```properties
window.borderless=false
chat.extended_history=true
chat.keep_draft=true
connection.reconnect_button=true
screenshots.contextual_names=false
inventory.instant_creative_search=true
```

Legacy migration support may read an old key, but new saves must write only the current canonical keys.

## Exit criteria

Utility Manager is considered architecture-locked when:

1. every active preference maps to implemented behavior;
2. no feature duplicates Vanilla, Map Manager, Performance Manager, or build-specific ownership;
3. there is one Utility Manager Fabric mod and one output JAR;
4. no subfeature introduces a background poller/worker;
5. lifecycle boundaries are explicit for chat draft, reconnect target, startup-only window behavior, and input-driven creative search;
6. mapping-sensitive mixins are documented as upgrade verification points;
7. deferred ideas remain out of the active config/UI until independently justified.

Additional Utility features should not be added by default. New requests must first pass the ownership/overlap filter.
