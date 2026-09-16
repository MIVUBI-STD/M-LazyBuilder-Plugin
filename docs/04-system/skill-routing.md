# Skill Routing / Jobdesk Map

Canonical map for LazyBuilder specialist selection and cross-owner handoff. Durable behavior belongs to domain docs/source; minimum-flow, global failure taxonomy, proof vocabulary, and STOP discipline belong to [`development-discipline.md`](development-discipline.md).

## Authority roles

```text
AGENTS.md      = repository/task routing
skill-routing  = specialist selection + cross-owner handoff
Skills         = execution procedure inside one specialist boundary
Docs           = durable semantic contracts
Source         = current implementation/runtime truth
Ops docs       = current continuation/proof only
Git history    = retired decisions/history
```

These routing rules are consumer-neutral: ChatGPT and Codex select the same semantic owner and handoff chain. Tool availability may change execution mechanics, never ownership.

Select by the semantic decision, not by implementation language, edited file, screen/page name, restart involvement, consumer, or where the symptom is visible.

## Canonical specialist set

```text
lazybuilder-desktop-runtime
lazybuilder-plugin-management
lazybuilder-world-management
lazybuilder-ui
lazybuilder-protocol
```

Keep this set intentionally small. There is no separate Launcher Framework, Visual Testing, implementation-language, build-tool, CI, ChatGPT, Codex, or Development Brief Skill.

## Primary owner map

```text
launcher architecture / Tauri core / operations / settings / update / Windows distribution
workspace / process / provisioning / Java / Paper runtime / bundled LazyBuilder core
→ lazybuilder-desktop-runtime

third-party Paper plugin identity / dependency / compatibility / lifecycle / rollback / restart requirement
→ lazybuilder-plugin-management

world lifecycle / registry / Paper world behavior / filesystem / import-export-conversion
→ lazybuilder-world-management

presentation / interaction / input / navigation / layout / accessibility / visual proof
→ lazybuilder-ui

neutral Paper↔Fabric request-result / identifiers / validation / versioning / transfer framing
→ lazybuilder-protocol
```

Key exclusions:

```text
desktop loopback HTTP/Tauri IPC        → desktop-runtime, not protocol
bundled World/Utilities core           → desktop-runtime, not plugin-management
Paper/world domain behavior            → world-management, not protocol
plugin dependency/compatibility truth  → plugin-management, not ui
runtime/world/plugin truth shown in UI → semantic owner first; ui only presents it
```

## Activation and discovery cues

Natural-language wording is a **candidate trigger**, never final ownership proof. Use these cues to shortlist one Skill, then confirm with the first separating evidence.

```text
lazybuilder-desktop-runtime
candidate cues:
"server won't start", "launcher stuck", "workspace missing", "Paper/Java not provisioned",
"restart/recovery", "readiness/health wrong", "backup/restore runtime", "settings not persisted",
"NSIS/update/Windows identity", "long operation stuck", "desktop HTTP/auth"
reject when:
canonical runtime result is already correct and only presentation is wrong;
issue is Paper world semantics, third-party plugin lifecycle, or neutral Paper↔Fabric wire meaning

lazybuilder-plugin-management
candidate cues:
"plugin missing", "plugin duplicated", "wrong plugin version", "dependency missing",
"plugin incompatible", "install/update/remove failed", "enable/disable", "restart required",
"plugin JAR rollback"
reject when:
artifact is bundled LazyBuilder core; issue is only list/warning layout/copy;
issue is world behavior or shared protocol

lazybuilder-world-management
candidate cues:
"world won't load/teleport", "archive/restore world", "duplicate/delete world",
"world import/export", "InspectImport", "conversion failed", "world registry wrong",
"ACTIVE/ARCHIVED", "world filesystem/publish", "idle unload"
reject when:
issue is server process/provisioning, UI-only world screen behavior,
neutral Paper↔Fabric payload meaning, or third-party plugin lifecycle

lazybuilder-ui
candidate cues:
"button doesn't respond", "double submit", "stale screen", "wrong loading/error state",
"back/close broken", "focus/keybind issue", "layout overflow", "GUI scale",
"accessibility", "visual mismatch", "progress bar wrong", "map flicker/reset"
reject when:
canonical runtime/plugin/world/protocol result itself is wrong

lazybuilder-protocol
candidate cues:
"client/server payload mismatch", "request/result shape wrong", "field/default mismatch",
"validation/bounds", "identifier mismatch", "capability advertisement", "protocol version",
"World Control", "Map Action", "transfer framing/chunk/order"
reject when:
only two modules are involved but shared wire meaning is unchanged;
issue is desktop HTTP/Tauri IPC, Paper domain behavior, or presentation
```

