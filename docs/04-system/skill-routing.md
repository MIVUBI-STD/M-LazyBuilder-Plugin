# Skill Routing / Jobdesk Map

Canonical map for LazyBuilder execution ownership. This document defines **which specialist owns which class of decision**. Durable behavior remains owned by domain docs/source; shared development economy is owned by [`development-discipline.md`](development-discipline.md).

## Authority Roles

```text
AGENTS.md      = repository/task routing
Docs           = durable semantic contracts and ownership
Skills         = execution procedure for one specialist boundary
Source         = current implementation/runtime truth
Ops docs       = current continuation/proof only
Git history    = retired decisions/history
```

One concern gets one canonical semantic owner. Select by the behavior being decided, not by edited file or implementation language.

## Specialist Set

Keep this set intentionally small. Add a Skill only when repeated work proves a materially different reusable procedure that cannot fit an existing semantic owner.

```text
lazybuilder-desktop-runtime
lazybuilder-plugin-management
lazybuilder-world-management
lazybuilder-ui
lazybuilder-protocol
```

There is no meta Development Brief Skill. Architecture/cross-owner ambiguity is resolved directly by `AGENTS.md` + `development-discipline.md` + this routing map, then work proceeds under one primary specialist.

## Primary-Owner Rule

```text
workspace/process/provisioning/runtime/config decision → desktop-runtime
third-party Paper plugin lifecycle decision           → plugin-management
world/Paper domain/files/conversion decision          → world-management
presentation/input decision                           → ui
neutral Paper/Fabric wire contract                    → protocol
```

### `lazybuilder-desktop-runtime`

Owns:

```text
workspace lifecycle
server process ownership/recovery
managed Java
Paper provisioning/update
bundled core synchronization
resource/runtime configuration
startup safety
local desktop HTTP/control bootstrap
runtime persistence/rollback
```

Does not own UI presentation, world rules, shared Minecraft wire contracts, or third-party plugin semantics.

### `lazybuilder-plugin-management`

Owns:

```text
plugin scan/metadata/dependencies
install/update
enable/disable
safe remove
duplicate resolution
minimum rollback state
```

Does not own bundled LazyBuilder core modules or generic desktop runtime configuration.

### `lazybuilder-world-management`

Owns:

```text
create/load/unload/settings
BUILD_READY world policy
archive/restore/backup/clone/delete
import/export/conversion
world registry/persistence
world operation leases/tasks
Paper world runtime gateway
world filesystem safety
```

### `lazybuilder-ui`

Owns presentation/input only, with two explicit branches:

```text
Desktop branch
→ Tauri/Svelte launcher/dashboard/settings/plugins/worlds presentation
→ frontend request/result typing
→ single frontend Tauri bridge

Fabric branch
→ in-game UI/keybinds
→ fullscreen LazyBuilder map/world-navigation presentation
→ World Manager / Import-Export presentation
→ client-only navigation preferences and presentation state
```

Load only the branch relevant to the current task. UI never becomes runtime, plugin, world-state, protocol-contract, or security authority.

`lazybuilder-ui` also owns the UI quality gate for its surfaces: interaction predictability, state feedback, hierarchy, keyboard/focus where applicable, responsive density/GUI-scale behavior, visual consistency, and presentation-performance checks. These quality rules must not create a second semantic owner, cache, manager, workflow, or backend rule.

Xaero may be used only as a familiarity/behavior reference for map interaction. LazyBuilder owns its own map implementation; do not create a Xaero runtime dependency, adapter, copied asset/source path, or second map authority merely to imitate it.

### `lazybuilder-protocol`

Owns neutral shared Paper/Fabric contracts under `shared/protocol`:

```text
request/result payloads
identifiers
wire validation/defaults
map-action contracts
shared transfer contracts
Paper/Fabric compatibility contracts
```

It does **not** own desktop loopback HTTP control, Paper implementation behavior, or UI presentation.

## Conflict Resolution

When a task touches several files, ask which semantic rule is changing.

Examples:

```text
Plugins.svelte layout only
→ ui / Desktop branch

plugin dependency semantics + warning text
→ plugin-management owns rule/result
→ ui only displays returned state

world action needs a new shared payload
→ protocol owns payload
→ world-management consumes it

Fabric button for an existing action
→ ui / Fabric branch

Desktop HTTP control behavior
→ desktop-runtime
NOT protocol
```

If ownership is still ambiguous:

```text
state the competing owners
→ apply development-discipline decision ladder
→ identify first wrong owner / smallest contract boundary
→ choose one primary specialist
→ continue implementation
```

Do not load a meta-skill for this step.

## Cross-Owner Handoff

Cross-domain work is sequential, not simultaneous ownership.

```text
Owner A decides/changes its contract
→ produce the smallest handoff artifact/result
→ Owner B consumes it
```

Typical handoffs:

```text
new world action + payload + Fabric button
→ protocol
→ world-management
→ ui / Fabric branch

new desktop setting changes runtime
→ desktop-runtime
→ ui / Desktop branch

plugin rule changes desktop warning
→ plugin-management
→ ui / Desktop branch
```

Do not keep multiple specialists active for the same decision.

## No-Skill Owners

Some source areas have clear ownership but do not need a dedicated Skill.

```text
Utilities-Manager internals
→ docs/04-system/utilities-manager-architecture-lock.md
→ exact source owner

build/version scripts
→ exact build/script owner + GITHUB_RULES.md

security-only repository policy
→ SECURITY.md / exact boundary
```

Do not create Skills for Rust, Java, TypeScript, Maven, Gradle, or implementation mechanics alone.

## Skill Creation Gate

A new Skill is justified only when all are true:

1. a new semantic responsibility exists;
2. its execution procedure materially differs from the five existing Skills;
3. the work is repeated, not one-off;
4. merging it into an existing Skill would materially increase unrelated context;
5. its entry, exit, and handoff boundary can be stated clearly.

Otherwise route to an existing owner or a no-Skill source owner.

## Completion Check

Before finishing a change, answer:

```text
Who is the primary semantic owner?
Did another owner duplicate that state/rule?
Was another Skill loaded before ownership actually changed?
Did the change add a user decision, manager, cache, registry, router, config path, worker, dependency, or compatibility layer unnecessarily?
Can an existing path or deletion satisfy the same accepted result?
For UI work: were flow, pending/error states, back/close behavior, responsive/GUI-scale behavior, and input races audited?
What is the cheapest proof that can falsify the result?
```

Finish under the exact specialist and STOP.
