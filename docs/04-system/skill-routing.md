# Skill Routing / Jobdesk Map

Canonical map for LazyBuilder specialist selection and cross-owner handoff. Durable behavior belongs to domain docs/source; minimum-flow, global failure taxonomy, proof vocabulary, temporal rules, and STOP discipline belong to [`development-discipline.md`](development-discipline.md).

## Authority roles

```text
AGENTS.md      = repository/task routing + capability/context gate
skill-routing  = specialist selection + sequential handoff + Skill maintenance
Skills         = execution procedure inside one specialist boundary
Docs           = durable semantic contracts
Source         = current implementation/runtime truth
Ops docs       = current continuation/proof only
Git history    = retired decisions/history
```

These routing rules are consumer-neutral: ChatGPT and Codex select the same owner/handoff chain. Tool availability changes execution mechanics, never semantic ownership.

## Canonical specialist set

```text
SEMANTIC / PRODUCT SPECIALISTS
lazybuilder-desktop-runtime
lazybuilder-plugin-management
lazybuilder-world-management
lazybuilder-ui
lazybuilder-protocol

IMPLEMENTATION SPECIALISTS
lazybuilder-paper-plugin-development
lazybuilder-fabric-mod-development
```

The first five decide product/domain/wire/presentation semantics. The final two implement LazyBuilder-owned Minecraft platform code after any required semantic contract is frozen.

Do not create additional Skills merely for Java, Rust, TypeScript, Maven, Gradle, Tauri, Svelte, testing, CI, screenshots, or another implementation mechanic.

## Primary owner map

```text
launcher architecture / Tauri core / operations / settings / update / Windows distribution
workspace / process / provisioning / Java / Paper runtime / bundled LazyBuilder core
→ lazybuilder-desktop-runtime

third-party Paper plugin identity / dependency / compatibility / lifecycle / rollback / restart requirement
→ lazybuilder-plugin-management

world lifecycle / registry / Paper world behavior / filesystem / import-export-conversion semantics
→ lazybuilder-world-management

presentation / interaction / input / navigation / layout / accessibility / visual proof
→ lazybuilder-ui

neutral Paper↔Fabric request-result / identifiers / validation / versioning / transfer framing
→ lazybuilder-protocol

LazyBuilder-owned Paper plugin implementation under plugins/**
→ lazybuilder-paper-plugin-development

LazyBuilder-owned Fabric mod implementation under mods/**
→ lazybuilder-fabric-mod-development
```

Key exclusions:

```text
desktop loopback HTTP/Tauri IPC        → desktop-runtime, not protocol
bundled World/Utilities core lifecycle  → desktop-runtime, not plugin-management
third-party Paper plugin lifecycle      → plugin-management, not paper-plugin-development
Paper/world semantic behavior           → world-management, not paper-plugin-development
shared Paper↔Fabric meaning             → protocol, not Paper/Fabric implementation Skills
presentation/interaction semantics      → ui, not fabric-mod-development
Paper/Fabric implementation mechanics   → corresponding implementation Skill after semantic contract is known
```

## Semantic vs implementation routing

Use this decision order:

```text
Does the request change what the product/domain/wire/UI contract means?
├─ yes → load ONE semantic/product Skill first
│        → decide + prove contract
│        → typed handoff
│        → STOP semantic Skill
│        → load Paper or Fabric implementation Skill only if implementation remains
└─ no  → if it is internal Paper/Fabric implementation work,
         load the matching implementation Skill directly
```

Examples:

```text
"add a new World capability used by Fabric"
→ protocol and/or world semantics first as required
→ paper-plugin-development / fabric-mod-development sequentially

"fix command listener duplicate registration"
→ paper-plugin-development directly if semantic result is unchanged

"change how the map screen behaves on Back/Esc"
→ ui first
→ fabric-mod-development only for implementation after UI contract is frozen

"fix Fabric Loom/resource packaging"
→ fabric-mod-development directly

"install/update a third-party Paper plugin"
→ plugin-management, never paper-plugin-development
```

One semantic decision still has one active owner. Implementation specialists do not stay active while a semantic owner is unresolved.

## Activation and discovery cues

Natural-language wording is a candidate trigger, never ownership proof.