Rules:

- cue matching may nominate a Skill; it must not bypass the owner-selection rule below;
- negative/rejection cues outrank superficial keyword matches;
- feature names such as `Import`, `Teleport`, `Plugin`, `Map`, `Restart`, or `Update` are insufficient by themselves;
- if two Skills remain plausible, gather one separating observation before loading either deeply;
- ChatGPT and Codex use the same cues and rejection rules.

## Owner selection rule

A symptom is not an owner. Choose the first wrong semantic boundary using the smallest separating evidence.

```text
symptom
→ compare canonical semantic result with adapter/transport/presentation
→ identify first wrong owner
→ load ONE primary Skill
```

If evidence cannot yet distinguish owners, use global `UNKNOWN` and name the next separating evidence. Do not load several Skills "just in case".

## Fast routing matrix

Use this matrix only when the observed evidence matches the row directly. It is a shortcut to the same owner/taxonomy rules, not a second routing authority. If no row matches cleanly, return to the owner-selection rule above.

| Observed evidence | Primary owner | Next context | Action | Proof / exit |
|---|---|---|---|---|
| Rust/runtime canonical state itself is wrong | desktop-runtime | exact runtime owner/service only | classify local runtime subtype and fix there | matching Desktop proof; STOP |
| Rust/runtime result is correct but rendered Launcher state is stale/wrong | ui | canonical runtime result + exact UI consumer | fix presentation only | UI proof; STOP |
| Third-party plugin metadata/dependency/compatibility result is wrong | plugin-management | exact plugin manager + affected JAR/metadata | fix plugin lifecycle truth | plugin proof; STOP |
| Plugin lifecycle result is correct but warning/list/action presentation is wrong | ui | canonical plugin result + exact UI surface | fix presentation only | UI proof; STOP |
| Shared Paper↔Fabric payload/default/bounds/version meaning disagrees | protocol | exact shared type + one producer + one consumer | fix neutral contract | contract proof; typed handoff if domain work remains |
| Shared contract is correct but Paper world behavior/result is wrong | world-management | canonical payload/result + exact Paper world owner | fix world-domain implementation | world proof; STOP |
| World lifecycle/registry/filesystem/import-publication truth is wrong | world-management | exact world id/service/registry/filesystem evidence | fix world semantics | matching world proof; STOP |
| World result is correct but screen/map pending/back-close/layout state is wrong | ui | canonical world result + exact UI surface | fix presentation only | UI proof; STOP |
| Desktop HTTP auth/session/request envelope is wrong | desktop-runtime | exact loopback route/session evidence | fix transport envelope | Desktop proof; STOP |
| Desktop HTTP transport is correct but world-domain result is wrong | world-management | authenticated request context + exact world evidence | fix domain result | world proof; STOP |
| Bundled LazyBuilder World/Utilities core is missing/incompatible | desktop-runtime | exact managed-core identity/provisioning evidence | fix provisioning/synchronization | Desktop proof; STOP |
| Third-party plugin mutation is correct on disk but Paper rejects/load-enable state is wrong | plugin-management | exact plugin artifact + live Paper evidence when available | keep plugin semantic owner; classify `PAPER_RUNTIME` residue as needed | `LIVE_RUNTIME` for runtime claim |
| UI click/key/pending race duplicates an otherwise-correct action | ui | exact UI controller + canonical action result | fix input/async presentation path | UI proof; STOP |
| New world capability requires new shared Paper↔Fabric meaning | protocol first | exact domain requirement only | freeze neutral contract, prove, STOP Protocol | hand typed contract to world-management |

Fast-path rules:

- do not infer a row from a feature name alone;
- `Next context` is a ceiling for the next read, not permission for a broad scan;
- when a row ends in another owner, emit the minimum typed handoff and stop the current Skill;
- proof type remains determined by the changed claim, not by the table row;
- ChatGPT and Codex use the same matrix; only available execution tools may differ.

## Scenario probes

These probes exist only for recurring ambiguous boundaries.

### Plugin warning is wrong

```text
plugin identity/dependency/compatibility/lifecycle result wrong
→ plugin-management

canonical plugin result correct; wording/layout/severity/action presentation wrong
→ ui
```

