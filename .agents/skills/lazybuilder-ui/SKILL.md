---
name: lazybuilder-ui
description: Own LazyBuilder presentation, interaction, UI issue resolution, and visual proof across Launcher Desktop (Tauri/Svelte), Fabric client mods, and plugin-facing presentation. Apply UX quality gates for flow, state authority, input races, accessibility, responsive density, perceived performance, visual consistency, platform-accurate research, and commit-addressed visual evidence. Do not take over runtime, plugin lifecycle, world, or protocol semantics.
---

# LazyBuilder UI

Own presentation and user interaction only. Follow `docs/04-system/development-discipline.md`, `docs/04-system/skill-routing.md`, and the exact source/docs for the selected surface.

General UI/UX references are inputs, not authority. Apply only guidance that fits LazyBuilder's actual platform and interaction model; never import web/mobile conventions blindly into Minecraft or desktop UI.

Load references selectively:

```text
issue/state/flow/race problem
→ .agents/skills/lazybuilder-ui/references/issue-resolution-playbook.md

Launcher visual/layout proof
→ .agents/skills/lazybuilder-ui/references/launcher-visual-preview.md

cross-surface visual proof / Minecraft proof
→ .agents/skills/lazybuilder-ui/references/visual-proof-system.md

external UI/platform research needed
→ .agents/skills/lazybuilder-ui/references/ui-knowledge-source-policy.md
```

Do not load every reference for a trivial copy/spacing change.

## Core objective

Every UI change should make the user's next decision clearer while preserving one semantic owner and one source of truth.

Prefer:

```text
fewer visible choices
+ predictable controls
+ explicit state feedback
+ preserved navigation context
+ responsive layout
+ stable rendering
+ one authoritative workflow
+ evidence from the real renderer when appearance matters
```

over adding more screens, managers, modes, caches, persisted state, or compatibility layers.

## Surface selector

Select the smallest applicable UI lane before editing.

### A — Launcher Desktop

```text
apps/launcher/src/**
Tauri/Svelte Server Library
Plugin Manager presentation
Client Setup / mod synchronization presentation
settings/world/runtime presentation
confirmation/progress/error flows
```

### B — Fabric Client Mods

```text
mods/map-manager/** screens/keybinds/map presentation
mods/utility-manager/** client convenience presentation
mods/performance-manager/** presentation/diagnostics only when one exists
World Manager / Import-Export presentation
```

### C — Plugin-facing presentation

Use this lane only for presentation around plugin behavior:

```text
Launcher plugin inventory/list/detail/actions
plugin warnings/status/progress/error presentation
in-game user-facing plugin messages where presentation-only
capability-driven action availability
```

`lazybuilder-plugin-management` still owns scan, dependency, compatibility, install/update/remove, enable/disable, duplicate resolution, and rollback semantics.

If a task spans several lanes, keep **one primary UI lane per decision** and inspect sibling surfaces only for regression/synchronization. Do not let cross-surface work become simultaneous semantic ownership.

## UI priority order

Fix in this order:

1. **Safety and interaction correctness** — no wrong target, duplicate action, stuck screen, input leak, or false success.
2. **State authority and clarity** — current/pending/error/loading/disabled/empty states are truthful and come from one owner.
3. **Navigation predictability** — Back/Esc/toggle behavior is consistent and useful context survives return.
4. **Information hierarchy** — primary task dominates; advanced/destructive actions are progressively disclosed.
5. **Accessibility and input parity** — important actions do not depend on hover/drag alone; focus/keyboard work where supported.
6. **Responsive density** — supported window sizes, GUI scales, long labels, and max-content states do not break layout.
7. **Perceived performance** — no flicker, unnecessary rebuilds, layout thrash, or unrelated cache invalidation.
8. **Visual consistency** — tokens, spacing, type, icons, and semantic colors are coherent.
9. **Decorative polish** — only after everything above is stable.

P0/P1 interaction and state defects always outrank polish.

## Mandatory preflight

Before coding, answer:

```text
What is the user's primary task?
What exact input reproduces the problem?
What is expected vs actual?
Which state is authoritative and where does it come from?
Which owner should decide the underlying behavior?
What existing screen/control/result can be reused?
What happens on pending, failure, retry, empty, resize, back/close, and repeated input?
Does leaving and returning preserve useful context?
Could this change create a second owner/cache/workflow/source of truth?
Can deletion or a smaller lifecycle correction solve it instead?
What proof level can actually falsify the issue?
Does platform/API behavior need external verification before implementation?
```

