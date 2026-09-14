# LazyBuilder Client Manager Architecture Lock

Status: architecture lock for the `Local` branch before client-side expansion.

## Purpose

LazyBuilder client-side functionality is split by semantic ownership. The goal is to keep the client familiar to Minecraft builders, avoid duplicated tools, avoid unnecessary shortcuts, and prevent overlap with Axiom, WorldEdit, or specialist optimization mods.

This document covers only client-side Fabric responsibilities. Build-specific helper utilities are intentionally deferred and are not part of the current implementation scope.

## Client-side manager model

```text
LazyBuilder Client Suite
├── Map Manager
├── Utility Manager
└── Performance Manager
```

Each manager is a distinct client responsibility. Features must have exactly one owner.

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

The current `client/fabric` implementation is the source to be migrated/renamed into Map Manager. The current mod identity `lazybuilder_client` / `LazyBuilder Client` is therefore transitional.

Target identity:

```text
Display name: LazyBuilder Map Manager
Mod id:       lazybuilder_map_manager
Artifact:     lazybuilder-map-manager
```

The rename must be performed as a controlled migration rather than a blind package rename because the current client also owns shared UI/bootstrap classes and World-Manager protocol integration.

### Utility Manager

Owns passive, non-building client convenience only.

Current approved scope:

- window mode / borderless behavior
- focus and window preference persistence
- clean loading/reload UX where safe
- extended chat history
- persistent chat draft
- copy-message convenience
- optional timestamps and message filtering
- reconnect button and optional auto-reconnect
- client UI preference persistence
- screenshot organization as an opt-in convenience
- shared client notifications
- small clipboard conveniences
- optional compact information display that does not replace vanilla F3

Rules:

- no default shortcut is required for Utility Manager
- vanilla controls remain authoritative (`E`, `F2`, `F3`, chat controls, etc.)
- Utility Manager must not create a parallel inventory, block browser, command workflow, build HUD, camera tool, or editing system
- visual/behavior-changing features should be opt-in unless they are invisible compatibility improvements

Target identity:

```text
Display name: LazyBuilder Utility Manager
Mod id:       lazybuilder_utility_manager
Artifact:     lazybuilder-utility-manager
```

Note: this is a **Fabric client manager** and is distinct from the existing Paper `Utilities-Manager`. Runtime and artifact naming must always make that distinction explicit in documentation and build output.

### Performance Manager

Owns performance coordination and resource behavior, not renderer internals.

Approved native scope:

- FPS / frame-time / memory status
- performance profiles such as Balanced, Large Map, Visual Review, and Custom
- background FPS behavior for unfocused/minimized/idle states
- capability detection for installed optimization mods
- lightweight integration/control surface for relevant vanilla/Sodium/Iris settings where safe
- performance warnings/status without notification spam

Performance Manager must **not** reimplement specialist engines.

The following remain external foundations unless a future architecture review explicitly changes this decision:

- Sodium
- Iris
- ImmediatelyFast
- FerriteCore
- EntityCulling
- MoreCulling

Performance Manager may detect and coordinate these mods, but it does not copy their source or replace their algorithms.

Target identity:

```text
Display name: LazyBuilder Performance Manager
Mod id:       lazybuilder_performance_manager
Artifact:     lazybuilder-performance-manager
```

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

1. **Map Manager = world/map workflow.**
2. **Utility Manager = client convenience and usability.**
3. **Performance Manager = resource/performance coordination.**
4. **Axiom/external tools = building and world editing.**
5. Vanilla behavior stays authoritative where it already provides a familiar workflow.
6. Do not add a shortcut when a setting, context action, or existing vanilla interaction is sufficient.
7. Do not duplicate settings pages owned by Sodium, Iris, Minecraft, or other specialist mods; link/integrate only where useful.
8. Shared services such as notifications must have one implementation and may be consumed by multiple managers through a small stable client contract.
9. No manager imports another manager's implementation packages.
10. Build-specific utilities remain parked until Map, Utility, and Performance boundaries are stable.

## Target repository shape

The current `client/fabric` project is a single Fabric mod. The target is a small client suite with independent source boundaries while keeping common contracts minimal.

Preferred eventual layout:

```text
client/
├── map-manager/
├── utility-manager/
├── performance-manager/
└── README.md
```

Shared protocol types that are genuinely consumed by Paper and Fabric remain in the existing versioned protocol ownership model. A new generic shared client implementation tree must not be created merely for convenience.

If the three Fabric managers need a tiny shared client contract (for example notification interfaces or capability descriptors), introduce it only after two managers genuinely require the same stable contract. Do not create a master runtime mod by default.

## Migration order

### Phase C1 — Map Manager identity migration

- inventory the current `client/fabric` classes by ownership
- keep all existing world/map behavior intact
- rename artifact/display/mod identity from generic LazyBuilder Client to Map Manager
- move only classes that are proven to belong to another manager; do not refactor for cosmetic reasons
- keep protocol compatibility unchanged during the rename

### Phase C2 — Utility Manager scaffold

- create independent Fabric module
- establish configuration/persistence foundation
- implement only approved non-tool conveniences
- add no mandatory default keybinds

### Phase C3 — Performance Manager scaffold

- create independent Fabric module
- implement capability detection and monitoring first
- add background FPS behavior and profiles only after baseline detection is stable
- treat external optimization mods as optional capabilities

### Phase C4 — Cross-manager verification

- verify independent build boundaries
- verify no duplicate semantic ownership
- verify managers work when installed independently where their feature set allows it
- verify Map Manager does not require Utility or Performance Manager to preserve current functionality
- verify compatibility with Axiom and the external performance stack

## Current source mapping

The existing Fabric source under `client/fabric/src/main/java/com/halokaryamedia/lazybuilder/client/` is predominantly Map Manager code. In particular, current world/map controllers, screens, transfer controllers, preferences, networking, and wire payload adapters belong to the Map Manager migration.

Generic-looking classes such as `LazyBuilderClient`, `LazyBuilderClientUi`, `LbUi`, and `LbButtonWidget` must be reviewed before renaming. They should stay with Map Manager if they are only used by the current world/map surface; they must not become an accidental shared framework without a proven second consumer.

## Naming collision note

The repository already contains a Paper `Utilities-Manager`. To avoid ambiguity:

- documentation should say **Fabric Utility Manager** when runtime matters
- documentation should say **Paper Utilities-Manager** for the server plugin
- artifact and mod IDs remain runtime-specific and unambiguous

Do not rename the existing Paper Utilities-Manager as part of this client migration.
