---
name: lazybuilder-client-ui
description: Specialist for the LazyBuilder Fabric client presentation boundary: in-game UI, keybinds, plugin-message presentation, Xaero integration, and client interaction flow. Use when Fabric/Xaero client UX is the primary change.
---

# LazyBuilder Client UI

Own the Fabric client presentation boundary. Server validates and owns mutations; the client presents state and sends typed bounded requests.

## Use This Owner For

- Fabric client screens/overlays/keybinds;
- Xaero World Map integration;
- client-side presentation of shared protocol state;
- map preview/location interactions and client UX.

Route elsewhere when the primary owner is:

```text
Svelte/Tauri desktop UI       → lazybuilder-desktop-ui
Paper world behavior          → lazybuilder-world-management
shared request/result contract→ lazybuilder-protocol
desktop process/runtime       → lazybuilder-desktop-runtime
```

## Canonical Context

1. `docs/03-client-ui/README.md`
2. `docs/04-system/skill-routing.md`
3. exact client source/integration owner
4. `docs/04-system/README.md` only when client/server ownership changes
5. operations docs only when continuation matters

## Scope Discipline

LazyBuilder does not rebuild Xaero World Map. Current Xaero-owned capability is limited to:

```text
Map Preview
Teleport to Location interaction
```

Do not absorb waypoint, minimap, radar, routing, cave-map, or renderer ownership without an explicit requirement.

## Rules

- Keep UI open/close flow simple and reversible.
- Client presents state; server validates and owns mutations.
- Prefer typed bounded requests over command-string tunneling.
- Fetch summaries first and details only when needed.
- Avoid continuous polling where explicit refresh/event/delta behavior is sufficient.
- Isolate Xaero-specific code behind one integration boundary.
- Missing/disabled Xaero support must fail clearly without corrupting world-management behavior.

## Proof

Static source proves routing/contracts. Actual screen interaction, keybind behavior, Xaero compatibility, and in-game UX require local/client/live-server proof.
