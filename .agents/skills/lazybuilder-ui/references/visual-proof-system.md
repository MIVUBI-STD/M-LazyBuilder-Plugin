# LazyBuilder Visual Proof System

Use this reference whenever the accepted result depends on what a user actually sees, not only on source correctness.

The Visual Proof System is one proof architecture with multiple renderers. It is **not** a second Launcher, second Minecraft UI, second plugin layer, or screenshot product.

```text
LazyBuilder source
       ↓
select cheapest renderer that can prove the issue
       ↓
render deterministic representative state
       ↓
capture artifact tied to exact commit
       ↓
ChatGPT/human visual audit
       ↓
native Local-PC acceptance only where still required
```

## Proof lanes

### 1. Launcher Proof

Renderer:

```text
real Svelte components + real CSS
+ deterministic browser-only runtime fixture
+ Chromium / Playwright
```

Canonical reference:

```text
.agents/skills/lazybuilder-ui/references/launcher-visual-preview.md
```

Use for Launcher hierarchy, layout, state presentation, density, overflow, responsive behavior, and browser-level lifecycle proof.

### 2. Fabric Mod UI Proof

Target renderer:

```text
real Minecraft Java client
+ exact LazyBuilder Fabric mod artifact
+ controlled client/world/server state
+ actual Minecraft Screen/widget/font/texture rendering
```

Use for:

```text
WorldMapScreen
WorldManagerScreen
WorldTransferScreen
mod settings/diagnostic screens
keybind-opened screens
context menus/tooltips
GUI-scale regressions
Minecraft-specific text/layout/rendering
```

Do **not** rebuild a Fabric screen as HTML for canonical proof. A lightweight mock may be used only for rough ideation and must be labeled SIMULATED.

Target capture flow:

```text
build exact Fabric artifact
→ launch controlled Minecraft client
→ enter deterministic state/test world when required
→ open canonical production Screen
→ set representative viewport + GUI scale
→ capture PNG
→ publish commit-addressed artifact
→ ChatGPT/human review
```

Preferred representative matrix, only where materially relevant:

```text
1920×1080  GUI scale 2
1600×900   GUI scale 2 or 3
1366×768   GUI scale 2
```

Do not run every permutation by default. Add a matrix row only when the issue depends on available space, scaling, or density.

For Map Manager, capture at minimum when visual behavior changes:

```text
map ready/current world
sidebar expanded
sidebar collapsed when changed
favorites/all-worlds state when changed
context menu when changed
loading/resolving state when changed
error/pending teleport state when changed
```

The screenshot must come from the production screen class and production rendering path. Fixture/test hooks may provide deterministic data/state but must not become runtime authority.

### 3. Plugin-facing In-Game Proof

Paper plugins do not own an arbitrary client `Screen` unless a client mod participates. Select proof based on the actual surface.

#### Vanilla/client-rendered surfaces

Examples:

```text
inventory/container GUI
book
chat/component
bossbar
title/subtitle/action bar
scoreboard/sidebar
player/tab list
resource-pack-backed presentation
```

Target renderer:

```text
real Paper server/plugin artifact
+ real Minecraft client
+ deterministic test player/state
```

The server/plugin emits the real production payload/action and the Minecraft client renders it.

#### Plugin + LazyBuilder client-mod surface

If plugin/server state is presented by a LazyBuilder Fabric screen, proof belongs primarily to **Fabric Mod UI Proof**, while server mutation/validation remains plugin/protocol authority.

#### Semantic fast preview

A MiniMessage/Adventure/component preview may be generated without Minecraft for quick iteration, but label it:

```text
SIMULATED PREVIEW — not native Minecraft rendering proof
```

Do not use simulated glyph widths, colors, item rendering, container geometry, or resource-pack presentation as final acceptance evidence.

## Proof-level ladder

Choose the cheapest level that can actually disprove the bug.

