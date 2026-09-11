# LazyBuilder Client UI Skill

Use for LazyBuilder client-mod UI, keybind, protocol presentation, and Xaero World Map integration work.

## Canonical context

1. `docs/03-client-ui/README.md`
2. `docs/04-system/README.md` when client/server ownership changes
3. exact client source and integration evidence
4. `docs/05-operations/README.md` only when continuation matters

## Scope discipline

LazyBuilder does not rebuild Xaero World Map. Current Xaero-owned capability is limited to:

```text
Map Preview
Teleport to Location interaction
```

Do not absorb waypoint, minimap, entity-radar, route, cave-map, or map-renderer ownership without a new explicit requirement.

## Rules

- Keep UI open/close flow simple and reversible.
- Client presents state; server validates and owns mutations.
- Prefer typed bounded requests over command-string tunneling.
- Fetch summaries first and details on demand.
- Avoid continuous polling when event/delta updates suffice.
- Isolate Xaero-specific code behind one integration boundary.
- A missing/disabled Xaero integration must fail clearly rather than corrupt world-management behavior.

## Proof

Static source can prove routing/contracts. Actual screen interaction, keybind behavior, Xaero compatibility, and in-game teleport UX require the appropriate local/client/live-server runtime proof.
