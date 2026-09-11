# Client UI / Xaero Integration

Canonical owner for LazyBuilder client-side interaction and map integration.

## UI Direction

LazyBuilder uses a dedicated client mod for modern UI rather than relying on Bukkit inventory GUIs as the primary experience.

The UI should remain simple:

```text
open LazyBuilder
→ map/world surface
→ choose bounded action
→ close back to normal gameplay
```

Do not reproduce Axiom's full editor UI. Use its clarity only where useful. Do not clone Xaero World Map.

## Xaero Scope

Xaero World Map is retained specifically because its map preview is already mature.

Required integration surface:

```text
Map Preview
Teleport to Location
```

Features such as minimap, waypoint ecosystems, entity radar, route planning, or a new map renderer are outside LazyBuilder ownership unless a later requirement explicitly changes scope.

## Responsibility Boundary

Client owns:

- screen/layout;
- keybind/open-close behavior;
- world/map selection presentation;
- user input for map-location teleport;
- local presentation preferences.

Server owns:

- permissions;
- world existence/state;
- safe teleport resolution;
- create/load/unload/clone/archive/delete;
- settings persistence and mutation;
- export/import validation.

The client must not become authoritative for server state.

## Efficiency

- request summaries first, details on demand;
- do not duplicate Xaero map caches/data when integration can reuse them safely;
- keep network payloads bounded and action-specific;
- no continuous polling when event/delta-based updates are sufficient.
