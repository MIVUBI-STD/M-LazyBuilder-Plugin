---
name: lazybuilder-ui
description: Own LazyBuilder presentation, interaction, UI issue resolution, and visual proof across Launcher Desktop, Fabric client mods, and plugin-facing presentation. Use for flow/state presentation/input/layout/accessibility/responsive/visual issues. Do not take over runtime, plugin lifecycle, world, or protocol semantics.
---

# LazyBuilder UI

Own presentation and user interaction only. Follow `docs/04-system/development-discipline.md`, `docs/04-system/skill-routing.md`, and the exact source/docs for the selected surface.

General UI references are inputs, not product authority. Platform behavior must match the actual Desktop or Minecraft/Fabric surface.

## Entry gate

Use this Skill when the first wrong owner is presentation or interaction:

```text
navigation / flow / back-close behavior
loading / empty / pending / error / success presentation
input/focus/key/click race
layout / overflow / density / GUI scale
visual hierarchy / tokens / icons / copy
accessibility / keyboard / non-hover alternatives
presentation-side async lifecycle
renderer/cache invalidation caused by UI state
visual proof of an already-correct semantic result
```

Do not enter when the underlying canonical state/behavior is wrong. Route that first to:

```text
workspace/process/provisioning/runtime → lazybuilder-desktop-runtime
third-party plugin lifecycle           → lazybuilder-plugin-management
Paper world behavior/import/export     → lazybuilder-world-management
shared Paper/Fabric wire semantics     → lazybuilder-protocol
```

## Surface selector

Choose one primary lane per decision.

### A — Launcher Desktop

```text
apps/launcher/src/**
Server Library presentation
Plugin Manager presentation
Client Setup presentation
settings/world/runtime presentation
confirmation/progress/error flows
```

### B — Fabric Client Mods

```text
mods/map-manager/**
mods/utility-manager/**
mods/performance-manager/** presentation only
World Manager / Import-Export client screens
```

### C — Plugin-facing presentation

```text
plugin inventory/list/detail/actions
plugin warning/status/progress/error presentation
in-game plugin messages when presentation-only
capability-driven action availability
```

If multiple lanes are affected, keep one primary lane and inspect siblings only for regression/synchronization.

## Reference routing

Load only the reference that can change the decision:

```text
issue/state/flow/race diagnosis
→ references/issue-resolution-playbook.md

Launcher visual/layout proof
→ references/launcher-visual-preview.md

cross-surface / Minecraft visual proof
→ references/visual-proof-system.md

external platform/UI research needed
→ references/ui-knowledge-source-policy.md
```

Do not preload every reference for a small copy/spacing/state change.

## Failure taxonomy

```text
FLOW       navigation/back/dead-end/context-loss
STATE      stale/duplicated/misleading presentation state
INPUT      click/key/focus race, duplicate submit, input leak
ASYNC      pending/retry/late-response/premature-transition bug
LAYOUT     overlap/overflow/density/GUI-scale failure
VISUAL     hierarchy/contrast/token/icon inconsistency
COPY       consequence/recovery terminology is unclear
ACCESS     keyboard/focus/non-hover/readability issue
PERF       UI state triggers unnecessary rebuild/cache/render churn
OWNERSHIP  UI workaround is masking backend/plugin/world/protocol defect
UNKNOWN    evidence cannot separate the above
```

Fix the dominant class first. For `OWNERSHIP`, hand off before adding UI-side guesses.

## UI priority

Work in this order:

```text
1 safety + interaction correctness
2 truthful state authority
3 navigation predictability
4 information hierarchy
5 accessibility/input parity
6 responsive density
7 perceived performance
8 visual consistency
9 decorative polish
```

P0/P1 interaction/state defects outrank polish.

## Canonical preflight

Before mutation answer only what is material:

```text
What user action/reproduction is failing?
Expected vs actual?
What canonical state/result drives this surface?
Which semantic owner produces it?
Which existing screen/control/result can be reused?
What happens on pending/failure/retry/empty/back-close/repeated input?
What proof level can falsify the issue?
```

If semantic ownership is unclear, stop and route first.

## Lane A — Launcher Desktop

Procedure:

```text
reproduce user decision/flow
→ trace Svelte state to existing Tauri bridge/result
→ verify Rust/runtime authority
→ classify UI failure
→ reuse existing component/bridge/control
→ smallest presentation/lifecycle fix
→ check pending/error/empty + focus/keyboard + representative responsive constraint
→ inspect sibling Launcher surface only when it consumes the same result
→ source/typecheck proof
→ Launcher visual proof when appearance/state presentation changed
→ local native proof only for remaining Windows/runtime boundary
→ STOP
```

Invariants:

- one production frontend runtime bridge;
- Svelte never becomes durable server/process/plugin/world authority;
- deterministic preview fixtures are proof infrastructure only;
- repeated action cannot dispatch duplicate work;
- late responses cannot update a different selected entity;
- important actions do not depend on hover alone;
- focus remains visible and logical where keyboard navigation is supported;
- errors stay near the affected workflow and expose recovery when practical;
- preserve useful selection/search/filter/scroll/tab/server context unless invalidated by canonical data;
- destructive target/consequence must be explicit; UI confirmation never replaces backend safety.

## Lane B — Fabric Client Mods

Procedure:

```text
reproduce player interaction
→ identify authoritative server/client state
→ verify Minecraft/Fabric API only when uncertain
→ classify UI/input/layout/render failure
→ reuse existing screen/keybind/controller/protocol
→ smallest presentation/state fix
→ inspect screen lifecycle + back/close + repeated input + representative GUI scale
→ Fabric build/artifact proof
→ real Minecraft-rendered visual proof when appearance matters and path exists
→ local PC proof only for remaining native/performance/input-feel boundary
→ STOP
```