```text
lazybuilder-desktop-runtime
candidate: launcher/server process/workspace/provisioning/readiness/settings/update/Windows/desktop HTTP
reject: Paper world semantics, third-party plugin lifecycle, shared Paper↔Fabric contract, presentation-only issue

lazybuilder-plugin-management
candidate: third-party plugin missing/duplicate/version/dependency/install/update/remove/enable/disable/restart/rollback
reject: bundled LazyBuilder core, internal plugin coding, presentation-only issue

lazybuilder-world-management
candidate: world load/teleport/archive/restore/duplicate/delete/import/export/conversion/registry/ACTIVE-ARCHIVED
reject: server process, UI-only state, shared payload meaning, pure Paper implementation mechanics

lazybuilder-ui
candidate: button/input/focus/back-close/loading/error/layout/GUI-scale/accessibility/visual/map interaction
reject: canonical semantic result itself is wrong

lazybuilder-protocol
candidate: payload/request-result/default/bounds/identifier/capability/version/World Control/Map Action/transfer framing
reject: desktop HTTP, Paper domain behavior, presentation, adapter-only defect

lazybuilder-paper-plugin-development
candidate: create internal Paper plugin, command, listener, service wiring, Paper API adapter, scheduler/threading, plugin resources, Maven/shading, Paper adapter implementation
reject: third-party plugin lifecycle, world/product meaning, shared wire meaning, presentation

lazybuilder-fabric-mod-development
candidate: create Fabric mod, initializer, keybind, Screen/controller/render adapter, Fabric networking adapter, resources, Loom/Gradle, mixin/API integration
reject: UI semantic decision, shared wire meaning, world semantics, Launcher runtime
```

Rules:

- negative/rejection cues outrank superficial keyword matches;
- feature words such as `Plugin`, `Mod`, `Import`, `Teleport`, `Map`, `Restart`, or `Update` never decide ownership alone;
- if semantic and implementation Skills both seem plausible, ask which boundary is actually wrong using the smallest separating evidence;
- ChatGPT and Codex use the same cues.

## Owner selection rule

A symptom is not an owner.

```text
symptom
→ inspect canonical semantic result/contract
→ is semantic truth wrong?
   yes → semantic/product Skill
   no  → inspect platform adapter/implementation/presentation
→ choose ONE first wrong owner
```

If evidence cannot distinguish owners, use global `UNKNOWN` and name the next separating evidence. Do not load multiple Skills just in case.

## Fast routing matrix

Use only when evidence matches directly.

| Observed evidence | Primary owner | Next action |
|---|---|---|
| Rust/runtime canonical state wrong | desktop-runtime | fix runtime truth; matching Desktop proof |
| Runtime result correct, Launcher presentation wrong | ui | fix presentation only |
| Third-party plugin lifecycle truth wrong | plugin-management | fix lifecycle truth |
| Internal Paper plugin command/listener/API wiring wrong; semantics already correct | paper-plugin-development | fix Paper implementation |
| Shared Paper↔Fabric contract meaning disagrees | protocol | fix/freeze neutral contract first |
| Protocol correct, Paper-side adapter stale/wrong | paper-plugin-development | implement frozen contract |
| Protocol correct, Fabric-side adapter stale/wrong | fabric-mod-development | implement frozen contract |
| World lifecycle/registry/filesystem truth wrong | world-management | fix world semantics |
| World semantics correct, Paper world adapter implementation wrong | paper-plugin-development | fix adapter mechanics |
| Canonical result correct, Fabric screen/input behavior wrong | ui first if interaction meaning changes; otherwise fabric-mod-development | freeze UI contract if needed, then implement |
| Fabric Gradle/Loom/resources/initializer defect | fabric-mod-development | fix module/toolchain implementation |
| Paper Maven/resources/bootstrap defect | paper-plugin-development | fix module/toolchain implementation |
| Desktop HTTP envelope wrong | desktop-runtime | fix desktop transport |
| UI duplicate dispatch around otherwise-correct action | ui | fix presentation/input path |

The table is a shortcut, not a second authority. If no row fits exactly, return to owner selection.

## Common sequential flows

### New Paper feature with existing semantics

```text
known semantic contract
→ paper-plugin-development
→ source/build proof
→ LIVE_RUNTIME residue only if actual Paper behavior must be exercised
```

### New cross-platform Paper↔Fabric capability

```text
protocol
→ freeze/prove neutral contract
→ STOP

world-management or other semantic owner when domain behavior is required
→ freeze/prove domain result
→ STOP

paper-plugin-development
→ implement Paper adapter
→ prove implementation boundary
→ STOP

fabric-mod-development
→ implement Fabric adapter
→ prove implementation boundary
→ STOP

ui when presentation semantics are required
→ decide UI contract before/independently of Fabric mechanics as appropriate
```

Never keep all specialists active on one decision.

### UI behavior in Fabric

```text
canonical backend result correct?
no  → route to semantic owner

yes, interaction/presentation contract unclear/wrong
→ ui
→ freeze expected interaction/state behavior
→ STOP
→ fabric-mod-development implements it
```

### Third-party plugin issue

```text
installed third-party plugin lifecycle
→ plugin-management
```

