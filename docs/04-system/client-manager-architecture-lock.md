# LazyBuilder Client Manager Architecture Lock

Status: architecture lock for the `Local` branch after Map, Utility, Performance, and cross-manager audits.

## Purpose

LazyBuilder client-side functionality is split by semantic ownership. The goal is to keep the client familiar to Minecraft builders, avoid duplicated tools, avoid unnecessary shortcuts, and prevent overlap with Axiom, WorldEdit, or specialist optimization mods.

This document covers only client-side Fabric responsibilities. Build-specific helper utilities are intentionally deferred and are not part of the current implementation scope.

## Client-side manager model

```text
LazyBuilder Client Suite
├── Map Manager              -> 1 Fabric mod / 1 JAR
├── Utility Manager          -> 1 Fabric mod / 1 JAR
└── Performance Manager      -> 1 Fabric mod / 1 JAR
```

Each manager is exactly one deployable Fabric mod. Internal identifiers such as Fabric `mod id` and Gradle artifact names are implementation metadata for that same mod; they are not additional plugins/mods and must not be presented to users as separate components.

Each manager owns one responsibility. Features must have exactly one owner.

### Map Manager

Owns world/map workflow that already exists in the current Fabric client:

- world list and world-management UI
- world map surface and map navigation
- world settings UI
- world create/add/duplicate/delete actions exposed by the World-Manager protocol
- world transfer UI and transfer preferences
- client-side map surface caching required by the map experience
- Fabric-to-Paper World-Manager transport and payload handling

Map Manager does **not** own generic client conveniences, renderer optimization, chat QoL, building/editing tools, measurement, palettes, placement helpers, or Axiom functionality.

Canonical source authority:

```text
client/map-manager/
```

User-facing component:

```text
LazyBuilder Map Manager
```

Implementation metadata for this same single mod:

```text
Fabric mod id: lazybuilder_map_manager
Build artifact: lazybuilder-map-manager.jar
```

### Utility Manager

Owns passive, non-building client convenience only.

Current implemented and locked scope:

- borderless window presentation
- extended chat history
- persistent unsent chat draft within the current session
- reconnect button
- contextual copy of connection/disconnect details
- resource-reload completion notice
- shared native-toast notification surface
- contextual screenshot naming while preserving vanilla `F2`
- preference persistence for implemented behavior only

Deferred ideas such as Auto Reconnect, Chat Timestamps, Compact Info, Message Filtering, and replacement loading visuals are not active product scope. They may only return after a separate ownership/value review.

Rules:

- no default shortcut is required for Utility Manager
- vanilla controls remain authoritative (`E`, `F2`, `F3`, chat controls, etc.)
- Utility Manager must not create a parallel inventory, block browser, command workflow, build HUD, camera tool, editing system, generic clipboard manager, or performance engine
- visual/behavior-changing features should be opt-in unless they are nearly invisible compatibility improvements

User-facing component:

```text
LazyBuilder Utility Manager
```

Implementation metadata for this same single mod:

```text
Fabric mod id: lazybuilder_utility_manager
Build artifact: lazybuilder-utility-manager.jar
```

This is one Fabric mod, not three components. The mod id and JAR name are only technical identifiers.

Note: this is a **Fabric client manager** and is distinct from the existing Paper `Utilities-Manager`. Runtime naming must make that distinction explicit in technical documentation when ambiguity is possible.

See `utility-manager-audit-lock.md` for the detailed scope decision.

### Performance Manager

Owns performance coordination and lightweight resource policy, not renderer internals.

Current implemented and locked scope:

- capability detection for installed optimization mods
- on-demand FPS / approximate frame-time / JVM-memory status
- on-demand render-distance and simulation-distance status
- on-demand window focus/minimized state
- minimal background FPS fallback for unfocused/minimized windows
- automatic handoff to Dynamic FPS when that external provider is installed

Performance profiles, permanent HUD/overlay, graphics auto-tuning, Sodium/Iris setting cloning, shader management, renderer hooks, memory optimization, chunk optimization, culling algorithms, and metrics-history storage are deferred. They are not approved by default.

Performance Manager must **not** reimplement specialist engines.

The following remain external foundations unless a future architecture review explicitly changes this decision:

- Sodium
- Iris
- ImmediatelyFast
- FerriteCore
- EntityCulling
- MoreCulling

Optional external conveniences/providers may also be detected without being absorbed:

- Sodium Extra
- Reese's Sodium Options
- Dynamic FPS

Performance Manager may detect and coordinate these mods, but it does not copy their source, import their implementation packages, replace their algorithms, or mirror their complete settings surfaces.

