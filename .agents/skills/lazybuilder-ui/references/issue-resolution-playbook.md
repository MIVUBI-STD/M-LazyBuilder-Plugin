# LazyBuilder UI Issue Resolution Playbook

Use only for a **reported UI defect/regression** that needs deeper diagnosis than the main `lazybuilder-ui/SKILL.md` procedure. The Skill owns taxonomy, priority, proof mapping, and handoff; this reference adds diagnostic depth.

Goal: find the **first wrong boundary** and fix the smallest owner that can produce a reliable user-visible result.

## Triage sequence

```text
reproduce shortest failing flow
→ capture only behavior-changing conditions
→ trace visible facts/actions to authorities
→ identify first wrong boundary
→ make smallest complete fix
→ inspect sibling consumers only when they share the same canonical result
→ use matching proof
→ STOP
```

Do not start with redesign.

## Reproduction record

Capture only what can change behavior:

```text
starting state
user input
visible intermediate state
expected vs actual
window size / GUI scale when relevant
selected entity identity
connection/capability state
pending operation state
fresh vs returning surface
mouse vs keyboard/keybind path
```

If evidence cannot yet separate the defect, instrument/narrow before rewriting UI.

## Authority trace

Every visible value/action should resolve to one authority:

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
UI recomputes permissions/compatibility/readiness
presentation state invalidates heavy renderer/domain state unnecessarily
```

If the canonical result itself is wrong, stop UI editing and hand off to its semantic owner.

## Boundary examples

```text
button enabled although returned capability denies action
→ UI

required capability absent from shared payload
→ Protocol/domain first

plugin dependency warning factually wrong
→ Plugin Management first

Map terrain resets when sidebar/favorite changes
→ UI/render invalidation unless map scope actually changed

Launcher confirmation is clear but deletion target/path is unsafe
→ Desktop Runtime first
```

Never compensate for missing/wrong backend semantics with hidden UI guesses.

## Async / input audit

For each user-triggered async action:

```text
pending starts before repeat dispatch
one logical action cannot run twice through independent handlers
success/error clears pending
reset/disconnect clears local pending safely
late response remains bound to original target identity
screen close does not imply unsupported cancellation
reopen does not replay the prior request
```

When relevant check mouse, Enter/Space, screen key handlers, and global keybind/event handlers. One physical input must not be interpreted twice.

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

Back/Esc/close should return to the meaningful parent/context. Leaving a screen is not cancellation by default.

## Destructive-flow audit

For delete/remove/archive/reset/replace-like actions:

```text
target identity visible
consequence matches backend semantics
routine and destructive actions separated
conflicting work blocks action
success appears only after authoritative success
failure leaves recoverable context
```

UI confirmation never substitutes for backend safety.

## Layout / rendering audit

Inspect only constraints capable of exposing the reported defect.

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
representative GUI scales
1080p-class fullscreen
wide layout only when relevant
long labels / max list
sidebar open/collapsed
cold/warm map state when renderer is involved
```

Performance red flags:

```text
full rebuild on hover/selection
renderer/cache reset from unrelated presentation state
polling every frame/tick for event/revision-driven state
clearing valid content before replacement is ready
large allocations/sorting in a hot render path
```

## Cross-surface regression

Inspect sibling consumers only when they consume the same canonical action/result, for example:

```text
teleport result → Map + World Manager
plugin update result → list + detail + notification
server lifecycle result → Library + confirmation/status surfaces
```

Align presentation around one authority; do not duplicate implementation.

## External knowledge trigger

Use `ui-knowledge-source-policy.md` only when platform/API behavior is genuinely uncertain, version-sensitive, or a new interaction type lacks an established LazyBuilder pattern.

For ordinary copy/spacing/state fixes already answered by source, stay inside the repo.

## Completion

A diagnosed UI defect is ready to close when you can state:

```text
reproduced cause
first wrong owner
smallest changed boundary
relevant states/inputs checked
sibling consumers checked when applicable
matching proof + remaining native/live residue
```

Then STOP. Do not convert one defect into a broad UI redesign or framework project.