Do not route to Paper Plugin Development unless the artifact is LazyBuilder-owned source being implemented in this repository.

## Cross-owner handoff

Cross-domain work is sequential, typed, and minimal.

```text
Owner A decides/changes its contract
→ prove Owner A boundary
→ emit minimum typed handoff
→ STOP Owner A
→ Owner B consumes without recomputing Owner A truth
```

Canonical payloads:

```text
desktop-runtime → ui
canonical ids + runtime/readiness/operation state + capabilities + stable result/error

plugin-management → ui
canonical plugin identity + lifecycle/capability + restart-required + stable result/error

protocol → paper-plugin-development / fabric-mod-development
neutral types + identifiers + version + validation/default/bounds/capability semantics

world-management → paper-plugin-development
world-domain intent + lifecycle/filesystem/runtime constraints + canonical result/error

ui → fabric-mod-development
expected interaction/state contract + production surface/controller target + accessibility/input constraints + proof requirement

paper-plugin-development → ui
proven canonical result/capability/error surfaced by the internal plugin

fabric-mod-development → ui
production Screen/controller implementation constraints + canonical result consumed

desktop-runtime ↔ world-management
Desktop supplies authenticated process/provisioning envelope; World Management supplies world semantic result
```

The receiving owner must not rescan/recalculate the previous owner's truth merely because it can access the same files/state.

### UI-originated semantic defect packet

```text
short reproduction
expected vs actual
selected canonical entity id
canonical input/result observed by UI
evidence that defect survives beyond presentation
```

Then stop semantic UI mutation until the owning Skill returns a corrected canonical result.

## No-Skill owners

Some work remains source-owned without a specialist:

```text
build/version scripts not specific to Paper/Fabric module semantics → exact script owner + GITHUB_RULES.md
security-only policy                                           → SECURITY.md / exact boundary
ordinary language/framework mechanics                          → exact source + existing specialist context
```

Do not create Skills for programming languages or build tools alone. Paper/Fabric development Skills exist because they combine repeated platform lifecycle, API, packaging, runtime, and proof procedures.

## Skill maintenance / change triggers

Skills are operational contracts, not changelogs.

### MUST update

```text
semantic responsibility / owner boundary
entry trigger or exclusion
reusable canonical procedure
stable invariant / safety rule
required proof boundary
cross-owner handoff payload/sequencing
CURRENT_CONTRACT version/required payload/capability semantics
routed reference purpose/path
Paper/Fabric implementation procedure when the repository platform/toolchain model materially changes
```

Repeated execution failure caused by the instruction itself is a `SKILL_INSTRUCTION` trigger.

### MAY update

```text
IMPLEMENTATION_SNAPSHOT intentionally documented by a Skill/reference
external pattern/provenance after an adopted rule materially changes
clarification removing recurring ambiguity without duplicating authority
new recurring failure mode changing diagnosis/proof selection
```

### DO NOT update

```text
ordinary bug fix inside existing semantics/procedure
internal refactor with same owner/contract
additional regression test for an existing rule
private symbol rename not referenced by the Skill
performance optimization preserving behavior/proof boundary
one-off implementation detail
commit/status/history information
```

### Sync matrix

```text
semantic owner/routing changed
→ skill-routing.md + affected Skill + durable domain doc if semantics changed

CURRENT_CONTRACT changed
→ source + canonical contract doc + affected semantic Skill + direct adapters/tests

Paper implementation procedure/toolchain boundary changed materially
→ paper-plugin-development + only directly affected routing/docs

Fabric implementation procedure/toolchain boundary changed materially
→ fabric-mod-development + only directly affected routing/docs

stable invariant/proof/handoff changed
→ affected Skill + only references made wrong

IMPLEMENTATION_SNAPSHOT changed
→ exact Skill/reference snapshot only when intentionally documented
```

Maintenance is semantic/procedural, not file-based.

## Skill creation gate

A new Skill is justified only when all are true:

1. a distinct repeated responsibility exists;
2. its execution procedure materially differs from the seven existing Skills;
3. work is repeated, not one-off;
4. merging it into an existing Skill would materially increase unrelated context or blur authority;
5. entry, exit, and handoff can be stated clearly.

Otherwise use an existing Skill or an exact source owner.

## Routing completion check

Before leaving routing:

```text
Is there exactly one first wrong owner?
Is this a semantic decision or an implementation decision?
If semantic, has that contract been frozen before platform implementation begins?
Was selection based on separating evidence rather than filename/language/symptom location?
If ownership changes, is the typed handoff minimal?
Can the receiver proceed without recomputing the previous owner's truth?
```

Then continue under the selected specialist. Global proof/STOP rules remain owned by `development-discipline.md`.