---
name: lazybuilder-desktop-ui
description: Own LazyBuilder Tauri/Svelte desktop presentation: launcher, dashboard/settings/plugins/worlds screens, frontend request/result typing, one Tauri bridge, and interaction states. Do not use for runtime/domain/plugin/world semantics.
---

# LazyBuilder Desktop UI

Own presentation and interaction only. Follow `docs/04-system/development-discipline.md` and `docs/04-system/skill-routing.md`.

## Owns

```text
Svelte pages/components/navigation
workspace launcher/adoption/provisioning presentation
dashboard/settings/plugins/worlds presentation
loading/error/empty/progress states
frontend request/result typing
one frontend Tauri bridge surface
```

## Does Not Own

```text
workspace/server/runtime semantics → desktop-runtime
plugin lifecycle/business rules    → plugin-management
world/Paper behavior               → world-management
shared Paper/Fabric contract       → protocol
Fabric/Xaero UI                    → client-ui
```

A UI file does not make this Skill the semantic owner. If a backend rule changes and UI merely displays it, the backend specialist goes first.

## Canonical Context

1. `docs/04-system/development-discipline.md`
2. `docs/04-system/skill-routing.md`
3. exact frontend source
4. product/domain contract only when the presentation depends on it

## Procedure

```text
identify real user decision
→ derive state from backend where possible
→ reuse existing bridge/control
→ remove redundant facade/state/control if no value
→ implement smallest presentation change
→ local UI proof when interaction matters
→ STOP
```

## UI Invariants

- exactly one frontend runtime bridge;
- UI validation improves feedback but is never the trust/security authority;
- internal maintenance is not exposed as a user choice without real product value;
- do not persist values that backend/source can derive;
- do not add knobs for unsupported or niche behavior without an explicit requirement;
- desktop UI and Fabric client UI remain separate presentation owners.

## Proof Boundary

Static source proves routing/types. Actual desktop interaction, dialogs, platform file pickers, restart UX, and rendered behavior require local desktop proof.