Probe the canonical plugin result before editing presentation.

### World import UI fails

```text
wire shape/default/bounds/InspectImport/DiscardImport contract wrong
→ protocol

wire contract correct; inspection/validation/publication/cleanup semantics wrong
→ world-management

canonical import/review result correct; pending/review/back-close/error presentation wrong
→ ui
```

Prove each boundary before loading the next Skill.

### Server operation progress is wrong

```text
operation phase/progress/cancel/retry/result wrong at Rust authority
→ desktop-runtime

runtime snapshot correct; bar/text/disabled state wrong
→ ui
```

Inspect the canonical operation snapshot first.

### Map teleport errors

```text
input race / duplicate dispatch / pending feedback wrong
→ ui

Map Action payload/capability/validation wrong
→ protocol

wire contract correct; Paper authorization/load/teleport behavior wrong
→ world-management
```

### Launcher readiness looks stale

```text
Rust readiness result wrong
→ desktop-runtime

Rust result correct; rendered state stale
→ ui
```

### Plugin mutation fails after restart

```text
plugin identity/dependency/restart-required/load semantics wrong
→ plugin-management
```

Desktop owns the restart process, not third-party plugin lifecycle truth.

### Bundled LazyBuilder core missing/incompatible

```text
World/Utilities core provisioning/synchronization/runtime packaging
→ desktop-runtime
```

### Desktop HTTP world request behaves incorrectly

```text
auth/session/loopback request envelope wrong
→ desktop-runtime

transport correct; world lifecycle/filesystem/domain result wrong
→ world-management
```

### New Paper↔Fabric world capability

```text
neutral contract required
→ protocol → freeze/prove contract → STOP

Paper/domain behavior for frozen contract
→ world-management → prove canonical result → STOP

presentation for proven result
→ ui
```

Never keep all three active on one semantic decision.

## Cross-owner handoff

Cross-domain work is sequential, typed, and minimal.

```text
Owner A decides/changes its contract
→ prove Owner A boundary
→ emit minimum typed handoff
→ STOP Owner A
→ Owner B consumes it without recomputing Owner A truth
```

Canonical payloads:

```text
desktop-runtime → ui
canonical ids + runtime/readiness/operation state + capabilities + stable result/error

plugin-management → ui
canonical plugin identity + lifecycle/capability + restart-required + stable result/error

protocol → world-management
neutral types + identifiers + version + validation/default/bounds/capability semantics

world-management → ui
canonical world identity + lifecycle + capabilities + presentation metadata + operation result/error

desktop-runtime ↔ world-management
Desktop supplies authenticated loopback/process/provisioning envelope;
World Management supplies world semantic result
```

The receiving owner must not rescan/recalculate the previous owner's truth merely because it can access the same files or state.

### UI-originated semantic defect

When UI proves the semantic result itself is wrong, hand off only:

```text
short reproduction
expected vs actual
selected canonical entity id
canonical input/result observed by UI
evidence that the defect survives beyond presentation
```

Then stop semantic UI mutation. UI resumes only after the owning Skill returns a corrected canonical result.

## No-Skill owners

Some repeated repository work has a clear owner but does not justify a specialist Skill.

```text
Utilities-Manager internals  → exact source + canonical system/domain docs
build/version scripts         → exact build/script owner + GITHUB_RULES.md
security-only policy          → SECURITY.md / exact boundary
```

Do not create Skills for Rust, Java, TypeScript, Maven, Gradle, Tauri, Svelte, Playwright, screenshots, testing, ChatGPT, Codex, or implementation mechanics alone.

## Skill creation gate

A new Skill is justified only when all are true:

1. a new semantic responsibility exists;
2. its execution procedure materially differs from the five existing Skills;
3. work is repeated, not one-off;
4. merging it into an existing Skill would materially increase unrelated context;
5. entry, exit, and handoff can be stated clearly.

Otherwise use an existing Skill or a no-Skill source owner.

## Routing completion check

Before leaving routing:

```text
Is there exactly one primary semantic owner?
Was owner selection based on separating evidence rather than symptom location/name/consumer?
Is another Skill actually required now, or only potentially later?
If ownership changes, is the handoff the minimum typed result needed?
Can the next owner proceed without recomputing the previous owner's truth?
```

Then continue under the selected specialist. Global proof/STOP rules remain owned by `development-discipline.md`.