If state authority or semantic ownership is unclear, route to the owning skill before adding UI-side guesses.

## External knowledge discipline

Do not browse external UI sources by reflex. Use `.agents/skills/lazybuilder-ui/references/ui-knowledge-source-policy.md` when platform behavior, version compatibility, or a new interaction pattern is genuinely uncertain.

Authority order:

```text
LazyBuilder source/docs
→ exact platform/API documentation
→ version-matched Minecraft/Fabric/Paper contracts
→ mature open-source implementation reference
→ general UI/UX heuristics
→ visual inspiration only
```

External references never become product authority.

Default useful sources include:

```text
Fabric custom Screen documentation
Minecraft/Fabric version-matched APIs/mappings
Paper + Adventure documentation
TerraformersMC/ModMenu as a mature Fabric implementation reference
```

Study behavior; implement independently in LazyBuilder style. Do not copy branding/assets/substantial source or add dependencies solely to imitate another UI.

Stop research once platform behavior, ownership, smallest implementation, and proof plan are clear.

## Issue-resolution mode

For a reported UI defect, do not begin with redesign. Use:

```text
reproduce
→ classify dominant defect
→ trace state authority
→ verify uncertain platform behavior if needed
→ find first wrong boundary
→ choose smallest fix
→ inspect sibling surfaces using the same action/state
→ select visual/native proof lane
→ prove at the cheapest useful level
→ state remaining live-proof boundary
```

Primary defect classes:

```text
FLOW      navigation/back/dead-end
STATE     stale/duplicated/misleading authority
INPUT     key/click race, duplicate submit, focus, event leak
LAYOUT    overlap/overflow/density/GUI scale
VISUAL    hierarchy/contrast/token/icon inconsistency
ASYNC     flicker/pending/retry/premature transition
PERF      repeated rebuild/layout thrash/render-cache churn
COPY      jargon/consequence/recovery clarity
ACCESS    keyboard/focus/non-hover alternative/readability
OWNERSHIP UI workaround masking backend/plugin/protocol/runtime defect
```

Fix the dominant class first. Do not rewrite an entire surface to solve one local failure.

## Lane A — Launcher Desktop procedure

```text
identify user decision/reproduction
→ trace frontend state to existing Tauri bridge/result
→ verify backend authority before changing presentation
→ audit loading/pending/error/empty/success lifecycle
→ reuse existing component/bridge/control
→ smallest presentation/lifecycle fix
→ keyboard/focus/responsive audit
→ inspect sibling Launcher surfaces using same result
→ source/typecheck proof
→ Launcher UI Preview artifact review when visual/state presentation changed
→ local desktop proof only for native/runtime boundaries
→ STOP
```

Desktop rules:

- Exactly one production frontend runtime bridge; do not create a parallel production API/facade for a UI fix.
- Deterministic visual-preview fixtures are test/proof infrastructure only and must never become product authority.
- Do not infer server/process/plugin/world truth in Svelte when backend already owns it.
- Important actions must work without hover-only discovery.
- Keep focus visible and keyboard order aligned with task order.
- Use bounded responsive panes; handle long names via deliberate truncation/wrapping, not accidental overflow.
- Long-running actions need local pending/progress feedback and duplicate-submit protection.
- Disabled controls need understandable context when the reason is actionable.
- Errors belong near the affected workflow and should offer a recovery path when possible.
- Preserve selection/search/filter/scroll/tab/server context when returning unless canonical data invalidated it.
- Destructive actions must be separated, target-specific, and confirmed when difficult to undo.
- UI confirmation never replaces backend process/filesystem safety.
- Compile/typecheck alone is insufficient for a visual/layout/state-presentation issue when Launcher UI Preview can render it.

## Lane B — Fabric Client Mods procedure

```text
identify player interaction/reproduction
→ map authoritative server/client state
→ verify target Minecraft/Fabric APIs when uncertain
→ audit screen lifecycle + keybind/back/close
→ audit renderer/cache invalidation when visual instability exists
→ reuse existing screen/keybind/controller/protocol
→ smallest presentation/state fix
→ avoid duplicate server/domain state
→ inspect sibling screens using same action
→ GUI-scale/input/failure-state audit
→ Fabric build/artifact proof
→ real Minecraft-rendered visual proof when available/applicable
→ Local-PC interaction proof only for remaining native/performance/input-feel boundary
→ STOP
```

