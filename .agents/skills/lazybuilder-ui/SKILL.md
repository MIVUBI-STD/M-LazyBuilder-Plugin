---
name: lazybuilder-ui
description: Own LazyBuilder presentation and user interaction across two explicit branches: Desktop UI (Tauri/Svelte) and Fabric Client UI. Use only the branch relevant to the current task. Apply UX quality gates for hierarchy, interaction predictability, accessibility, responsive density, state feedback, and visual consistency. Do not use for runtime, world, plugin, or shared protocol semantics.
---

# LazyBuilder UI

Own presentation and user interaction only. Follow `docs/04-system/development-discipline.md`, `docs/04-system/skill-routing.md`, and the relevant UI docs.

Choose exactly one branch for the current decision. Do not load Desktop and Fabric context together unless the request truly spans both presentation surfaces.

This skill is intentionally product-specific. General UI/UX references are inputs, not authority. Apply only rules that fit LazyBuilder's actual platform, task, and interaction model; never import web/mobile conventions blindly into Minecraft or desktop UI.

## Core objective

Every UI change should make the user's next decision clearer while preserving product ownership boundaries.

Prefer:

```text
fewer visible choices
+ predictable controls
+ explicit state feedback
+ preserved navigation state
+ responsive layout
+ stable rendering
```

over adding more surfaces, managers, menus, modes, or duplicated state.

## UI priority order

Audit in this order. Fix higher-priority failures before polishing lower-priority visuals.

1. **Interaction correctness** — controls do what users expect; no stuck, duplicate, or conflicting actions.
2. **State clarity** — loading, pending, success, error, disabled, empty, and current/selected states are visible and truthful.
3. **Navigation predictability** — Back/Esc/toggle behavior is consistent and preserves meaningful state.
4. **Information hierarchy** — primary task dominates; secondary actions are progressively disclosed.
5. **Accessibility and input parity** — keyboard/focus/readability do not depend on hover or drag alone.
6. **Responsive density** — layout survives supported window sizes, GUI scales, and long labels without overflow.
7. **Visual consistency** — spacing, typography, icon style, color semantics, and component hierarchy are coherent.
8. **Motion and perceived performance** — motion explains change and never causes jank or visual instability.
9. **Decorative polish** — only after the interaction model and state flow are stable.

## Preflight before changing UI

Before coding, answer these questions:

```text
What is the user's primary task on this surface?
What is the single most important next action?
Which state is authoritative, and where does it come from?
What existing screen/control/state can be reused?
What happens on loading, failure, retry, pending, empty, resize, and back/close?
Will this change preserve state when leaving and returning?
Does this introduce a second owner, second cache, second workflow, or duplicate control?
Can the same outcome be achieved with fewer visible controls or less new code?
```

