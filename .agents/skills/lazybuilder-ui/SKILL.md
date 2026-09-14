---
name: lazybuilder-ui
description: Own LazyBuilder presentation and user interaction across two explicit branches: Desktop UI (Tauri/Svelte) and Fabric Client UI. Use only the branch relevant to the current task. Do not use for runtime, world, plugin, or shared protocol semantics.
---

# LazyBuilder UI

Own presentation and user interaction only. Follow `docs/04-system/development-discipline.md`, `docs/04-system/skill-routing.md`, and the relevant UI docs.

Choose exactly one branch for the current decision. Do not load Desktop and Fabric context together unless the request truly spans both presentation surfaces.

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
fullscreen world-map presentation and interaction
World Manager navigation presentation
Pinned / Recent / search client preferences
Import / Export workspace presentation
native file picker/save interaction
client presentation state
presentation/dispatch of already-defined shared protocol actions
```

Canonical context:

1. `docs/04-system/development-discipline.md`
2. `docs/03-client-ui/README.md`
3. `docs/03-client-ui/world-manager-flow.md`
4. exact Fabric source
5. shared/system docs only when presentation depends on their contract

Procedure:

```text
identify user interaction
→ reuse existing screen/keybind/map boundary
→ consume existing typed protocol
→ smallest presentation/state change
→ avoid duplicate server/domain state
→ local/client proof when interaction matters
→ STOP
```

## Fabric interaction lock

Primary entry is map-first:

```text
M
→ World Map
→ Worlds
```

The fullscreen map may use Xaero World Map as a behavioral/familiarity reference, but LazyBuilder owns its own implementation. Do not copy Xaero source, assets, branding, or create a runtime dependency merely to imitate it.

World Manager presentation target:

```text
Search
Pinned
Recent
All Worlds
Archived
+ Add World
```

Manage World presentation:

```text
Teleport
Import / Export
Duplicate
World Settings
Archive
Delete
```

Import and Export share one `WorldTransferScreen`. Do not recreate standalone Import/Export screens or a nested Export submenu.

Pinned/Recent are navigation preferences only. They never become server metadata, lifecycle, or keep-loaded state.

Do not display manual Load/Unload, runtime-state labels, `autoLoad`, converter/Chunker names, artifact/job terminology, or internal folder identity as normal builder decisions.

## Shared UI invariants

- UI presents state and requests actions; it is not trust/security/domain authority.
- A UI file does not make this Skill the semantic owner of backend behavior.
- Internal maintenance is not exposed as a user choice without real product value.
- Do not persist values that can be derived from canonical backend/server state.
- Do not add controls for unsupported behavior.
- Capability-dependent controls render only from authoritative capability data.
- Prefer typed bounded requests over command-string tunneling.
- Avoid polling when explicit refresh/event/push behavior is sufficient.
- leaving a screen is not cancellation unless the backend owns safe cancellation;
- error/empty/loading states must be actionable and avoid backend jargon.

## Branch-specific invariants

Desktop:

- exactly one frontend runtime bridge;
- backend specialists own workspace/runtime/plugin/world semantics;
- desktop validation improves feedback only.

Fabric:

- server validates and owns mutations;
- if a feature needs a new payload or validation rule, `lazybuilder-protocol` owns that contract first;
- current-world truth comes from server-observed player world transitions;
- map-area export reuses the canonical Import / Export workspace and backend export service;
- no second client world registry or converter catalog.

## Does not own

```text
workspace/server/process/provisioning/runtime → lazybuilder-desktop-runtime
third-party Paper plugin lifecycle           → lazybuilder-plugin-management
Paper world behavior/import/export           → lazybuilder-world-management
shared Paper/Fabric wire semantics           → lazybuilder-protocol
```

## Proof boundary

Static source proves routing/types and screen ownership. Rendered desktop behavior, in-game layout across GUI scales, native file dialogs, map controls, actual protocol interoperability, and large-transfer UX require local/client/live proof.