Fabric rules:

- Server validates and owns mutations.
- New wire payload/validation belongs to `lazybuilder-protocol` first.
- Do not solve one mod UI issue by adding a cross-manager dependency without a proven architectural requirement.
- Presentation-only state must not become a second registry, world truth, plugin truth, or runtime truth.
- One physical input must not be handled by independent paths that can submit twice, close/reopen, or leave stale state.
- Prefer immediate response and stable frames over decorative animation.
- GUI-scale behavior is a correctness requirement, not optional polish.
- Canonical visual proof should use the real Minecraft client, production `Screen`, Minecraft font/widgets/textures, and exact mod artifact whenever technically practical.
- HTML recreations of Minecraft screens are never final visual proof.
- Deterministic test hooks may provide state or open a production screen, but may not duplicate product semantics.

### Map interaction lock

```text
M              open / close map
Esc            close current map surface
left drag      pan
scroll         zoom
recenter input return to player
right click    contextual map actions
```

Xaero may be used only as a familiarity/behavior reference. Do not copy source/assets/branding, create an adapter, runtime dependency, or second map authority merely to imitate it.

Map presentation must preserve one canonical map/cache path. Current managed world + dimension defines persistent map scope; never render a temporary fake/unmanaged scope first. Sidebar/hover/favorite/context state must not reset terrain renderer/cache state.

For Map visual changes, prefer Minecraft-rendered proof of representative states such as ready/current-world, sidebar expanded/collapsed when relevant, context menu, loading/resolving, and pending/error states. Use multiple GUI scales only when the issue depends on density/space.

### World Manager lock

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

Import and Export share one `WorldTransferScreen`. Pinned/Recent are client navigation preferences only, never server metadata or keep-loaded state.

Do not expose manual Load/Unload, `autoLoad`, converter implementation names, job IDs, protocol terminology, or internal folder identity as ordinary builder decisions.

## Lane C — Plugin-facing presentation procedure

```text
reproduce plugin-facing issue
→ fetch canonical plugin-management state/result
→ identify actual client-rendered surface
→ verify whether defect is semantic or presentational
→ if semantic: hand off narrowly to lazybuilder-plugin-management
→ if presentational: fix list/detail/action/status flow
→ align pending/error/success across sibling plugin surfaces
→ preserve plugin selection/filter/scroll where useful
→ choose Launcher proof, Minecraft proof, or simulated semantic preview correctly
→ STOP
```

Plugin-facing rules:

- Never calculate plugin compatibility/dependency/health independently in UI.
- Action availability comes from authoritative plugin state/capability, not button-local assumptions.
- List, detail, progress, confirmation, and notification views must not invent different success criteria.
- Pending install/update/remove/enable/disable actions must prevent conflicting duplicate requests.
- Warning copy should state the user consequence and recovery, not internal resolver jargon.
- A plugin row disappearing/reordering during an operation must not cause a late response to update the wrong selected plugin.
- Removal/disable/destructive flows must make the target explicit and preserve rollback/safety semantics owned by plugin management.
- For inventory/container, chat/component, title, bossbar, scoreboard, book, tab-list, or resource-pack presentation, native Minecraft client rendering is the visual authority.
- MiniMessage/Adventure/component previews may be used for fast semantic iteration only when explicitly labeled `SIMULATED PREVIEW`.
- If a Paper plugin feeds a LazyBuilder Fabric screen, visual proof belongs to the Fabric lane; server mutation semantics remain plugin/protocol authority.

## Visual Proof System

When appearance or state presentation matters, use:

```text
.agents/skills/lazybuilder-ui/references/visual-proof-system.md
```

The proof architecture is shared, not duplicated:

```text
VISUAL PROOF
├── Launcher renderer  → real Svelte/CSS + deterministic fixture
├── Fabric renderer    → real Minecraft client + production Screen
└── Plugin renderer    → Paper/plugin + real Minecraft client surface
```

Use the cheapest proof that can genuinely falsify the issue.

Proof levels:

```text
L0 source inspection
L1 build/typecheck/tests
L2 deterministic simulated preview
L3 real Launcher Svelte visual proof
L4 real Minecraft-rendered visual proof
L5 Local-PC native acceptance
```

Never claim a higher level than observed.