User-facing component:

```text
LazyBuilder Performance Manager
```

Implementation metadata for this same single mod:

```text
Fabric mod id: lazybuilder_performance_manager
Build artifact: lazybuilder-performance-manager.jar
```

This is one Fabric mod and produces one Manager artifact. The internal mod id is not an additional plugin.

See `performance-manager-audit-lock.md` for the detailed scope decision.

## External build-tool boundary

The client suite does not duplicate or wrap professional building/editing tools.

Explicitly external:

- Axiom
- WorldEdit / WorldEditCUI
- FAWE ecosystem
- FastAsyncVoxelSniper
- MetaBrushes
- other specialist build editors

The following categories are therefore deferred from LazyBuilder client implementation until a separate final build-utility review:

- measurement/ruler
- selection/editing
- palettes/hotbar building systems
- placement helpers
- symmetry/build guides
- terrain/brush tools
- precision build flight
- blueprint/build manipulation
- build-specific free camera/inspection systems
- other features already handled well by Axiom or another external builder tool

## Ownership rules

1. **One Manager = one Fabric mod = one output JAR.**
2. **Map Manager = world/map workflow.**
3. **Utility Manager = client convenience and usability.**
4. **Performance Manager = resource/performance coordination.**
5. **Axiom/external tools = building and world editing.**
6. Vanilla behavior stays authoritative where it already provides a familiar workflow.
7. Do not add a shortcut when a setting, context action, or existing vanilla interaction is sufficient.
8. Do not duplicate settings pages owned by Sodium, Iris, Minecraft, or other specialist mods.
9. Shared services must have one implementation and may be consumed by multiple managers only through a small stable client contract when a second real consumer exists.
10. No manager imports another manager's implementation packages.
11. Build-specific utilities remain parked until Map, Utility, and Performance boundaries are stable.
12. New Utility or Performance features must pass an ownership/overlap review before implementation.

## Repository shape

```text
client/
├── map-manager/             -> lazybuilder-map-manager.jar
├── utility-manager/         -> lazybuilder-utility-manager.jar
├── performance-manager/     -> lazybuilder-performance-manager.jar
└── README.md
```

All three client Managers now exist as independent Fabric source authorities. There must not be separate JARs for subfeatures such as chat, window behavior, FPS monitoring, notifications, or map subfeatures.

Shared protocol types that are genuinely consumed by Paper and Fabric remain in the existing versioned protocol ownership model. A new generic shared client implementation tree must not be created merely for convenience.

## Migration phases

### Phase C1 — Map Manager identity and path migration

- preserve all existing world/map behavior
- use `client/map-manager/` as the single source authority
- produce one `lazybuilder-map-manager.jar`
- keep protocol compatibility unchanged

Status: implemented.

### Phase C2 — Utility Manager

- one independent Fabric mod
- one `lazybuilder-utility-manager.jar`
- implemented non-tool conveniences only
- no mandatory default keybinds
- active scope locked by Utility audit

Status: implemented / scope locked.

### Phase C3 — Performance Manager

- one independent Fabric mod
- one `lazybuilder-performance-manager.jar`
- capability detection and on-demand state first
- minimal background FPS fallback only where no external provider owns it
- no profiles/UI/optimizer-engine expansion without a new review

Status: implemented baseline / scope locked.

### Phase C4 — Cross-manager verification

Verified architecture requirements:

- each Manager has one Fabric identity and one artifact identity;
- Map Manager is the only client Manager wired to shared World-Manager protocol;
- Utility Manager and Performance Manager remain independent of Map Manager implementation packages;
- no Manager imports another Manager's implementation packages;
- Utility window presentation and Performance focus/minimized reads are separate ownership domains rather than duplicate systems;
- no generic shared client implementation module is currently justified;
- external build tools and specialist performance engines retain their ownership.

Status: complete / architecture locked.

See `client-cross-manager-audit-lock.md` for the final C4 audit.

## Next architecture gate

Client Manager expansion is closed by default. The next design discussion should be a separate **build-utility overlap audit** before any builder-facing feature is implemented. That audit must begin from what Vanilla, Axiom, WorldEdit, and the current mod stack already provide rather than from a wishlist of new tools.

## Naming collision note

The repository already contains a Paper `Utilities-Manager`. To avoid ambiguity:

- the user-facing Fabric mod is simply **LazyBuilder Utility Manager**
- technical documentation may say **Fabric Utility Manager** when runtime distinction is necessary
- technical documentation should say **Paper Utilities-Manager** for the server plugin

Do not rename the existing Paper Utilities-Manager as part of this client migration.