If ownership or state authority is unclear, stop and route to the owning skill before adding UI-side state.

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
→ map authoritative backend state
→ audit hierarchy + failure/loading states
→ reuse existing bridge/control
→ remove redundant facade/state/control if no value
→ implement smallest presentation change
→ keyboard/focus/responsive audit
→ local desktop proof when interaction matters
→ STOP
```

Desktop-specific rules:

- Detect the real Svelte/Tauri structure before applying framework guidance; do not assume a web default stack.
- Preserve focus visibility and logical keyboard order for actionable controls.
- Do not rely on hover as the only way to discover or operate an important action.
- Avoid fixed layouts that break at supported window sizes; prefer bounded responsive panes and truncation over horizontal overflow.
- Keep destructive actions separated from common actions and require explicit confirmation when consequences are not trivially reversible.
- Do not disable a control without explaining why when the reason is user-actionable.
- Long-running actions need visible pending/progress state and duplicate-submit protection.
- Errors should appear near the affected workflow, use user-facing language, and provide a recovery path when one exists.
- Preserve scroll/selection/filter state when navigating to detail/confirmation surfaces and returning, unless resetting is the explicit product behavior.

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
identify primary player interaction
→ map authoritative server/client state
→ audit keybind/back/close and state preservation
→ reuse existing screen/keybind/map boundary
→ consume existing typed protocol
→ smallest presentation/state change
→ avoid duplicate server/domain state
→ GUI-scale + keyboard + failure-state audit
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

Map interaction should remain familiar and deterministic:

```text
M              open / close map
Esc            close current map surface
left drag      pan
scroll         zoom
recenter input return to player
right click    contextual map actions only
```

Do not let one physical input be handled by two independent paths in a way that can close then reopen, submit twice, or leave stale UI state.

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

## Hierarchy and progressive disclosure

Use the surface around the user's primary task, not around every available capability.

Rules:

- One primary action per local decision area where possible.
- Secondary navigation belongs in compact navigation/sidebar patterns; primary task actions should stay close to the object/content they affect.
- Reveal advanced or destructive actions only when context makes them relevant.
- Do not expose internal maintenance or expert-only options on the default path merely because the backend supports them.
- Avoid permanent buttons for actions that are meaningful only after selection.
- Current/selected/pending/disabled states must be visually distinguishable without relying on color alone where practical.
- Repeated labels and duplicated controls are a design smell; first ask whether one control can serve both contexts.

## Navigation and state preservation

Back/close behavior must be predictable and should preserve meaningful context.

Preserve when returning to a parent surface unless there is a strong product reason not to:

```text
selection
search/filter
scroll position
map center/zoom
sidebar state
current tab/workspace context
```

Leaving a screen is not cancellation unless the backend owns safe cancellation.

Never recreate or reset a heavy visual surface just to change small presentation state such as hover, selection, context-menu visibility, favorite status, or disabled state.

## Loading, feedback, and async actions

Every asynchronous interaction must have one clear lifecycle:

```text
idle
→ pending
→ success OR recoverable error
```

Rules:

- Prevent duplicate submission while the same action is pending.
- Pending feedback should be visible where the user initiated the action.
- Keep valid previous content visible while replacement content is being prepared when doing so avoids flicker and does not misrepresent state.
- Do not clear a usable view before new data is ready unless the old view would be misleading or unsafe.
- Failure should keep the user in a recoverable context when possible.
- Success may close or advance a surface only when that transition matches the user's intent.
- Empty state is not an error state; explain what the user can do next.
- Do not show backend jargon, raw exception vocabulary, IDs, internal paths, or protocol terminology unless the user explicitly needs diagnostics.

## Accessibility and input parity

Apply platform-appropriate accessibility, not mobile rules by default.

Shared requirements:

- Important actions must not depend on hover alone.
- Keyboard focus must remain visible and unobscured on Desktop UI.
- Logical keyboard order must follow visual/task order.
- Icon-only controls need an understandable accessible label/tooltip where the platform supports it.
- Text/background and state indicators need sufficient contrast for practical readability.
- Do not use color as the only indicator of error, success, selection, current state, or disabled state when a text/icon/state cue can be provided.
- If an author-controlled drag interaction is essential, provide a non-drag alternative where feasible for the platform.
- Do not import 44/48px mobile touch-target requirements into mouse/keyboard Fabric UI; instead ensure clickable hit areas are comfortably larger than their glyphs and consistent with Minecraft GUI scale.

## Responsive density and layout

The UI must survive the real range of supported sizes rather than one screenshot size.

Audit:

```text
minimum supported window / GUI scale
normal desktop size
wide / ultrawide layout
long translated/user-provided labels
empty and maximum-content states
sidebar expanded/collapsed states
```

Rules:

- Prefer adaptive bounded widths to one fixed width everywhere.
- Truncate long labels deliberately; do not let them widen navigation panes unpredictably.
- Avoid horizontal scrolling for core navigation and management flows.
- On constrained space, collapse, stack, or overlay secondary navigation before shrinking the primary content into unusability.
- Preserve interaction targets and hierarchy when compacting the layout.
- Density should follow task frequency: frequent navigation can be compact; destructive/settings workflows need more separation.

## Visual consistency

Use existing LazyBuilder UI tokens/components before inventing new visual language.

- Keep semantic colors stable: accent/current/success/warning/danger/disabled should not swap meaning between screens.
- Use one icon/glyph style per hierarchy level; avoid arbitrary mixtures of filled, outline, emoji, and text-symbol styles.
- Avoid decorative icons when plain text communicates the action more clearly.
- Spacing should follow a small reusable rhythm rather than one-off values everywhere.
- Use typography hierarchy sparingly; excessive uppercase section labels and repeated headers increase visual noise.
- Primary content should visually dominate chrome, toolbars, and navigation.

## Motion and perceived performance

Motion must explain change, preserve spatial continuity, or provide feedback. Otherwise omit it.

- Prefer transform/opacity-like lightweight transitions on Desktop where applicable; avoid animation that causes repeated layout recalculation.
- Do not animate every state transition with one universal duration.
- Respect reduced-motion expectations on Desktop when adding nonessential motion.
- Fabric UI should prioritize immediate input response and stable frames over decorative animation.
- A stable old frame is better than visible partial rebuilds, flashing placeholders, or repeated layout resets.
- Rendering/cache invalidation should follow actual content/viewport changes, not unrelated hover/sidebar/presentation state.

## Destructive and risky actions

For archive/delete/remove/reset/replace-like operations:

- visually separate destructive actions from routine navigation;
- make the target explicit;
- communicate the consequence in user terms;
- require confirmation when the operation is destructive or difficult to undo;
- disable/reject the action while conflicting operations are active;
- never imply success until the authoritative operation succeeds.

## Shared UI invariants

- UI presents state and requests actions; it is not trust/security/domain authority.
- A UI file does not make this Skill the semantic owner of backend behavior.
- Internal maintenance is not exposed as a user choice without real product value.
- Do not persist values that can be derived from canonical backend/server state.
- Do not add controls for unsupported behavior.
- Capability-dependent controls render only from authoritative capability data.
- Prefer typed bounded requests over command-string tunneling.
- Avoid polling when explicit refresh/event/push behavior is sufficient.
- Error/empty/loading states must be actionable and avoid backend jargon.
- Do not create a new manager/service/cache/event bus solely to organize presentation code when the existing owner can support the interaction cleanly.
- UI polish must not create a second semantic workflow for an operation already owned elsewhere.

## Branch-specific invariants

Desktop:

- exactly one frontend runtime bridge;
- backend specialists own workspace/runtime/plugin/world semantics;
- desktop validation improves feedback only;
- component-local state is appropriate only for presentation state that cannot be derived cheaply from authoritative data.

Fabric:

- server validates and owns mutations;
- if a feature needs a new payload or validation rule, `lazybuilder-protocol` owns that contract first;
- current-world truth comes from server-observed player world transitions;
- map-area export reuses the canonical Import / Export workspace and backend export service;
- no second client world registry, converter catalog, map cache, or parallel navigation authority;
- changing sidebar/context/hover/favorite state must not reset map renderer/cache state;
- current managed world + dimension defines the persistent map scope; do not render a temporary fake/unmanaged scope first.

## Anti-overdevelopment gate

Before introducing any new UI abstraction, manager, component family, screen, persisted field, cache, or animation layer, ask:

```text
Does this solve a reproduced user problem?
Is there already an owner or reusable surface?
Can this be expressed as local presentation state instead?
Does it reduce decisions or merely add structure?
Will users notice a meaningful improvement?
Does it create another source of truth or synchronization burden?
```

If the change does not clearly improve the user experience or reliability, do not add it.

## Pre-delivery UI audit

Before declaring a UI task complete, verify all applicable items:

### Flow
- primary task is obvious without documentation;
- back/close/toggle behavior is predictable;
- returning to parent preserves useful state;
- no duplicate or dead-end navigation path;
- advanced actions are progressively disclosed.

### Interaction
- repeated clicks/keypresses cannot duplicate an in-flight operation;
- disabled/pending states are truthful;
- clicking outside transient menus closes them where expected;
- no input leaks into an unrelated screen/workflow;
- keybind and screen handlers do not race each other.

### States
- loading, empty, pending, error, success, selected/current, and unavailable states are covered where applicable;
- errors provide a recovery path when possible;
- stale/orphan client preferences cannot masquerade as authoritative entities.

### Layout
- supported window sizes / GUI scales checked;
- long labels handled;
- no accidental overlap or horizontal overflow;
- primary content still dominates in compact mode.

### Visual
- color semantics and component styles are consistent;
- icons/glyphs are coherent and understandable;
- focus/selection/current/pending states are distinguishable;
- no unnecessary headers, toolbars, or decorative chrome.

### Performance
- presentation-only state does not trigger heavy renderer/cache resets;
- valid previous content is retained during safe refreshes to avoid flicker;
- no repeated full layout rebuild for minor interaction state;
- no unnecessary polling/background work was added.

### Ownership
- no duplicate server/domain state;
- no second manager/cache/workflow was introduced without a proven need;
- protocol/runtime semantics stayed with their owning skill.

If any applicable item is unproven, state the remaining proof boundary rather than declaring the task finished.

## Does not own

```text
workspace/server/process/provisioning/runtime → lazybuilder-desktop-runtime
third-party Paper plugin lifecycle           → lazybuilder-plugin-management
Paper world behavior/import/export           → lazybuilder-world-management
shared Paper/Fabric wire semantics           → lazybuilder-protocol
```

## Proof boundary

Static source proves routing/types, ownership, state flow, and intended interaction contracts. Rendered desktop behavior, keyboard/focus behavior, in-game layout across GUI scales, native file dialogs, map controls, actual protocol interoperability, animation smoothness, and large-transfer UX require local/client/live proof.

Do not call a UI issue resolved merely because the code compiles when the reported defect is visual, timing-dependent, input-dependent, or scale-dependent.