Visual artifacts must be tied to an exact commit and record renderer/scenario/viewport/GUI scale/proof boundary where relevant. Prefer GitHub Actions artifacts over committed PNG baselines.

Do not add pixel-diff databases, visual SaaS, golden-image repositories, full resolution matrices, or a second UI implementation without repeated evidence that simple commit-addressed screenshots are insufficient.

## Hierarchy and progressive disclosure

Build surfaces around the user's primary task, not around every capability.

- Prefer one primary action per local decision area.
- Keep primary actions near the object/content they affect.
- Use sidebar/drawer/secondary navigation for navigation, not as a dumping ground for primary actions.
- Reveal advanced/destructive actions only when context makes them relevant.
- Avoid permanent buttons for actions meaningful only after selection.
- Current/selected/pending/disabled states must be distinguishable without relying only on color.
- Duplicated labels/actions are a design smell; first ask whether one control can serve the workflow.

## Navigation and state preservation

Preserve when returning to a parent surface unless invalidated by canonical data:

```text
selection
search/filter
scroll position
active tab
map center/zoom
sidebar state
current workspace/server context
```

Leaving a screen is not cancellation unless the backend owns safe cancellation.

Never recreate/reset a heavy visual surface solely for hover, selection, context-menu visibility, favorite state, disabled state, or similar small presentation changes.

## Async lifecycle and race rules

Every user-triggered async action follows:

```text
idle
→ pending
→ success OR recoverable error
```

Required checks:

```text
pending begins before repeated dispatch is possible
same logical action cannot be submitted twice
success clears pending
error clears pending
reset/disconnect clears pending safely
late response cannot mutate a different selected entity
screen close does not silently cancel unsupported work
reopen does not replay the previous request
```

Keep valid previous content visible while replacement content is prepared when this prevents flicker and remains truthful. Do not clear usable state just to show loading.

Success should close/advance only when that matches user intent. Failure should keep a recoverable context whenever safe.

## Accessibility and input parity

Apply platform-appropriate accessibility.

- Important actions must not depend on hover alone.
- Desktop keyboard focus must remain visible and unobscured.
- Logical keyboard order should follow visual/task order.
- Icon-only controls need labels/tooltips where supported.
- Do not use color as the only state cue when text/icon/state cue is practical.
- Drag-essential interactions should have a non-drag alternative where feasible.
- Do not import mobile 44/48px rules into mouse/keyboard Fabric UI; instead use comfortably clickable hit areas consistent with Minecraft GUI scale.

## Responsive/layout matrix

Audit real supported extremes, not one screenshot.

Launcher:

```text
minimum supported window
normal laptop window
maximized/wide desktop
long server/plugin/world names
empty and large lists
loading/pending/error states
keyboard-only traversal
```

Fabric:

```text
common GUI scales
small game window
1080p-class fullscreen
wide/ultrawide
long labels
empty/max lists
sidebar open/collapsed
cold/warm map cache when relevant
```

Do not automatically capture every matrix combination. Choose representative constraints that can disprove the current issue.

Prefer adaptive bounded widths, truncation, stacking/collapse/overlay of secondary navigation, and preservation of primary interaction targets. Avoid horizontal scrolling for core management/navigation.

## Visual consistency

Use existing LazyBuilder tokens/components first.

- Keep semantic color meaning stable: accent/current/success/warning/danger/disabled.
- Use coherent icon/glyph styles at the same hierarchy level.
- Prefer clear text over decorative icons when text communicates better.
- Use a small spacing rhythm rather than unrelated one-off values.
- Avoid excessive uppercase headers and repeated chrome.
- Primary content should visually dominate navigation/toolbars.

## Motion and perceived performance

Motion must explain change or provide feedback; otherwise omit it.

- Avoid transitions that repeatedly recalculate layout.
- Respect reduced-motion expectations on Desktop for nonessential motion.
- Fabric prioritizes immediate input and stable frames.
- Stable previous content is preferable to partial rebuild/flicker when still truthful.
- Presentation state must not trigger heavy renderer/cache resets unless visual content actually changed.
- Avoid polling every render/tick when revision/event/push state already exists.

## Destructive/risky actions

For archive/delete/remove/reset/replace-like operations:

```text
target is explicit
consequence is user-readable
routine and destructive actions are separated
confirmation matches actual backend behavior
conflicting operations block the action
success appears only after authoritative success
failure leaves a recoverable context
```

