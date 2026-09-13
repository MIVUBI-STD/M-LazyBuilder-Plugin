# Skill Routing / Jobdesk Map

Canonical map for LazyBuilder execution ownership. This document defines **which specialist owns which class of change**. It does not duplicate domain behavior already owned elsewhere.

## Authority Roles

```text
AGENTS.md      = repository/task routing
Docs           = durable semantic contracts and ownership
Skills         = execution procedure for one specialist boundary
Source         = current implementation/runtime truth
Ops docs       = current continuation/proof only
Git history    = retired decisions/history
```

One concern gets one canonical semantic owner. Skills link to that owner instead of copying its rules.

## Specialist Set

Keep this set intentionally small. Add a new Skill only when a responsibility has a materially different execution procedure and cannot be routed cleanly to an existing owner.

```text
lazybuilder-development-brief
lazybuilder-desktop-runtime
lazybuilder-desktop-ui
lazybuilder-plugin-management
lazybuilder-world-management
lazybuilder-client-ui
lazybuilder-protocol
```

### `lazybuilder-development-brief`

**Jobdesk:** resolve genuine architecture/cross-owner ambiguity, success criteria, and execution partition.

Use only until the exact specialist can be selected. It is not a super-owner and should not stay loaded after ambiguity is resolved.

### `lazybuilder-desktop-runtime`

**Jobdesk:** desktop runtime authority.

Owns:

```text
workspace lifecycle
server process ownership/recovery
managed Java
Paper provisioning/update
bundled core synchronization
resource/runtime configuration
startup safety
local desktop control bootstrap
runtime persistence/rollback
```

Does not own Svelte presentation, Paper world rules, Fabric client UI, or third-party plugin semantics.

### `lazybuilder-desktop-ui`

**Jobdesk:** Tauri/Svelte presentation and user interaction.

Owns:

```text
workspace launcher UX
dashboard/settings/plugins/worlds presentation
loading/error/progress states
frontend request/result typing
single frontend Tauri bridge
```

Backend/domain policy remains with its specialist.

### `lazybuilder-plugin-management`

**Jobdesk:** third-party Paper plugin lifecycle managed by LazyBuilder desktop.

Owns:

```text
scan/metadata/dependencies
install/update
enable/disable
safe remove
duplicate resolution
minimum rollback state
```

Does not own bundled LazyBuilder core modules.

### `lazybuilder-world-management`

**Jobdesk:** World Manager domain and Paper world behavior.

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

### `lazybuilder-client-ui`

**Jobdesk:** Fabric client presentation and Xaero integration.

Owns:

```text
in-game UI
keybinds
client presentation state
Xaero map/location integration
client-side interaction flow
```

It never becomes world-state authority.

### `lazybuilder-protocol`

**Jobdesk:** neutral shared Paper/Fabric contracts under `shared/protocol`.

Owns:

```text
request/result payloads
identifiers
wire validation/defaults
map-action protocol
world-control protocol
transfer protocol
Paper/Fabric compatibility contract
```

It does not own Paper implementation logic or UI presentation.

## Fast Routing

```text
architecture owner unclear
→ lazybuilder-development-brief
→ choose exactly one specialist when resolved

workspace/create/open/adopt/provision/start/stop/restart
→ lazybuilder-desktop-runtime

Svelte desktop page/control/bridge UX
→ lazybuilder-desktop-ui

Paper plugin install/update/remove/dependency/duplicate
→ lazybuilder-plugin-management

world lifecycle/settings/file operation/conversion
→ lazybuilder-world-management

Fabric keybind/screen/Xaero/client UX
→ lazybuilder-client-ui

shared Paper/Fabric payload/wire behavior
→ lazybuilder-protocol
```

## Cross-Owner Handoff Rules

A task may cross boundaries, but ownership changes explicitly.

Examples:

```text
new world action needs protocol + Paper behavior + Fabric button
→ protocol owns payload
→ world-management owns server behavior
→ client-ui owns button/presentation

new desktop setting changes server runtime
→ desktop-runtime owns semantics/config
→ desktop-ui owns presentation only

plugin dependency rule changes UI warning
→ plugin-management owns rule/result
→ desktop-ui displays returned state
```

Do not keep multiple specialists active when one owner can finish the current decision.

## No-Skill Owners

Some source areas have clear ownership but do not need a dedicated Skill yet.

```text
Utilities-Manager internals
→ docs/04-system/utilities-manager-architecture-lock.md
→ exact source owner

build/version scripts
→ exact build/script owner + GITHUB_RULES.md

security-only repository policy
→ SECURITY.md / exact boundary
```

Create a specialist only if repeated work proves a distinct reusable execution procedure exists.

## Minimum-Flow Rules

- one config concern → one config owner;
- one process → one process owner/recovery path;
- one user action → one primary command/runtime path;
- one wire contract → one shared neutral source;
- UI adapters do not own business rules;
- internal maintenance is automatic unless it represents a real user decision;
- do not create compatibility layers for unsupported consumers;
- do not create routing Skills for implementation languages alone;
- stop loading sibling docs/Skills once the current owner is known.

## Completion Check

Before finishing a change, answer:

```text
Who owns the changed behavior?
Did another owner duplicate that state/rule?
Did the change add a new user decision unnecessarily?
Did it add a new manager/cache/registry/router/config path?
Can one layer be removed without losing accepted behavior?
What is the cheapest proof that can falsify the result?
```

If ownership is still unclear, return to `lazybuilder-development-brief`. Otherwise finish under the exact specialist and STOP.
