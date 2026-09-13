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

One concern gets one canonical semantic owner. A Skill must not duplicate another Skill's jobdesk merely because the same files or language are involved.

## Primary-Owner Rule

Select the owner by **the behavior being decided**, not by the file being edited.

```text
runtime/process/config decision     → desktop-runtime
Svelte/Tauri presentation decision  → desktop-ui
third-party plugin lifecycle rule   → plugin-management
world/Paper domain behavior         → world-management
Fabric/Xaero presentation decision  → client-ui
neutral Paper/Fabric wire contract  → protocol
owner/success criteria still unclear→ development-brief
```

Examples:

```text
Plugins.svelte only changes layout
→ desktop-ui

plugin dependency semantics change and UI message follows
→ plugin-management first
→ desktop-ui only if presentation also changes

world action requires a new shared payload
→ protocol owns payload
→ world-management consumes it

Desktop HTTP control changes
→ desktop-runtime
NOT protocol
```

## Specialist Set

Keep this set intentionally small. Add a new Skill only when repeated work proves a materially different reusable execution procedure.

```text
lazybuilder-development-brief
lazybuilder-desktop-runtime
lazybuilder-desktop-ui
lazybuilder-plugin-management
lazybuilder-world-management
lazybuilder-client-ui
lazybuilder-protocol
```

All specialist work follows `development-discipline.md`.

### `lazybuilder-development-brief`

**Jobdesk:** resolve genuine architecture/cross-owner ambiguity, success criteria, and execution partition.

It is a temporary routing owner only. Once the primary specialist is known, unload/stop using Development Brief and continue under that specialist.

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
local desktop HTTP/control bootstrap
runtime persistence/rollback
```

Does not own Svelte presentation, Paper world rules, Fabric UI, shared Minecraft wire contracts, or third-party plugin semantics.

### `lazybuilder-desktop-ui`

**Jobdesk:** Tauri/Svelte presentation and interaction.

Owns:

```text
workspace launcher UX
dashboard/settings/plugins/worlds presentation
loading/error/progress states
frontend request/result typing
single frontend Tauri bridge
```

It never becomes authority for runtime, world, plugin, or security policy.

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

Does not own bundled LazyBuilder core modules or generic desktop runtime configuration.

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

It never becomes world-state or protocol-contract authority.

### `lazybuilder-protocol`

**Jobdesk:** neutral shared Paper/Fabric contracts under `shared/protocol`.

Owns:

```text
request/result payloads
identifiers
wire validation/defaults
map-action contracts
shared transfer contracts
Paper/Fabric compatibility contracts
```

It does **not** own desktop loopback HTTP control, Paper implementation behavior, or UI presentation. If a `WorldControl*` type lives in `shared/protocol`, Protocol owns only its neutral wire semantics; the desktop HTTP bridge remains Desktop Runtime.

## Cross-Owner Handoff

Cross-domain work is sequential, not simultaneous ownership.

```text
Owner A changes/decides its contract
→ produce the smallest handoff artifact/result
→ Owner B consumes it
```

Do not keep multiple specialists active for the same decision. A caller/UI adapter follows the semantic owner rather than re-implementing its rule.

Typical handoffs:

```text
new world action + shared payload + Fabric button
→ protocol: payload
→ world-management: Paper/application behavior
→ client-ui: button/presentation

new desktop setting changes server runtime
→ desktop-runtime: semantics/config
→ desktop-ui: presentation

plugin dependency rule changes UI warning
→ plugin-management: rule/result
→ desktop-ui: display only
```

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

Do not create Skills for implementation languages alone (Rust, Java, TypeScript, Maven, Gradle).

## Completion Check

Before finishing a change, answer:

```text
Who is the primary semantic owner?
Did another owner duplicate that state/rule?
Was a second specialist loaded before ownership actually changed?
Did the change add a user decision, manager, cache, registry, router, config path, worker, or compatibility layer unnecessarily?
Can an existing path or deletion satisfy the same accepted result?
What is the cheapest proof that can falsify the result?
```

If ownership is unclear, use `lazybuilder-development-brief`. Otherwise finish under the exact specialist and STOP.