UI safety is additive; it never substitutes for backend safety.

## Cross-surface regression rule

After fixing an operation/state in one surface, inspect sibling surfaces that consume the same canonical result.

Examples:

```text
Map teleport fixed
→ inspect World Manager teleport pending/error state

Plugin update pending fixed in detail
→ inspect plugin list/status/notification

Server delete error flow fixed
→ inspect Server Library confirmation/status/return flow

Client mod sync state fixed
→ inspect Client Setup required-mod list and repair/restart guidance
```

Do not duplicate fixes blindly. Align presentation around the same authoritative state.

## Anti-overdevelopment gate

Before adding a new UI abstraction, component family, screen, manager, service, persisted field, cache, router, background worker, compatibility layer, animation layer, preview renderer, fixture framework, or screenshot service, ask:

```text
Does this solve a reproduced user problem?
Is there already an owner or reusable surface?
Can lifecycle/state correction solve it instead?
Can this be local presentation state?
Does it reduce user decisions or merely add structure?
Will users notice a meaningful reliability/usability improvement?
Does it create synchronization burden or another source of truth?
Can the existing Visual Proof System prove it without new infrastructure?
```

If not clearly justified, do not add it.

## Pre-delivery UI audit

Before declaring a UI task complete, verify all applicable items.

### Safety / ownership
- correct semantic owner remained authoritative;
- no UI guess replaced backend/plugin/protocol/runtime truth;
- no second manager/cache/workflow/source of truth was introduced;
- destructive target/consequence are correct.

### Flow / navigation
- primary task is obvious;
- Back/Esc/toggle are predictable;
- returning preserves useful state;
- no duplicate/dead-end navigation;
- advanced actions are progressively disclosed.

### Interaction / async
- repeated click/key cannot duplicate work;
- keybind/screen handlers cannot race;
- pending/disabled/error states are truthful;
- transient menus dismiss predictably;
- no input leaks into another workflow;
- late response cannot update wrong selected entity.

### States
- loading, empty, pending, error, success, selected/current, unavailable states covered where applicable;
- error has recovery path when possible;
- stale/orphan client preference cannot masquerade as authoritative entity.

### Layout / accessibility
- supported window sizes or GUI scales checked at representative constraints;
- long labels and max-content state handled;
- no accidental overlap/horizontal overflow;
- focus/input alternatives checked where platform supports them;
- state does not depend on color/hover alone when avoidable.

### Performance / visual stability
- presentation-only changes do not trigger heavy resets;
- valid previous content retained during safe refresh where useful;
- no repeated full rebuild for minor state;
- no unnecessary polling/background work;
- tokens/icon/spacing hierarchy remain consistent.

### Research / proof
- uncertain platform behavior was verified against the correct authority when needed;
- visual issue was reviewed through the applicable real-renderer proof lane when available;
- artifact/commit identity is known for visual evidence;
- simulated preview is clearly labeled and not overstated;
- remaining native/live proof boundary is stated explicitly.

### Regression
- sibling surfaces using the same action/state were inspected;
- packaging/build/CI contract remains consistent;
- remaining local/live proof boundary is stated explicitly.

## Proof ladder

Use the cheapest proof that can falsify the change:

```text
L0 static source inspection
→ L1 unit/source-contract/typecheck/build
→ L2 deterministic simulated preview when useful
→ L3 real Launcher Svelte visual proof
→ L4 real Minecraft-rendered visual proof
→ L5 Local-PC native interaction/integration proof
```

Compilation proves source contracts, not visual/timing/input behavior.

Launcher visual/layout/state-presentation should use Launcher UI Preview before Local-PC testing when practical. Fabric screens and plugin-facing Minecraft surfaces should use the real Minecraft renderer when the automated proof path exists. Local PC remains the final boundary for native Windows/Tauri behavior, GPU/driver/frame pacing, mouse feel, real network timing, installed packaging, and other environment-specific behavior.

## Does not own

```text
workspace/server/process/provisioning/runtime → lazybuilder-desktop-runtime
third-party Paper plugin lifecycle           → lazybuilder-plugin-management
Paper world behavior/import/export           → lazybuilder-world-management
shared Paper/Fabric wire semantics           → lazybuilder-protocol
```

When the root cause belongs to one of these owners, identify it precisely, make the smallest handoff needed, then return to UI only to consume/present the corrected result.
