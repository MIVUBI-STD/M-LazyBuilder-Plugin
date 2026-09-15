# LazyBuilder UI Issue Resolution Playbook

Use this reference only when diagnosing or fixing a UI/UX defect, regression, confusing flow, visual inconsistency, stale state, input race, or presentation-performance problem.

The goal is to find the **first wrong boundary** and fix the smallest owner that can produce a reliable user-visible result. Do not patch symptoms in presentation if the authoritative defect belongs elsewhere.

## Surface map

LazyBuilder UI issues normally enter through one of three lanes.

### Lane 1 — Launcher Desktop

Typical surfaces:

```text
apps/launcher/src/**
Tauri/Svelte Server Library
Plugin Manager presentation
Client Setup / mod synchronization presentation
runtime/settings/world-management presentation
confirmation/progress/error flows
```

UI owns:

```text
layout
navigation
labels and hierarchy
loading/pending/error/success/empty presentation
focus and keyboard behavior
frontend-only selection/filter/panel state
request dispatch through the existing bridge
```

UI does not own:

```text
server process safety
workspace filesystem safety
plugin lifecycle rules
world semantics
installation/provisioning policy
```

If the defect persists with the UI removed and the same backend request is issued directly, route to the backend owner.

### Lane 2 — Fabric Mods

Typical surfaces:

```text
mods/map-manager/** client screens/keybinds/map presentation
mods/utility-manager/** client convenience presentation
mods/performance-manager/** passive diagnostics/policy presentation when one exists
```

UI owns:

```text
screen lifecycle
keybind routing
map/sidebar/context interaction
client-only preferences
loading/pending/error states
GUI-scale layout
presentation/render invalidation policy
```

UI does not own:

```text
server authority
world mutation validation
wire semantics
Paper implementation
cross-manager performance ownership
```

Do not solve a map problem by adding a dependency on Performance Manager, Utility Manager, Xaero, or a second map authority unless there is a separately proven architectural requirement.

### Lane 3 — Plugin-facing UI

Paper plugins normally expose user experience through Launcher surfaces, in-game text/commands, status messages, permissions, action results, and vanilla/client-rendered surfaces rather than a native desktop widget tree.

UI owns:

```text
how plugin state/result is presented
plugin inventory/list/detail layout
warnings and compatibility presentation
confirmation/progress/error wording
button/action availability derived from authoritative capabilities
in-game user-facing status/message clarity where presentation-only
```

Plugin Management owns:

```text
scan/metadata/dependency rules
install/update/remove/enable/disable semantics
compatibility and duplicate resolution
rollback state
```

Do not infer plugin health, compatibility, or dependency truth from visual state. Present the canonical result returned by the plugin owner.

For inventory/container, chat/component, title, bossbar, scoreboard, book, player-list/tab, or resource-pack presentation, the Minecraft client is the visual rendering authority.

## Mandatory triage sequence

Do not start with styling. Run this sequence:

```text
1. Reproduce
2. Classify
3. Trace state authority
4. Verify uncertain platform behavior when needed
5. Find first wrong boundary
6. Minimize fix scope
7. Implement
8. Regression audit
9. Select proof renderer/level
10. State remaining native/live proof boundary
```

### 1. Reproduce

Write the shortest reproducible user flow:

```text
starting state
→ user input
→ visible intermediate state
→ expected result
→ actual result
```

Capture all conditions that could change UI behavior:

```text
window size / GUI scale
selected server/world/plugin
connection state
permissions/capabilities
pending operations
fresh launch vs returning screen
first load vs cached load
mouse vs keyboard path
single click vs repeated input
```

If the issue cannot be reproduced in source or runtime evidence, do not invent a broad rewrite. Instrument or narrow the hypothesis first.

### 2. Classify

Use one primary defect class:

```text
FLOW        wrong screen order, dead end, broken Back/close
STATE       stale, missing, duplicated, misleading, wrong authority
INPUT       key/click race, double-submit, focus, drag, event leakage
LAYOUT      overlap, overflow, density, GUI-scale/window-size breakage
VISUAL      hierarchy, contrast, inconsistent tokens/icons/spacing
ASYNC       flicker, pending ambiguity, premature close, retry failure
PERF        jank, repeated rebuild, layout thrash, render/cache churn
COPY        jargon, unclear consequence, weak recovery guidance
ACCESS      keyboard/focus/readability/non-hover alternative
OWNERSHIP   UI workaround masking backend/protocol/plugin/runtime defect
```

