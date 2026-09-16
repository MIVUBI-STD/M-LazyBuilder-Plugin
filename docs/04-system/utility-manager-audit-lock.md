# Utility Manager Audit Lock

## Purpose

This document locks the current Utility Manager architecture after the performance/resource cleanup pass.

Utility Manager is the passive, non-building client convenience layer. It must improve day-to-day Minecraft use without teaching builders a replacement workflow and without absorbing building or performance-policy ownership.

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
├── Compact Debug presentation
└── Contextual clipboard actions
```

Explicitly outside Utility Manager:

```text
Build-specific systems
└── building and editing

Map Manager
└── world, project, map and transfer ownership

Performance Manager
└── FPS/resource policy, throttling, optimization and tuning
```

Compact Debug may observe a tiny bounded metric set because that data is now an explicit builder-facing F3 requirement. Observation does not transfer performance-policy ownership into Utility Manager.

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
| Interface | Compact Debug | Keep | On |
| Clipboard | Small contextual copy helper | Keep | No standalone keybind |

## Runtime/lifecycle lock

Utility Manager stays event/screen/input-driven:

- no client tick loop solely for Utility features;
- no polling worker;
- no background executor;
- no always-on custom HUD;
- no separate runtime per subfeature.

Compact Debug only renders while Vanilla's F3 debug surface is active. It is not a permanent HUD.

Keep Chat Draft is connection-local within the Minecraft session. Closing/reopening chat preserves an unsent draft, but disconnect clears it so text from one server context is not restored in another.

Reconnect state intentionally survives the disconnect event for the current Minecraft session because the disconnect screen needs the previous multiplayer target. The address is never persisted to disk.

Borderless Window is startup-only. It is applied once after the client starts when enabled, and a changed preference takes effect on the next client start. Do not add a window watcher merely to make this live-switchable.

Resource Reload Notice ignores startup resource loading and uses Minecraft's native toast surface only for later reload completion.

Instant Creative Search is input-driven only. It runs inside the existing Creative Inventory `charTyped` path, switches to Vanilla's Search Items tab when needed, focuses Vanilla's existing search field, and lets Vanilla process the original character. It must not add a search worker, keybind, replacement inventory, or independent search index.

Compact Debug replaces only the rendered F3 information wall. Fast Minecraft state may be read from current client state; process CPU is sampled at a bounded cadence; server telemetry is session-scoped and must clear on join/disconnect. GPU or server machine metrics must show unavailable until a truthful source exists.

## Version-maintenance lock

Extended Chat History remains a minimal Vanilla patch rather than a replacement chat subsystem. The implementation modifies Vanilla retention constants in three ChatHud paths. These hooks are mapping/version-sensitive and must be reverified on Minecraft upgrades.

Screenshot naming remains a thin Vanilla interception: explicit filenames are preserved and only automatic names are changed when the preference is enabled.

Instant Creative Search targets `CreativeInventoryScreen.charTyped`, its existing `searchBox`, and Vanilla's private `setSelectedTab` path. These Yarn 1.21.4 targets are mapping/version-sensitive and must be reverified on Minecraft upgrades rather than replaced with a custom creative inventory controller.

Compact Debug targets `DebugHud.render` and `Keyboard.onKey` for the existing F3 presentation/input path. These Yarn 1.21.4 targets are mapping/version-sensitive and must be reverified on Minecraft upgrades. Mixins remain adapters only; metric/state logic stays in normal Utility classes.

## Deferred, not part of the active product surface

These ideas are not implemented and must not appear as active preferences:

- Auto Reconnect: can become surprising or loop against intentionally closed/unavailable servers. Reconsider only with a clear failure/cancellation policy.
- Chat Timestamps: low-value visual modification for the current builder workflow. Reconsider only if actual usage proves a need.
- Message Filtering: can hide errors or server information. Requires a concrete allowlist/visibility contract before implementation.
- Dark/Clean Loading replacement: visual replacement is not currently justified. Keep the safer reload-completion notice instead.

The former `Compact Info` idea is no longer deferred. It has been replaced by the explicitly scoped Compact Debug requirement with a fixed information contract, F3-only lifecycle, and no optimization ownership.

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
- profiler history databases or performance auto-tuning;
- generic clipboard history;
- project/world authority owned by Map Manager.

Using Vanilla's existing Creative Search Items flow earlier is allowed because Utility Manager does not own or replace search semantics, indexing, results, or creative-inventory layout.

Compact Debug may read FPS, bounded process CPU, JVM RAM, player coordinate/facing/biome/time, and explicitly supplied server telemetry for presentation only. Those reads must not become a second performance engine.

## Preference surface

Only implemented behavior is configurable:

```properties
window.borderless=false
chat.extended_history=true
chat.keep_draft=true
connection.reconnect_button=true
screenshots.contextual_names=false
inventory.instant_creative_search=true
hud.compact_debug=true
```

Legacy migration support may read an old key, but new saves must write only the current canonical keys.

## Exit criteria

Utility Manager is considered architecture-locked when:

1. every active preference maps to implemented behavior;
2. no feature duplicates Vanilla, Map Manager, Performance Manager policy, or build-specific ownership;
3. there is one Utility Manager Fabric mod and one output JAR;
4. no subfeature introduces a background poller/worker;
5. lifecycle boundaries are explicit for chat draft, reconnect target, startup-only window behavior, input-driven creative search, and F3-only Compact Debug;
6. mapping-sensitive mixins are documented as upgrade verification points;
7. server telemetry cannot survive a server/session transition;
8. unsupported metrics remain explicitly unavailable rather than inferred.

Additional Utility features should not be added by default. New requests must first pass the ownership/overlap filter.
