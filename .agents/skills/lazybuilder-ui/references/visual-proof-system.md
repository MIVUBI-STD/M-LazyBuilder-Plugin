# LazyBuilder Visual Proof System

Use this reference when acceptance depends on **what the user actually sees**, not only source correctness.

This is one proof architecture with multiple real renderers. It is not a second UI implementation, screenshot product, or semantic owner.

## Proof ladder

Choose the cheapest level that can genuinely falsify the claim. The L0–L5 labels are UI shorthand over the canonical proof vocabulary in `docs/04-system/development-discipline.md`.

| Level | Canonical proof type | Evidence | Proves | Does not prove |
|---|---|---|---|---|
| L0 | `STATIC_SOURCE` | source inspection | ownership / intended flow | appearance |
| L1 | `EXECUTED_SOURCE` | typecheck/build/tests | source/API/contracts | visual correctness |
| L2 | `VISUAL_SIMULATED` | deterministic simulated preview | early composition/state ideation | native renderer behavior |
| L3 | `VISUAL_RENDERED` | real Launcher Svelte/CSS preview | Launcher layout/state presentation | native Windows/Tauri behavior |
| L4 | `VISUAL_RENDERED` | real Minecraft client rendering | Fabric/plugin-facing Minecraft appearance | semantic client-server runtime or local GPU/input/network feel |
| L5 | `NATIVE_ACCEPTANCE` | Local-PC native acceptance | installed native interaction/environment boundary | unrelated semantic/runtime paths not exercised |

Never report a higher level or stronger canonical proof type than observed.

`VISUAL_RENDERED` is not automatically `LIVE_RUNTIME`. A real Minecraft screenshot proves what the production renderer displayed for that scenario; protocol ordering, Paper authorization, world mutation, plugin lifecycle, filesystem safety, and networking require their own matching semantic proof.

## Renderer selection

### Launcher Desktop

Use:

```text
real Svelte components + real CSS
+ deterministic preview runtime fixture
+ browser capture
```

Read `launcher-visual-preview.md` for the concrete artifact/review flow.

Suitable for:

```text
hierarchy
layout/density
wrapping/overflow
responsive behavior
loading/ready/problem/empty state presentation
browser-level UI lifecycle
```

### Fabric UI

Target:

```text
real Minecraft Java client
+ exact LazyBuilder Fabric artifact
+ production Screen/rendering path
+ deterministic representative state
```

Suitable for:

```text
WorldMapScreen
WorldManager/Transfer screens
context menus/tooltips
GUI-scale behavior
Minecraft font/widget/texture rendering
```

HTML recreation is never canonical Fabric proof.

### Plugin-facing Minecraft UI

For chat/components, bossbar, title, book, scoreboard, tab list, inventory/container or resource-pack-backed presentation:

```text
real Paper/plugin payload
+ real Minecraft client renderer
```

If a LazyBuilder Fabric screen presents the state, use the Fabric lane. A MiniMessage/component preview may be used for iteration only when labeled `SIMULATED PREVIEW`.

## Deterministic fixture rule

A fixture/test hook may:

```text
select representative state
return deterministic data
open a production screen/component
set viewport / GUI scale
suppress nondeterministic animation where safe
```

It must not:

```text
implement product semantics again
replace production components/screens
invent unsupported capabilities
become runtime authority
hide a real lifecycle/rendering defect
make production depend on proof-only parameters
```

## Artifact contract

Visual evidence should identify:

```text
branch
commit SHA
surface/lane
renderer
scenario
viewport/resolution
GUI scale when relevant
product/Minecraft version when relevant
proof boundary
screenshots
```

Artifacts are evidence, not source authority. Prefer commit-addressed CI artifacts over committed golden PNG repositories.

Canonical naming:

```text
LazyBuilder-UI-Preview-<sha>
LazyBuilder-Minecraft-UI-Preview-<sha>
LazyBuilder-Plugin-UI-Preview-<sha>
```

## Scenario selection

Capture only scenarios that prove a user decision or regression class:

```text
READY
LOADING / RESOLVING
EMPTY
PENDING
RECOVERABLE ERROR
DISABLED / PERMISSION-LIMITED
CONFLICT / PROBLEM
SMALL VIEWPORT / HIGH GUI SCALE
RETURNED / PRESERVED CONTEXT
```

Avoid combinatorial screenshot matrices. Add another viewport/state only when the issue depends on it.

For Map changes, inspect only affected representative states such as ready/current-world, sidebar open/collapsed, context menu, pending/error, or constrained GUI scale.

## Review checklist

For applicable visual artifacts, check:

```text
primary task/action is obvious
current/selected/pending/error states are truthful
no overlap/clipping/unintended horizontal overflow
long labels are bounded
primary and destructive actions are distinct
important action is not hover-only
back/close context is understandable
loading is not confused with empty/failure
no stale/duplicate widgets
valid previous content does not flicker away unnecessarily
spacing/type/icon/color semantics are coherent
constrained size/GUI scale remains usable
```

For map/renderer surfaces additionally check:

```text
terrain scope remains stable
sidebar/context state does not reset map cache
current world/player marker stays legible
overlays do not collide
loading does not masquerade as empty map
```

## Chat/review delivery

When the user is reviewing a visual change and exact-commit L3/L4 evidence exists, show a representative screenshot directly when the interaction surface supports it. One image is normally enough unless more are requested.

Do not present:

```text
workflow green status
artifact name
ZIP link
source description
```

as a substitute for actual visual evidence. If visual proof is unavailable, state that boundary explicitly.

Never use a stale screenshot from an older changed surface as proof of a newer revision.

## Automation boundary

Allowed by default:

```text
small deterministic scenario set
commit-addressed screenshots
manifest metadata
failure diagnostics
artifact retrieval for review
```

Do not add without repeated evidence:

```text
pixel-diff approval database
golden-image repository
third-party visual SaaS
parallel UI implementation
full resolution/state cross-product
video capture for static layout defects
separate screenshot orchestration service
```

## Native boundary

Even L4 screenshots do not prove:

```text
FPS/frame pacing
mouse/drag feel
GPU/driver differences
real network races
OS DPI/window activation
native dialogs
installed modpack/resource-pack interactions
```

Use `NATIVE_ACCEPTANCE` for those boundaries. Use `LIVE_RUNTIME` separately when the claim is about actual Paper/Fabric/client-server semantics rather than appearance.

## Stop rule

Once the selected renderer has falsified or confirmed the requested visual claim at the necessary level, stop. Do not expand the proof matrix merely because more states can be captured.