Fix the dominant class first. Do not redesign the whole surface because one local class failed.

### 3. Trace state authority

For each visible value/action, identify exactly one source:

```text
local presentation state
backend runtime state
Paper/server state
shared protocol response
persistent client preference
```

Red flags:

```text
same fact stored in two UI locations
derived backend fact persisted again in frontend
optimistic state shown as authoritative after failure
screen-local copy survives after canonical entity is deleted
UI decides permissions/compatibility independently
```

### 4. Verify uncertain platform behavior

Use external research only when the answer materially affects implementation.

Read:

```text
.agents/skills/lazybuilder-ui/references/ui-knowledge-source-policy.md
```

Typical triggers:

```text
Screen/widget lifecycle unclear
Minecraft/Fabric version/API changed
Paper/Adventure native surface unclear
focus/key input behavior uncertain
new UI type has no established LazyBuilder pattern
repeated defect suggests current pattern is wrong
```

Do not browse for ordinary spacing/copy work when repo conventions already answer the question.

### 5. Find the first wrong boundary

Examples:

```text
button says enabled but server capability says denied
→ UI defect if authoritative capability was available but ignored
→ protocol/backend defect if capability was never provided and is required

Plugin dependency warning is factually wrong
→ plugin-management owns the rule
→ UI owns only its presentation

Map flickers when favorite changes
→ UI/render lifecycle defect if favorite state invalidates renderer
→ map cache defect only if cache itself changes scope/content incorrectly

Launcher delete confirmation is clear but deletion path is unsafe
→ desktop-runtime defect, not UI
```

Never compensate for a wrong backend contract by adding hidden UI guesses.

## Issue severity

Prioritize by user harm, not visual annoyance.

### P0

```text
wrong destructive target
UI implies success when operation failed
input race performs duplicate/destructive action
stale state can operate on wrong server/world/plugin
screen trap prevents safe exit
UI exposes action that bypasses required safety contract
```

### P1

```text
broken primary flow
repeated flicker/jank affecting normal work
Back/close loses meaningful work state
pending action can be resubmitted
critical error has no recovery path
important action unreachable at supported size/GUI scale
```

### P2

```text
secondary layout inconsistency
minor copy/icon/spacing issue
non-blocking polish
rare edge state with safe fallback
```

Resolve P0/P1 before visual polish.

## Cross-surface synchronization rules

When one operation is visible in several surfaces, keep one semantic owner and align presentation around it.

Examples:

```text
Plugin install/update
plugin-management result
→ Launcher list status
→ detail action state
→ notification/progress copy

World teleport
server/protocol result
→ Map quick action
→ World Manager action
→ pending/error feedback

Client mod synchronization
Launcher runtime/Client Setup owner
→ required-mod list
→ install/repair status
→ restart/relaunch guidance
```

Do not implement separate success criteria on each surface.

## Async and race audit

For every user-triggered async action, verify:

```text
one request in flight per logical action
pending state starts before dispatch can be repeated
success clears pending
error clears pending
reset/disconnect clears pending safely
late response cannot mutate the wrong selected entity
screen close does not silently cancel unless backend supports cancellation
reopen does not replay the previous action
```

Check both input paths when relevant:

```text
mouse click
keyboard shortcut
Enter/Space activation
screen-level key handler
global keybinding/event handler
```

One physical input must not be interpreted twice by independent handlers.

## Navigation-state audit

For parent/detail/confirmation flows, preserve where useful:

```text
selected entity
search query
filters
scroll position
active tab
map center/zoom
sidebar collapsed state
workspace/server context
```

Reset only when canonical data invalidates the state.

Back/Esc/close must answer one question consistently: **where does the user expect to return?**

## Destructive-flow audit

Before archive/delete/remove/replace/reset-like actions:

```text
target name is visible
consequence is explicit
routine and destructive actions are visually separated
confirmation wording matches actual backend behavior
action is unavailable while conflicting work is active
success is shown only after authoritative success
error leaves a recoverable context
```

UI confirmation is not a substitute for backend safety checks.

## Layout matrix