Invariants:

- server remains authorization/mutation authority;
- new neutral payload/validation routes to `lazybuilder-protocol` first;
- presentation state never becomes a second registry/world/runtime authority;
- one physical input does not dispatch through independent competing paths;
- prefer immediate stable response over decorative animation;
- GUI-scale behavior is correctness, not polish;
- HTML recreation is never final Minecraft visual proof;
- deterministic test hooks may open/feed a production screen but may not duplicate product semantics.

### Map interaction lock

```text
M           open / close map
Esc         close current map surface
left drag   pan
scroll      zoom
recenter    return to player
right click contextual map actions
```

Xaero is familiarity/interaction reference only; never a dependency, adapter, map authority, or asset/source donor.

Map presentation keeps one canonical map/cache path. Current managed world + dimension defines scope. Hover/sidebar/favorite/context state must not reset terrain/cache unnecessarily.

### World presentation lock

Navigation target:

```text
Search
Pinned
Recent
All Worlds
Archived
+ Add World
```

Manage target:

```text
Teleport
Import / Export
Duplicate
World Settings
Archive
Delete
```

Pinned/Recent are client preferences only. Do not expose manual Load/Unload, `autoLoad`, converter implementation names, protocol/job IDs, or internal folder identity as normal builder choices.

## Lane C — Plugin-facing presentation

Procedure:

```text
reproduce plugin-facing issue
→ consume canonical plugin-management state/result
→ classify semantic vs presentation defect
→ hand semantic defect to lazybuilder-plugin-management
→ otherwise fix list/detail/action/status flow
→ align pending/error/success on sibling plugin views using same result
→ choose Launcher or Minecraft proof lane
→ STOP
```

Invariants:

- UI never calculates compatibility/dependency/health independently;
- action availability comes from canonical lifecycle/capability state;
- list/detail/progress/notification do not invent different success criteria;
- pending plugin actions block conflicting duplicates;
- warning copy states consequence/recovery, not resolver jargon;
- late response cannot update the wrong plugin after reorder/selection change;
- native Minecraft rendering is visual authority for Minecraft-native surfaces.

## Shared interaction invariants

Every user-triggered async action follows:

```text
idle
→ pending
→ success OR recoverable error
```

Required properties:

- pending starts before duplicate dispatch is possible;
- success/error clears pending correctly;
- disconnect/reset clears transient pending safely;
- screen close does not pretend to cancel unsupported backend work;
- reopen does not replay the previous request;
- keep valid previous content visible during safe refresh when truthful and useful;
- leaving a surface is not cancellation unless semantic owner supports cancellation.

Preserve useful navigation state unless invalidated:

```text
selection
search/filter
scroll
active tab
map center/zoom
sidebar state
current workspace/server context
```

Do not rebuild/reset a heavy renderer for hover, selection, menu visibility, favorite state, or other small presentation-only changes.

## Accessibility / layout / performance guardrails

Apply platform-appropriate rules, not generic mobile heuristics.

- important actions have non-hover access;
- keyboard focus/order is coherent on Desktop;
- icon-only controls have labels/tooltips where supported;
- state should not rely only on color when another cue is practical;
- long names and max-content states are deliberate, not accidental overflow;
- test representative minimum/normal/wide or GUI-scale constraints only when relevant;
- avoid core horizontal scrolling where responsive restructuring is practical;
- motion explains change/feedback or is omitted;
- presentation state must not trigger heavy cache/render resets without visual-content change;
- avoid polling every render/tick when event/revision state already exists.

Detailed issue patterns and proof mechanics belong in the references, not duplicated here.

## Visual proof

Use `references/visual-proof-system.md` when appearance/state presentation matters.

Proof ladder:

```text
L0 source inspection
L1 source-contract/typecheck/build/tests
L2 deterministic simulated preview
L3 real Launcher Svelte visual proof
L4 real Minecraft-rendered proof
L5 Local-PC native interaction/integration
```

Use the cheapest level that can falsify the issue; never claim a higher level than observed.

Visual evidence should be tied to exact source revision and identify renderer/scenario/viewport-or-GUI-scale/proof boundary where relevant. Prefer workflow artifacts over committed screenshot baselines.

Do not add visual SaaS, pixel-diff databases, golden-image repositories, second UI implementations, or exhaustive resolution matrices without repeated evidence they are needed.

## External knowledge discipline

Use `references/ui-knowledge-source-policy.md` only when platform/API/version behavior is genuinely uncertain.

Authority order:

```text
LazyBuilder source/docs
→ exact platform/API docs
→ version-matched Minecraft/Fabric/Paper docs
→ mature implementation reference
→ general UX heuristics
→ visual inspiration
```

Study behavior; implement independently. Stop research once ownership, platform behavior, smallest implementation, and proof plan are clear.

## Handoff / exit contract

```text
runtime/workspace/process semantic defect
→ lazybuilder-desktop-runtime

plugin lifecycle semantic defect
→ lazybuilder-plugin-management

world-domain semantic defect
→ lazybuilder-world-management

neutral wire semantic defect
→ lazybuilder-protocol
```

Finish when:

- canonical semantic owner remains authoritative;
- one primary UI lane is corrected;
- async/input/navigation state cannot duplicate or mis-target work;
- representative layout/accessibility/performance risks for the issue are covered;
- matching visual/source proof is complete at the available context ceiling;
- remaining native/live residue is named precisely.

Do not redesign unrelated surfaces, create a second state/workflow owner, or continue polishing after the reported interaction/presentation contract is satisfied.
