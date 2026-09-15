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

One concern gets one canonical semantic owner. Select by the behavior being decided, not by edited file, implementation language, or where the symptom is visible.

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

There is no separate Visual Testing or Minecraft UI Preview Skill. **Visual proof, UI research discipline, and presentation acceptance belong to `lazybuilder-ui`** while semantic/runtime ownership remains with the domain owner.

There is no meta Development Brief Skill. Architecture/cross-owner ambiguity is resolved directly by `AGENTS.md` + `development-discipline.md` + this routing map, then work proceeds under one primary specialist.

## Primary-Owner Rule

```text
launcher architecture/Tauri core/jobs/settings/update/distribution → desktop-runtime
workspace/process/provisioning/runtime/config decision             → desktop-runtime
third-party Paper plugin lifecycle decision                        → plugin-management
world/Paper domain/files/conversion decision                       → world-management
presentation/input/UI issue decision                               → ui
visual proof / UI research / renderer acceptance                   → ui
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
plugin scan/identity/metadata/dependencies
install/update
enable/disable
safe remove
duplicate resolution
restart-required state
minimum rollback state
```

Does not own bundled LazyBuilder core modules or generic desktop runtime configuration. It owns plugin truth; `lazybuilder-ui` owns how that truth is presented and interacted with.

### `lazybuilder-world-management`

Owns:

```text
create/settings + BUILD_READY policy
ACTIVE/ARCHIVED durable lifecycle
automatic Paper load / idle unload coordination
archive/restore/backup/duplicate/delete
import inspection/review/discard
import/export/conversion
world registry/persistence
world operation leases/tasks
Paper world runtime gateway
world filesystem safety/publication
```

Loaded/unloaded are transient Paper runtime facts, not durable product lifecycle. Do not route manual Load/Unload or Clone product behavior back into this Skill; user-facing duplication is `Duplicate`.

### `lazybuilder-ui`

Owns presentation/input and visual proof across three lanes:

```text
Launcher Desktop
→ Tauri/Svelte Server Library / Plugin Manager / Client Setup / settings/world/runtime presentation

Fabric Client Mods
→ in-game UI/keybinds/map/world-navigation/import-export presentation

Plugin-facing presentation
→ plugin inventory/detail/status/warning/progress/error/confirmation and presentation-only in-game messages
```

UI owns interaction correctness, truthful state presentation, input races, navigation, accessibility, responsive density/GUI-scale behavior, visual consistency, perceived performance, and the applicable proof renderer.

UI never becomes runtime, updater, settings-persistence, plugin-lifecycle, world-state, protocol-contract, filesystem-safety, or security authority.

For non-trivial UI defects, identify the first wrong boundary and hand semantic defects back to `desktop-runtime`, `plugin-management`, `world-management`, or `protocol` rather than hiding them behind UI state.

Xaero may be used only as a familiarity/behavior reference. LazyBuilder owns its map implementation; do not add Xaero runtime dependency, adapter, copied source/assets, or a second map authority.

### `lazybuilder-protocol`

Owns neutral shared Paper/Fabric contracts under `shared/protocol`:

```text
request/result payloads
identifiers
wire validation/defaults
World Control V5
Map Action V2
shared transfer contracts
Paper/Fabric compatibility contracts
```

It does **not** own desktop loopback HTTP control, Tauri IPC/application operations, Paper implementation behavior, or UI presentation.

## Conflict Resolution

When a task touches several files, ask which semantic rule is changing.

Examples:

```text
Launcher Activity page layout only
→ ui / Launcher Desktop

whether an operation can cancel/retry and what survives restart
→ desktop-runtime
→ ui presents returned state

Launcher self-update signature/restart semantics
→ desktop-runtime

Update banner layout/copy
→ ui / Launcher Desktop

settings schema/default/migration/persistence
→ desktop-runtime

settings panel arrangement
→ ui

plugin dependency semantics + warning text
→ plugin-management owns rule/result
→ ui presents returned state

plugin update button submits twice
→ ui owns input/pending race
→ plugin-management still owns update semantics

world action needs a new shared payload
→ protocol owns payload
→ world-management consumes it

Fabric button for an existing action
→ ui / Fabric

Paper permission/result itself is wrong
→ owning plugin/domain Skill, not UI

Desktop HTTP control behavior
→ desktop-runtime
NOT protocol
```

If ownership is still ambiguous:

```text
state competing owners
→ apply development-discipline decision ladder
→ identify first wrong owner / smallest contract boundary
→ choose one primary specialist
→ continue implementation
```

Do not load a meta-skill for this step.

## Scenario Routing Probes

A symptom name is not an owner. Use the first evidence that can separate semantic truth from transport/adaptation/presentation.

### Plugin warning is wrong

```text
canonical dependency/compatibility/lifecycle result is wrong
→ plugin-management

canonical result is correct but wording/layout/severity/action presentation is wrong
→ ui
```

First probe: compare the canonical plugin lifecycle/capability result with the rendered warning. Do not start by editing the warning text if the underlying result is factually wrong.

### World import UI fails

```text
wire request/result/default/bounds or InspectImport/DiscardImport shape is wrong
→ protocol

wire contract is correct but inspection/validation/publication/cleanup/domain ownership is wrong
→ world-management

canonical import/review result is correct but pending/review/back-close/error presentation is wrong
→ ui
```

First probe: identify whether the failure appears in the neutral round trip, Paper/domain result, or only presentation. Load the next Skill only after the previous boundary is proven correct.

### Server backup progress is wrong

```text
operation phase/current/total/cancel/retry/result is wrong at Rust/runtime authority
→ desktop-runtime

runtime snapshot is correct but progress bar/text/disabled state is wrong
→ ui
```