Audit representative constraints that can disprove the issue, not every theoretical permutation.

### Launcher Desktop

```text
minimum supported window
normal laptop window
maximized desktop
long server/plugin/world names
empty list
large list
loading/pending/error states
keyboard-only traversal
```

### Fabric UI

```text
common GUI scales
small game window
1080p-class fullscreen
wide/ultrawide
long labels
empty / maximum list
sidebar open/collapsed
map with cache cold/warm
```

Avoid screenshot-specific coordinates when adaptive layout can express the same hierarchy.

## Rendering/performance audit

Presentation changes must not trigger heavy work unless the visual content actually requires it.

Check for:

```text
full screen/widget rebuild on hover/selection
renderer invalidation from unrelated sidebar state
cache clear on transient identity/loading state
polling every frame/tick for state available by revision/event
list re-sorting/re-filtering unnecessarily during render
large allocations in hot render paths
clearing valid content before replacement is ready
```

Prefer coalesced/atomic visible updates over incremental flicker.

## Copy and status rules

User-facing status should answer:

```text
What is happening?
What object is affected?
Can I do anything now?
What should I do if it fails?
```

Prefer:

```text
Updating plugin…
Teleporting…
Could not load worlds. Retry.
Server must be stopped before deletion.
```

over internal terms such as raw exception names, protocol IDs, job IDs, implementation class names, internal paths, or converter brands.

## Minimal-fix discipline

Before adding code, prefer in this order:

```text
reuse existing state/result
→ correct lifecycle handling
→ correct layout/hierarchy
→ remove redundant control
→ add tiny presentation state
→ extend existing component/screen
→ only then consider a new component abstraction
```

Do not add a manager, service, cache, event bus, router, persistence layer, background worker, duplicate screen, preview implementation, or screenshot service solely because the current file is large.

## Required regression scan

After a fix, search for sibling surfaces using the same action/state.

Examples:

```text
Teleport fixed in Map
→ inspect World Manager teleport state

Plugin pending state fixed in detail
→ inspect plugin list/status/header

Server lifecycle error copy fixed
→ inspect duplicate/remove/delete confirmation and toast/status surfaces
```

Do not duplicate the same fix blindly. Confirm whether siblings consume the same canonical state and only align presentation where needed.

## Visual proof selection

Read:

```text
.agents/skills/lazybuilder-ui/references/visual-proof-system.md
```

Choose proof based on the surface:

```text
Launcher visual/layout/state issue
→ real Svelte/CSS Launcher UI Preview artifact

Fabric Screen / map / GUI-scale issue
→ real Minecraft client + production Screen when automated proof exists

Paper plugin inventory/chat/title/bossbar/book/scoreboard/tab/resource-pack issue
→ real plugin/server payload + real Minecraft client

MiniMessage/component semantic iteration only
→ SIMULATED PREVIEW, clearly labeled
```

A mock HTML reproduction of Minecraft UI is not native proof.

Every visual proof should be tied to the exact commit/scenario and record viewport/GUI scale where relevant.

## Proof ladder

Use the cheapest proof capable of falsifying the issue:

```text
L0 static source inspection
→ L1 unit/source-contract/typecheck/build
→ L2 deterministic simulated preview when useful
→ L3 real Launcher Svelte visual proof
→ L4 real Minecraft-rendered visual proof
→ L5 Local-PC native interaction/integration proof
```

Compilation proves syntax/contracts, not interaction quality. A simulated preview proves only what its renderer actually models.

For Launcher visual/layout/state presentation, inspect Launcher UI Preview before Local-PC acceptance when practical. For Fabric or plugin-facing Minecraft surfaces, use the real Minecraft renderer when that proof path exists. Local PC remains required for environment-specific timing, GPU/driver/frame pacing, mouse feel, OS DPI/windowing, native dialogs, installed packaging, real network conditions, and other behavior not captured by deterministic proof.

Never claim a higher proof level than observed.

## Completion report

A UI issue is complete only when the report can state:

```text
reproduced cause
primary owner
smallest changed boundary
platform/source research used, if any
states/inputs audited
sibling surfaces checked
no duplicate source of truth introduced
build/CI proof status
visual proof renderer + commit/scenario, when applicable
remaining Local-PC/native proof boundary, if any
```
