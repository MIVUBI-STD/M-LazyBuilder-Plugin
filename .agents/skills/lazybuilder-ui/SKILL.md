---
name: lazybuilder-ui
description: Own LazyBuilder presentation, interaction, UI issue resolution, and visual proof across Launcher Desktop, Fabric client mods, and plugin-facing presentation. Use for flow/state presentation/input/layout/accessibility/responsive/visual issues. Do not take over runtime, plugin lifecycle, world, or protocol semantics.
---

# LazyBuilder UI

Own presentation and user interaction only. Global diagnosis/proof rules come from `docs/04-system/development-discipline.md`; cross-owner selection/handoff comes from `docs/04-system/skill-routing.md`.

This Skill is consumer-neutral: ChatGPT and Codex use the same UI ownership, diagnosis, and proof rules. Use whichever source/renderer/artifact tools are actually available; never treat an unavailable visual/native proof as observed.

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

Do not enter when canonical semantics are wrong:

```text
runtime/workspace/process/provisioning → lazybuilder-desktop-runtime
third-party plugin lifecycle           → lazybuilder-plugin-management
Paper world behavior/import-export     → lazybuilder-world-management
shared Paper↔Fabric wire semantics     → lazybuilder-protocol
```

## Surface selector

Choose one primary lane per decision.

```text
A Launcher Desktop
→ apps/launcher/src/**
→ Server Library / Plugin Manager / Client Setup / settings/world/runtime presentation

B Fabric Client Mods
→ mods/map-manager/**
→ mods/utility-manager/**
→ mods/performance-manager/** presentation only
→ World Manager / Import-Export client screens

C Plugin-facing presentation
→ plugin inventory/detail/actions
→ warning/status/progress/error presentation
→ presentation-only in-game plugin messages
```

If multiple lanes consume the same canonical result, fix one primary lane and inspect siblings only for regression.

## Context loading

### Default load

```text
this SKILL.md
→ exact affected UI surface/component/controller
→ exact canonical semantic result/state consumed by that surface
```

Do not preload visual references merely because the task is UI-related.

### Required if

```text
non-trivial flow/state/input/async race diagnosis
→ references/issue-resolution-playbook.md

Launcher appearance/layout/state-presentation proof is material
→ references/launcher-visual-preview.md

cross-surface or Minecraft-rendered visual acceptance is material
→ references/visual-proof-system.md

platform/API/version behavior is genuinely uncertain and external research may change the decision
→ references/ui-knowledge-source-policy.md
```

### Do not load if

- do not load all four UI references for one defect;
- do not load visual proof references for copy-only or already-obvious source-state fixes unless appearance acceptance is part of the claim;
- do not load external research when LazyBuilder source/docs already answer the platform behavior;
- do not load semantic-owner Skills merely because their result is displayed in the UI; consume the canonical result instead;
- do not inspect sibling UI lanes unless they share the same canonical result or regression surface.

### Escalate when

Load one additional reference only when the current UI source + canonical semantic result cannot decide the failure class, renderer acceptance, or platform behavior. If evidence proves the semantic result itself is wrong, hand off immediately rather than loading more UI context.

## Failure taxonomy

```text
FLOW       navigation/back/dead-end/context-loss
STATE      stale/duplicated/misleading presentation state
INPUT      click/key/focus race, duplicate submit, input leak
ASYNC      pending/retry/late-response/premature-transition bug
LAYOUT     overlap/overflow/density/GUI-scale failure
VISUAL     hierarchy/contrast/token/icon inconsistency
COPY       consequence/recovery terminology unclear
ACCESS     keyboard/focus/non-hover/readability issue
PERF       UI state triggers unnecessary rebuild/cache/render churn
OWNERSHIP  semantic owner is wrong; UI must hand off
UNKNOWN    next separating evidence required
```

`OWNERSHIP` is not patched in UI. Reclassify through global routing, emit the typed defect packet from `skill-routing.md`, and stop semantic UI mutation.

## Priority

```text
1 safety + interaction correctness
2 truthful state presentation
3 navigation predictability
4 information hierarchy
5 accessibility/input parity
6 responsive density
7 perceived performance
8 visual consistency
9 decorative polish
```

## Preflight

Before mutation answer only what is material:

```text
What user action reproduces the issue?
Expected vs actual?
What canonical state/result drives this surface?
Which semantic owner produced it?
What existing screen/control/result can be reused?
What happens on pending/failure/retry/back-close/repeated input?
What proof can falsify the claim?
```

## Lane A — Launcher Desktop

```text
reproduce
→ trace Svelte state to existing Tauri result
→ verify Rust/runtime authority
→ classify UI failure
→ reuse existing component/bridge/control
→ smallest presentation fix
→ check pending/error/empty + focus/keyboard + representative responsive constraint
→ inspect sibling Launcher consumer only when it uses the same result
→ EXECUTED_SOURCE when available
→ VISUAL_RENDERED when appearance/state presentation changed and renderer proof is available
→ NATIVE_ACCEPTANCE only for remaining Windows-native boundary
→ STOP
```

Invariants:

- one production frontend runtime bridge;
- Svelte never becomes durable runtime/plugin/world authority;
- preview fixtures are proof infrastructure only;
- repeated action cannot dispatch duplicate work;
- late response cannot update another selected entity;
- important actions do not depend on hover alone;
- focus remains visible/logical where keyboard navigation is supported;
- preserve useful selection/search/filter/scroll/tab/server context unless canonical data invalidates it;
- destructive target/consequence is explicit; UI confirmation never replaces backend safety.

## Lane B — Fabric Client Mods

```text
reproduce player interaction
→ identify authoritative server/client state
→ verify Minecraft/Fabric API only when uncertain
→ classify UI/input/layout/render failure
→ reuse existing screen/keybind/controller/protocol
→ smallest presentation fix
→ inspect lifecycle/back-close/repeated input/representative GUI scale
→ EXECUTED_SOURCE when available
→ VISUAL_RENDERED when appearance matters and renderer proof is available
→ NATIVE_ACCEPTANCE only for local GPU/input/environment residue
→ STOP
```

Invariants:

- server remains authorization/mutation authority;
- new neutral payload/validation routes to `lazybuilder-protocol` first;
- presentation state never becomes a second registry/world/runtime authority;
- one physical input cannot dispatch through competing paths;
- GUI-scale behavior is correctness, not polish;
- HTML recreation is never canonical Minecraft proof;
- test hooks may feed/open production screens but may not duplicate product semantics.

### Map interaction lock

```text
M           open / close map
Esc         close current map surface
left drag   pan
scroll      zoom
recenter    return to player
right click contextual map actions
```

Xaero is interaction reference only, never dependency/adapter/authority/source donor. Current managed world + dimension defines map scope; hover/sidebar/favorite/context state must not reset terrain/cache unnecessarily.

### World presentation lock

Navigation:

```text
Search
Pinned
Recent
All Worlds
Archived
+ Add World
```

Manage:

```text
Teleport
Import / Export
Duplicate
World Settings
Archive
Delete
```

Pinned/Recent are client preferences only. Do not expose manual Load/Unload, `autoLoad`, converter names, protocol/job IDs, or internal folder identity as normal builder choices.

## Lane C — Plugin-facing presentation

```text
reproduce
→ consume canonical plugin-management result
→ classify semantic vs presentation defect
→ hand semantic defect to plugin-management
→ otherwise fix list/detail/action/status flow
→ align sibling views using the same canonical result
→ EXECUTED_SOURCE + matching visual proof when available/needed
→ STOP
```

Invariants:

- UI never calculates compatibility/dependency/health independently;
- action availability comes from canonical lifecycle/capability state;
- list/detail/progress/notification share one success criterion;
- pending plugin actions block conflicting duplicates;
- warning copy states consequence/recovery, not resolver jargon;
- late response cannot update the wrong plugin after reorder/selection change.

## Shared interaction invariants

Every async user action follows:

```text
idle → pending → success OR recoverable error
```

Required:

- pending starts before duplicate dispatch is possible;
- success/error clears pending;
- disconnect/reset clears transient pending safely;
- screen close does not imply unsupported cancellation;
- reopen does not replay the prior request;
- keep valid previous content visible during safe refresh when truthful;
- leaving a surface is not cancellation unless the semantic owner supports it.

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

Do not rebuild a heavy renderer for hover/selection/menu/favorite-only changes.

## Accessibility / layout / performance

- important actions have non-hover access;
- desktop keyboard focus/order is coherent;
- icon-only controls have labels/tooltips where supported;
- state should not rely on color alone when another cue is practical;
- long names/max-content states are deliberate, not accidental overflow;
- test representative constraints only when relevant;
- avoid unnecessary core horizontal scrolling;
- motion explains change/feedback or is omitted;
- presentation state must not trigger heavy cache/render resets without content change;
- avoid polling when event/revision state already exists.

## Visual proof

Detailed mechanics live in `references/visual-proof-system.md`. UI shorthand maps to the global proof vocabulary:

```text
L0 → STATIC_SOURCE
L1 → EXECUTED_SOURCE
L2 → VISUAL_SIMULATED
L3 → VISUAL_RENDERED (real Svelte/CSS)
L4 → VISUAL_RENDERED (real Minecraft renderer)
L5 → NATIVE_ACCEPTANCE
```

`VISUAL_RENDERED` proves only the captured renderer/scenario. It does not prove backend mutation, Paper authorization, plugin-channel interoperability, filesystem safety, mouse feel, or network timing.

## External knowledge

Use `references/ui-knowledge-source-policy.md` only when platform/API/version behavior is genuinely uncertain. LazyBuilder source/docs remain product authority; external sources are references only.

## Handoff / exit

Use the canonical typed handoff rules in `skill-routing.md`. UI-specific semantic defect packet:

```text
short reproduction
expected vs actual
selected canonical entity id
canonical input/result observed by UI
evidence that defect survives beyond presentation
```

Finish when:

- canonical semantic owner remains authoritative;
- one primary UI lane is corrected;
- async/input/navigation state cannot duplicate or mis-target work;
- relevant layout/accessibility/performance risk is covered;
- matching source/visual proof is complete to the available context ceiling;
- remaining native/live residue is named precisely.

Do not redesign unrelated surfaces or continue decorative polish after the accepted interaction/presentation contract is satisfied.