First probe: inspect the canonical operation snapshot before touching Svelte progress logic.

### Map teleport button errors

```text
click/key race, duplicate dispatch, pending/disabled feedback is wrong
→ ui

Map Action payload/capability/validation semantics are wrong
→ protocol

payload is correct but Paper authorization/load/teleport domain behavior is wrong
→ world-management
```

First probe: distinguish duplicate/input behavior from wire mismatch from server-domain result.

### Launcher readiness looks stale

```text
Rust readiness/health result itself is stale or wrong
→ desktop-runtime

Rust result is correct but UI keeps/renders stale state
→ ui
```

First probe: compare the current authoritative readiness snapshot with the rendered surface.

### Plugin install/update succeeds on disk but fails after restart

```text
identity/dependency/restart-required/load semantics or Paper plugin runtime result is wrong
→ plugin-management
```

Do not route to desktop-runtime merely because a server restart is involved. Desktop owns the server process lifecycle; plugin-management owns whether the third-party plugin mutation is valid and what restart state it requires.

### Bundled LazyBuilder core is missing or incompatible

```text
World/Utilities core provisioning/synchronization/runtime packaging
→ desktop-runtime
```

Do not route bundled core through third-party plugin-management.

### Desktop HTTP world request behaves incorrectly

```text
auth/session/loopback routing/request envelope is wrong
→ desktop-runtime

transport is correct but world lifecycle/filesystem/domain result is wrong
→ world-management
```

Desktop may carry the request/result; it does not become World Manager semantic authority.

### New Fabric world capability

```text
new/changed shared payload is required
→ protocol
→ STOP after neutral contract proof

Paper/domain implementation of frozen payload
→ world-management
→ STOP after canonical domain proof

screen/control/presentation for proven result
→ ui
```

Never open all three Skills at once.

## Cross-Owner Handoff

Cross-domain work is **sequential, typed, and minimal**, never simultaneous ownership.

```text
Owner A decides/changes its semantic contract
→ prove Owner A's boundary
→ emit the smallest typed handoff artifact/result
→ STOP Owner A
→ Owner B consumes that artifact without recomputing Owner A truth
```

A handoff artifact contains only what the next owner needs. It is not permission for the next owner to reopen the previous owner's semantic decision.

### Canonical handoff payloads

```text
desktop-runtime → ui
canonical ids + runtime/readiness/operation state + capabilities + stable error/result
UI does not re-scan filesystem/process/runtime to derive the same truth

plugin-management → ui
canonical plugin identity + lifecycle state + capabilities + restart-required + stable result/error
UI does not parse JAR metadata/dependencies or decide compatibility independently

protocol → world-management
neutral types + version + validation/default/bounds/capability semantics
World Management implements domain behavior without redefining the wire contract

world-management → ui
canonical world identity/lifecycle/capabilities + operation result/error
UI does not derive lifecycle from folders, registry internals, or transient Paper load state

desktop-runtime ↔ world-management
Desktop owns authenticated loopback/process/provisioning envelope; World Management owns world semantics
Desktop may transport a world request/result but must not reinterpret world lifecycle/filesystem rules
```

### UI-originated defect handoff

When UI discovers the semantic owner is wrong, hand off only:

```text
short reproduction
expected vs actual
canonical input/result observed by UI
selected entity identity
proof that the defect survives beyond presentation
```

Then stop UI semantic patching until the owning Skill returns a corrected canonical result. UI resumes only to present that result.

### Protocol-first chain

For a new/changed Paper↔Fabric world capability:

```text
protocol
→ freeze neutral contract
→ world-management consumes/implements it
→ freeze canonical domain result
→ ui consumes/presents it
```

Do not keep Protocol and World Management active on the same semantic decision. Protocol owns **wire meaning**; World Management owns **domain meaning**.

Typical handoffs:

```text
launcher operation/update/settings semantic change
→ desktop-runtime
→ ui

new world action + payload + Fabric presentation
→ protocol
→ world-management
→ ui

plugin rule changes warning/action presentation
→ plugin-management
→ ui
```

Do not keep multiple specialists active for the same decision, and do not pass full source trees/history when one typed result is sufficient.

## No-Skill Owners

Some source areas have clear ownership but do not need a dedicated Skill.

```text
Utilities-Manager internals
→ exact source + canonical system/domain docs

build/version scripts
→ exact build/script owner + GITHUB_RULES.md

security-only repository policy
→ SECURITY.md / exact boundary
```

Do not create Skills for Rust, Java, TypeScript, Maven, Gradle, Tauri, Svelte, visual testing, Playwright, screenshots, or implementation mechanics alone. Framework/tool knowledge belongs inside the semantic Skill that owns the product behavior.

## Skill Creation Gate

A new Skill is justified only when all are true:

1. a new semantic responsibility exists;
2. its execution procedure materially differs from the five existing Skills;
3. work is repeated, not one-off;
4. merging it into an existing Skill would materially increase unrelated context;
5. its entry, exit, and handoff boundary can be stated clearly.

Otherwise route to an existing owner or a no-Skill source owner.

## Completion Check

Before finishing:

```text
Who is the primary semantic owner?
Did another owner duplicate that state/rule?
Was another Skill loaded before ownership changed?
Did the handoff carry only the minimum typed result needed by the next owner?
Did the next owner avoid recomputing the previous owner's truth?
Was owner selection based on separating evidence rather than symptom location/name?
Did the change introduce unnecessary manager/cache/registry/router/config/worker/dependency/compatibility/proof infrastructure?
Can an existing path or deletion satisfy the same accepted result?
What is the cheapest proof that can falsify the result?
Was simulated evidence separated from real-renderer/native evidence?
```

Finish under the exact specialist and STOP.