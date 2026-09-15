# LazyBuilder UI Issue Resolution Playbook

Use this reference only for a **reported UI defect/regression** where reproduction, state authority, input timing, or cross-surface behavior needs deeper diagnosis than the main `lazybuilder-ui/SKILL.md` procedure.

Goal: find the **first wrong boundary** and fix the smallest owner that can produce a reliable user-visible result.

## Triage sequence

```text
1. reproduce shortest failing flow
2. choose one dominant defect class
3. trace every visible fact/action to one authority
4. verify uncertain platform behavior only if material
5. identify first wrong boundary
6. make smallest complete fix
7. inspect sibling consumers of the same canonical result
8. choose proof level that can falsify the defect
9. state remaining native/live boundary
```

Do not start with redesign.

## Reproduction record

Capture only conditions that can change behavior:

```text
starting state
user input
visible intermediate state
expected result
actual result
window size / GUI scale when relevant
selected entity
connection/capability state
pending operation state
fresh vs returning surface
mouse vs keyboard/keybind path
```

If the issue cannot yet be reproduced or separated by evidence, narrow/instrument before rewriting UI.

## Defect classes

Use one primary class:

```text
FLOW       navigation/back/dead-end
STATE      stale/duplicated/misleading authority
INPUT      double-submit, focus, key/click race, event leak
LAYOUT     overlap/overflow/density/GUI-scale breakage
VISUAL     hierarchy/contrast/token/icon inconsistency
ASYNC      flicker/pending ambiguity/late response/retry
PERF       repeated rebuild/layout/render-cache churn
COPY       unclear consequence/recovery or internal jargon
ACCESS     keyboard/focus/readability/non-hover alternative
OWNERSHIP  UI workaround masking runtime/plugin/world/protocol defect
```

Fix P0/P1 interaction/state problems before polish.

## Authority trace

Every visible value/action should resolve to exactly one of:

```text
local presentation state
Desktop Runtime result/state
Paper/domain state
shared protocol result
persistent client preference
plugin-management result
```

Red flags:

```text
same fact persisted in multiple UI locations
backend fact copied into another durable frontend store
optimistic result remains authoritative after failure
late response mutates a newly selected entity
UI computes permissions/compatibility/readiness independently
presentation state invalidates heavy renderer/domain state unnecessarily
```

If the canonical result itself is wrong, stop UI editing and hand off to its semantic owner.

## Boundary examples

```text
button enabled although returned capability denies action
→ UI defect

required capability is absent from a shared payload
→ protocol/domain defect first

plugin dependency warning factually wrong
→ plugin-management first

Map terrain resets when sidebar/favorite changes
→ UI/render invalidation defect unless map scope actually changed

Launcher confirmation is clear but deletion target/path is unsafe
→ desktop-runtime first
```

Never compensate for missing/wrong backend semantics with hidden UI guesses.

## Async / input audit

For each user-triggered async action:

```text
pending starts before repeat dispatch
one logical action cannot run twice from independent handlers
success clears pending
error clears pending
reset/disconnect clears local pending safely
late response is bound to the original target identity
screen close does not imply cancellation unless owner supports it
reopen does not replay the prior request
```

When relevant check both:

```text
mouse click
Enter/Space
screen key handler
global keybind/event handler
```

One physical input must not be interpreted twice.

## Navigation / state preservation

Preserve useful local context unless canonical data invalidates it:

```text
selection
search/filter
scroll
active tab
map center/zoom
sidebar state
workspace/server context
```

Back/Esc/close must return where the user reasonably expects. Leaving a screen is not cancellation by default.

## Destructive-flow audit

For delete/remove/archive/reset/replace-like actions:

```text
target identity visible
consequence matches backend semantics
routine and destructive actions separated
conflicting work blocks the action
success shown only after authoritative success
failure leaves recoverable context
```

UI confirmation never substitutes for backend safety.

## Layout / rendering audit

Use only representative constraints that can expose the current defect.

Launcher examples:

```text
minimum supported window
normal laptop window
long labels / large list
loading/pending/error
keyboard path
```

Fabric examples:

```text
small window / common GUI scales
1080p-class fullscreen
wide layout when relevant
long labels / max list
sidebar open/collapsed
cold/warm map state when renderer is involved
```

Performance red flags:

```text
full rebuild on hover/selection
renderer/cache reset from unrelated presentation state
polling each frame/tick for event/revision-driven state
clearing valid content before replacement is ready
large allocations/sorting in hot render path
```

## Cross-surface regression

After fixing one presentation of a canonical action/result, inspect sibling consumers only where the same contract is used.

```text
teleport result → Map + World Manager
plugin update result → list + detail + notification
server lifecycle result → Library + confirmation/status surfaces
client-sync result → required-mod list + repair/restart guidance
```

Do not duplicate implementation; align presentation around the same authority.

## External research trigger

Read `ui-knowledge-source-policy.md` only when platform/API behavior is genuinely uncertain, version-sensitive, or a new interaction type has no established LazyBuilder pattern.

Do not browse for ordinary copy/spacing/state fixes that source already answers.

## Proof selection

Read `visual-proof-system.md` when appearance/state presentation is part of acceptance.

```text
L0 source inspection
L1 typecheck/build/tests
L2 simulated preview (explicitly simulated)
L3 real Launcher Svelte preview
L4 real Minecraft renderer
L5 Local-PC native interaction
```

Choose the cheapest level that can disprove the reported issue. Never claim a higher level than observed.

## Completion report

A UI defect is complete when you can state:

```text
reproduced cause
primary owner
smallest changed boundary
states/inputs checked
sibling consumers checked when relevant
proof level + renderer/commit when visual
remaining native/live boundary
```

Then STOP. Do not convert one defect into a broad UI redesign or framework project.