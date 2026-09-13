---
name: lazybuilder-ui
description: Own LazyBuilder presentation and user interaction across two explicit branches: Desktop UI (Tauri/Svelte) and Fabric Client UI (including Xaero integration). Use only the branch relevant to the current task. Do not use for runtime, world, plugin, or shared protocol semantics.
---

# LazyBuilder UI

Own presentation and user interaction only. Follow `docs/04-system/development-discipline.md` and `docs/04-system/skill-routing.md`.

Choose exactly one branch for the current decision. Do not load Desktop and Client context together unless the user request truly spans both presentation surfaces.

## Branch A — Desktop UI

Owns:

```text
Svelte pages/components/navigation
workspace launcher/adoption/provisioning presentation
dashboard/settings/plugins/worlds presentation
loading/error/empty/progress states
frontend request/result typing
one frontend Tauri bridge surface
```

Canonical context:

1. `docs/04-system/development-discipline.md`
2. `docs/04-system/skill-routing.md`
3. exact desktop frontend source
4. product/domain contract only when presentation depends on it

Procedure:

```text
identify real user decision
→ derive state from backend where possible
→ reuse existing bridge/control
→ remove redundant facade/state/control if no value
→ implement smallest presentation change
→ local desktop proof when interaction matters
→ STOP
```

## Branch B — Fabric Client UI

Owns:

```text
Fabric screens/overlays/keybinds
Xaero World Map integration
client presentation state
map preview/location interaction UX
presentation/dispatch of already-defined shared protocol actions
```

Canonical context:

1. `docs/04-system/development-discipline.md`
2. `docs/03-client-ui/README.md`
3. `docs/04-system/skill-routing.md`
4. exact client/integration source
5. system docs only when client/server ownership changes

Procedure:

```text
identify user interaction
→ reuse existing screen/keybind/integration boundary
→ consume existing typed protocol
→ smallest presentation/state change
→ avoid duplicate server/domain state
→ local/client proof when interaction matters
→ STOP
```

## Shared UI Invariants

- UI presents state and requests actions; it is not the trust/security/domain authority.
- A UI file does not make this Skill the semantic owner of backend behavior.
- Internal maintenance is not exposed as a user choice without real product value.
- Do not persist values that can be derived from canonical backend/server state.
- Do not add controls for unsupported or niche behavior without an explicit requirement.
- Prefer typed bounded requests over command-string tunneling.
- Avoid continuous polling when explicit refresh/event/delta behavior is sufficient.

## Branch-Specific Invariants

Desktop:

- exactly one frontend runtime bridge;
- backend specialists own workspace/runtime/plugin/world semantics;
- desktop validation improves feedback only.

Fabric/Xaero:

- server validates and owns mutations;
- if a feature needs a new payload or validation rule, `lazybuilder-protocol` owns that contract first;
- Xaero-specific code stays behind one integration boundary;
- current Xaero scope remains Map Preview + Teleport to Location unless explicitly expanded.

## Does Not Own

```text
workspace/server/process/provisioning/runtime → lazybuilder-desktop-runtime
third-party Paper plugin lifecycle           → lazybuilder-plugin-management
Paper world behavior/import/export           → lazybuilder-world-management
shared Paper/Fabric wire semantics           → lazybuilder-protocol
```

## Proof Boundary

Static source proves routing/types. Rendered desktop interaction, file pickers, in-game screens, keybind behavior, Xaero compatibility, and other actual UI behavior require the appropriate local/client/live proof.
