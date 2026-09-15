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

There is no separate Launcher Framework Skill. **Launcher application engineering is part of `lazybuilder-desktop-runtime`**; presentation remains `lazybuilder-ui`.

There is no meta Development Brief Skill. Architecture/cross-owner ambiguity is resolved directly by `AGENTS.md` + `development-discipline.md` + this routing map, then work proceeds under one primary specialist.

## Primary-Owner Rule

```text
launcher architecture/Tauri core/jobs/settings/update/distribution → desktop-runtime
workspace/process/provisioning/runtime/config decision             → desktop-runtime
third-party Paper plugin lifecycle decision                        → plugin-management
world/Paper domain/files/conversion decision                       → world-management
presentation/input/UI issue decision                               → ui
neutral Paper/Fabric wire contract                                 → protocol
```

### `lazybuilder-desktop-runtime`

Owns Launcher engineering plus desktop runtime semantics:

```text
Tauri/Rust application architecture and command orchestration
launcher lifecycle/startup/shutdown/restart semantics
long-running operation/job state semantics
launcher settings persistence/schema migrations
launcher self-update/update-channel semantics
Windows app identity, bundle/NSIS/distribution semantics
diagnostics/support-data semantics
workspace/server-library lifecycle
server process ownership/recovery
managed Java
Paper provisioning/update
bundled core synchronization
resource/runtime configuration
startup safety
local desktop HTTP/control bootstrap
runtime persistence/rollback
server duplicate/remove/delete/backup/restore runtime semantics
```

Does not own visual presentation, world rules, shared Minecraft wire contracts, or third-party plugin semantics.

The key boundary is:

```text
what state/operation exists, who owns it, how it persists/recovers → desktop-runtime
how that state/operation is rendered and interacted with          → ui
```

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

Does not own bundled LazyBuilder core modules or generic desktop runtime configuration. It owns plugin truth; `lazybuilder-ui` owns how that truth is presented and interacted with.

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

Owns presentation/input and UI issue resolution across three lanes:

```text
Launcher Desktop lane
→ Tauri/Svelte launcher/dashboard/settings/plugins/worlds presentation
→ Server Library / Plugin Manager / Client Setup presentation
→ frontend request/result typing
→ single frontend Tauri bridge
→ visual progress/activity/health/settings presentation from canonical backend state

Fabric Mods lane
→ in-game UI/keybinds
→ fullscreen LazyBuilder map/world-navigation presentation
→ World Manager / Import-Export presentation
→ client-only navigation preferences and presentation state

Plugin-facing presentation lane
→ plugin inventory/list/detail/status presentation
→ plugin warning/progress/error/confirmation UX
→ action availability derived from canonical plugin state/capability
→ user-facing in-game plugin messages when presentation-only
```

UI never becomes runtime, launcher-operation, updater, settings-persistence, plugin-lifecycle, world-state, protocol-contract, filesystem-safety, or security authority.

`lazybuilder-ui` owns the UI quality gate for these surfaces: interaction predictability, state authority/feedback, hierarchy, keyboard/focus where applicable, responsive density/GUI-scale behavior, visual consistency, presentation-performance checks, async race prevention, destructive-flow clarity, and cross-surface regression checks.

For non-trivial UI defects, use the issue-resolution playbook inside the UI skill. The UI specialist must identify the first wrong boundary and hand semantic defects back to `desktop-runtime`, `plugin-management`, `world-management`, or `protocol` rather than hiding them behind UI state.

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

It does **not** own desktop loopback HTTP control, Tauri IPC/application operations, Paper implementation behavior, or UI presentation.

## Conflict Resolution

When a task touches several files, ask which semantic rule is changing.

Examples:

```text
Launcher Activity page layout only
→ ui / Launcher Desktop lane

whether an operation can cancel/retry and what survives restart
→ desktop-runtime
→ ui displays returned operation state

Launcher self-update flow/signature/restart semantics
→ desktop-runtime

Update available banner layout/copy
→ ui / Launcher Desktop lane

Settings panel arrangement
→ ui

settings schema/default/migration/persistence
→ desktop-runtime

NSIS shortcut/install/upgrade semantics
→ desktop-runtime

Plugins.svelte layout only
→ ui / Launcher Desktop lane

plugin dependency semantics + warning text
→ plugin-management owns rule/result
→ ui / plugin-facing lane displays returned state

plugin update button submits twice
→ ui owns input/pending race
→ plugin-management remains owner of update semantics

Client Setup required-mod list is confusing
→ ui / Launcher Desktop lane

whether a mod is actually required/compatible
→ owning runtime/product contract, not UI

world action needs a new shared payload
→ protocol owns payload
→ world-management consumes it

Fabric button for an existing action
→ ui / Fabric Mods lane

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
launcher operation/update/settings semantic change
→ desktop-runtime
→ ui / Launcher Desktop lane

new world action + payload + Fabric button
→ protocol
→ world-management
→ ui / Fabric Mods lane

new desktop setting changes runtime
→ desktop-runtime
→ ui / Launcher Desktop lane

plugin rule changes desktop warning
→ plugin-management
→ ui / plugin-facing lane
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

Do not create Skills for Rust, Java, TypeScript, Maven, Gradle, Tauri, Svelte, or implementation mechanics alone. Framework knowledge belongs inside the semantic Skill that owns the product behavior.

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
For Launcher engineering: are durable owner, state machine, persistence, restart, retry/cancel, rollback/recovery, diagnostics, and package proof defined?
For UI work: were flow, state authority, pending/error states, back/close behavior, responsive/GUI-scale behavior, input races, and sibling surfaces audited?
Was a plugin/mod/runtime semantic issue accidentally patched only in presentation?
What is the cheapest proof that can falsify the result?
```

Finish under the exact specialist and STOP.