| Level | Evidence | Suitable for | Not enough for |
|---|---|---|---|
| L0 | source inspection | ownership, intended flow, obvious contract defects | actual appearance |
| L1 | typecheck/build/tests | compile/API/contracts | visual correctness |
| L2 | deterministic simulated preview | early composition/copy/state ideation | native renderer claims |
| L3 | real Launcher Svelte preview | Launcher visual/layout/state presentation | native Tauri/Windows behavior |
| L4 | real Minecraft-rendered proof | Fabric screens and client-rendered plugin surfaces | GPU/driver/local interaction feel |
| L5 | Local-PC native acceptance | installed Windows/Tauri/Minecraft/runtime behavior | — |

Never report a higher proof level than was actually observed.

## Artifact contract

Every visual-proof artifact should identify:

```text
source branch
source commit SHA
surface/lane
renderer
Minecraft/Launcher version when relevant
viewport/resolution
GUI scale when relevant
fixture/scenario name
proof boundary
screenshots
```

Prefer one artifact per commit/proof lane rather than committed PNG baselines.

Suggested names:

```text
LazyBuilder-UI-Preview-<sha>
LazyBuilder-Minecraft-UI-Preview-<sha>
LazyBuilder-Plugin-UI-Preview-<sha>
```

Artifacts are evidence, not source authority.

## Deterministic-state discipline

A visual fixture/test hook may:

```text
select representative workspace/world/plugin state
return deterministic server/client data
open a production screen
position a test player/camera
set GUI scale/resolution
suppress nondeterministic animation/caret/time where safe
```

It must **not**:

```text
implement product semantics a second time
replace production screen/component code
invent capabilities not supported by runtime
change production state ownership
make production depend on test query parameters
hide an actual lifecycle/rendering bug to satisfy capture
```

If visual proof exposes a real UI defect, fix the production UI boundary first. Keep the assertion unless it was factually wrong.

## Screenshot scenarios

Capture scenarios, not decorative galleries. A scenario exists only if it proves a user decision or regression class.

Useful scenario classes:

```text
READY
LOADING / RESOLVING
EMPTY
PENDING ACTION
RECOVERABLE ERROR
DISABLED / PERMISSION-LIMITED
PROBLEM / CONFLICT
SMALL VIEWPORT / HIGH GUI SCALE
RETURNED STATE / PRESERVED CONTEXT
```

Avoid combinatorial state explosion. Prefer one representative scenario per meaningful UX contract.

## Review checklist

For every visual artifact, inspect:

```text
primary task obvious within first view
current/selected/pending/error states truthful
no overlap/clipping/unintended scroll
long labels bounded
primary and destructive actions visually separated
important action not hover-only
focus/keyboard target visible where applicable
back/close context understandable
empty/loading state not confused with failure
no stale/duplicate widgets
no flash/blank replacement when prior valid content exists
consistent tokens/spacing/type/icon language
screen still usable at captured constrained size/GUI scale
```

For map/renderer surfaces additionally inspect:

```text
terrain does not disappear because sidebar/context state changed
scope/current-world identity is stable
sidebar does not dominate map
player/current-world marker is legible
zoom/context overlays do not collide
loading state does not masquerade as empty map
```

## Automation policy

Automate only what repeatedly saves review time.

Allowed by default:

```text
commit-addressed screenshot artifacts
small deterministic scenario set
manifest generation
failure diagnostics
ChatGPT-readable artifact retrieval
```

Do not add by default:

```text
pixel-diff approval database
large golden-image repository
third-party visual SaaS
parallel UI implementation
full cross-product resolution matrix
video capture for static-layout issues
complex screenshot orchestration service
```

Add stronger automation only after repeated evidence proves current proof cannot catch the defect efficiently.

## Native acceptance boundary

Even L4 Minecraft screenshots do not fully prove:

```text
FPS/frame pacing
mouse feel/drag feel
GPU/driver-specific rendering
real network latency/races
OS DPI/window activation
installed modpack/resource-pack differences
native file dialogs/process/filesystem behavior
```

Use Local PC for those final boundaries, not for first discovery of obvious visual/layout problems.
