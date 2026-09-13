---
name: lazybuilder-client-ui
description: Own the LazyBuilder Fabric client presentation boundary: in-game UI, keybinds, Xaero integration, and client-side presentation of shared protocol state. Do not use for shared wire semantics, Paper world rules, or desktop Svelte/Tauri UI.
---

# LazyBuilder Client UI

Own Fabric client presentation only. Follow `docs/04-system/development-discipline.md` and `docs/04-system/skill-routing.md`.

## Owns

```text
Fabric screens/overlays/keybinds
Xaero World Map integration
client presentation state
map preview/location interaction UX
presentation/dispatch of already-defined shared protocol actions
```

## Does Not Own

```text
Svelte/Tauri desktop presentation → desktop-ui
Paper world behavior              → world-management
shared request/result semantics   → protocol
desktop process/runtime           → desktop-runtime
```

If a client feature requires a new payload/validation rule, Protocol owns that contract first. Client UI only presents and dispatches it.

## Canonical Context

1. `docs/04-system/development-discipline.md`
2. `docs/03-client-ui/README.md`
3. `docs/04-system/skill-routing.md`
4. exact client/integration source
5. system docs only when client/server ownership changes

## Procedure

```text
identify user interaction
→ reuse existing screen/keybind/integration boundary
→ consume existing typed protocol
→ smallest presentation/state change
→ avoid duplicate server/domain state
→ local/client proof when interaction matters
→ STOP
```

## Client Invariants

- client presents state; server validates and owns mutations;
- no command-string tunneling when a typed contract exists;
- summaries first, details on demand;
- avoid continuous polling when explicit refresh/event/delta behavior is sufficient;
- Xaero-specific code stays behind one integration boundary;
- missing/disabled Xaero support fails clearly without corrupting world behavior;
- current Xaero scope remains Map Preview + Teleport to Location unless explicitly expanded.

## Proof Boundary

Static source proves routing/contracts. Actual screen interaction, keybind behavior, Xaero compatibility, and in-game UX require appropriate local/client/live-server proof.
