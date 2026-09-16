# LazyBuilder Plugin — Agent Routing

User-authorized work proceeds through verified checkpoints; never claim proof above the available execution context.

## Branch and boot

- `Local` is working authority; `main` changes only by explicit promotion.
- Material GitHub work follows `GITHUB_RULES.md`.
- Canonical documentation starts at `docs/README.md`; resolve one domain before loading deeper context.
- Canonical specialist/jobdesk routing is `docs/04-system/skill-routing.md`.
- Canonical minimum-flow and diagnosis discipline is `docs/04-system/development-discipline.md`.
- Current readiness is resolved from current `Local` source plus `docs/05-operations/` only when continuation/proof is material.

## Dual-consumer contract

These repository instructions and Skills are first-class inputs for **both ChatGPT and Codex**. Neither consumer is the semantic authority over the other.

```text
same user requirement
→ same source precedence
→ same failure classification
→ same specialist routing
→ same domain invariants
→ same proof requirement
```

Only the **available execution capability** may differ by session.

- Never assume shell, local checkout, filesystem, browser, GitHub mutation, CI dispatch, or live Minecraft access merely because one consumer often has it.
- Detect the tools/context actually available, then use the lowest sufficient execution context.
- ChatGPT may inspect or mutate repository state through connected tools when available; Codex may inspect or mutate a local workspace when available. Both must obey the same owner/proof contracts.
- When required execution is unavailable, complete every independently provable partition and name the exact remaining residue. Do not simulate a command, test, screenshot, runtime result, or file mutation.
- Repository-relative paths, exact symbols/contracts, and explicit handoff payloads are preferred over consumer-specific UI instructions.
- A Skill must remain understandable when read directly by either consumer; consumer-specific mechanics belong only where the task genuinely depends on them.

## Capability Gate

Resolve capability from the current session before choosing execution mechanics. Capability affects **what can be executed or proven now**, never which semantic owner is correct.

```text
REPO_READ
= can inspect current repository source/history/docs

REPO_WRITE
= can mutate the authoritative repository/ref

LOCAL_SHELL
= can execute commands against a local checkout/toolchain

CI_CONTROL
= can inspect/dispatch/re-run relevant CI and retrieve exact-run evidence

ARTIFACT_ACCESS
= can inspect/download exact build/proof artifacts

VISUAL_RENDERER
= can produce or inspect the relevant production/simulated renderer output

LIVE_RUNTIME_ACCESS
= can exercise the exact changed Paper/Fabric/client-server path

NATIVE_HOST
= can exercise target local Windows/device behavior
```

Use the smallest available capability set that can satisfy the claim:

```text
REPO_READ only
→ diagnose / route / inspect contracts / STATIC_SOURCE
→ do not imply mutation or execution

REPO_READ + REPO_WRITE
→ repository mutation is allowed
→ proof remains limited to evidence actually observed

LOCAL_SHELL
→ source/build/test/filesystem execution may produce EXECUTED_SOURCE or INTEGRATION_FIXTURE

CI_CONTROL / ARTIFACT_ACCESS
→ use exact commit/run/artifact evidence
→ CI location never upgrades the proof type by itself

VISUAL_RENDERER
→ may produce VISUAL_SIMULATED or VISUAL_RENDERED according to the real renderer used

LIVE_RUNTIME_ACCESS
→ may produce LIVE_RUNTIME only when the changed runtime path is exercised end to end

NATIVE_HOST
→ may produce NATIVE_ACCEPTANCE only for behavior actually exercised on the target host/device
```

Capability rules:

- capability is additive, not hierarchical: having `REPO_WRITE` does not imply `LOCAL_SHELL`; having `CI_CONTROL` does not imply `LIVE_RUNTIME_ACCESS`; having a screenshot does not imply `VISUAL_RENDERER` for the current revision;
- capability names describe access/execution ability; proof types describe evidence actually observed. Never treat a capability name as proof by itself;
- never substitute a different capability for a required proof type merely to finish the task;
- if a higher capability is absent, finish all lower-capability partitions and record only the precise residue that still requires it;
- do not create ChatGPT-specific or Codex-specific Skill branches; capability names, repository-relative paths, exact commands, and typed handoffs are the portable contract.

## Execution Context Gate

```text
REMOTE_GITHUB = remote repository/evidence context; exact capabilities still come from the Capability Gate
LOCAL_CODE    = local checkout + available local toolchain/filesystem capabilities
LIVE_SERVER   = LOCAL_CODE + available running Minecraft 1.21.4 Paper/Fabric runtime access
```

Execution context does not grant capability by itself. For example, `REMOTE_GITHUB` does not imply `CI_CONTROL` or `ARTIFACT_ACCESS`, and `LOCAL_CODE` does not imply a live server.

Use the lowest sufficient provable context. `LIVE_SERVER` is never assumed.

A request that contains a live-runtime residue does not automatically move the whole task to `LIVE_SERVER`. Exhaust source/static/CI-verifiable work first, prepare independent proof/harnesses, then hand off only the minimum residue that genuinely needs higher context.

## Observe / Audit

For read-only inspection:

```text
AGENTS.md
→ development-discipline.md
→ skill-routing.md only when ownership is not obvious
→ smallest canonical owner/evidence
→ CONTEXT.md / docs/05-operations only when continuity matters
→ report → STOP
```

Do not preload all docs/Skills for an audit.

### Evidence Opt-In Rule

Historical/supporting documents are **not default context**. Files whose primary purpose is audit evidence, migration history, implementation status, handoff history, remediation notes, or architecture locks are loaded only when they can materially change the current decision or when the user asks for that history/proof.

```text
current canonical owner/source is sufficient
→ do not load supporting evidence

owner/source is contradictory, rationale is material, or proof history is requested
→ load the smallest directly relevant evidence document
```

Do not use broad evidence-document scans for reassurance. Git history remains the archive for superseded designs.

## Development Gate

Classify work as `Bounded`, `Standard`, or `Complex`.

**Bounded**
```text
Goal
First evidence
Failure classification / first wrong owner
Acceptance
Proof required
STOP condition
```

**Standard**
```text
Goal
Success metric
Non-goal / forbidden proxy
First evidence / first wrong owner
Failure classification
In scope / out of scope
Execution partition / higher-context residue
Proof required
STOP condition
```

**Complex / ambiguous** uses the same discipline, not a separate Skill:

```text
state competing semantic owners / unknowns
→ gather only separating evidence
→ classify the first material failure
→ resolve the smallest contract boundary
→ choose one primary specialist
→ continue implementation
```

Clear optimization/audit work does not become Complex merely because several files are involved.

## Evidence Gate

For non-trivial mutation, obtain the cheapest evidence capable of falsifying the current diagnosis before editing.

```text
symptom / requested outcome
→ current evidence
→ failure classification
→ first wrong owner
→ smallest complete change
→ matching proof
→ STOP
```

Use the canonical taxonomy in `docs/04-system/development-discipline.md`. `UNKNOWN` is allowed only with the next separating evidence named. Do not turn uncertainty into fallback code, compatibility layers, retries, or a second owner.

## Specialist Routing

Select the specialist by the **decision being made**, not by language or filename.

There are now two specialist layers:

```text
SEMANTIC / PRODUCT SPECIALISTS
→ decide what the product/domain/wire/presentation contract means

IMPLEMENTATION SPECIALISTS
→ implement an already-decided Paper or Fabric contract using the platform correctly
```

A semantic Skill and an implementation Skill are sequential, not competing authorities. When semantics are material, freeze/prove the semantic contract first, emit a typed handoff, STOP that Skill, then load the matching implementation Skill.

```text
workspace / provisioning / Java / Paper process / core / recovery / resources
→ lazybuilder-desktop-runtime

third-party Paper plugin lifecycle
→ lazybuilder-plugin-management

World Manager / Paper world behavior / import-export-conversion semantics
→ lazybuilder-world-management

presentation/input on desktop or Fabric/Minecraft client
→ lazybuilder-ui
→ choose one primary UI lane only

shared Paper/Fabric request-result/wire contract
→ lazybuilder-protocol

LazyBuilder-owned Paper plugin/module implementation
→ lazybuilder-paper-plugin-development

LazyBuilder-owned Fabric mod/module implementation
→ lazybuilder-fabric-mod-development
```

Examples:

```text
new world capability implemented in Paper plugin
→ protocol/world semantics first as required
→ typed contract
→ paper-plugin-development

new Fabric screen interaction
→ ui semantics first
→ typed UI contract
→ fabric-mod-development

internal Paper command/listener refactor with unchanged semantics
→ paper-plugin-development directly

Fabric initializer/build/resource fix with unchanged semantics
→ fabric-mod-development directly
```

Cross-owner rule:

```text
Owner A decides + proves its boundary
→ emit minimum typed result
→ STOP Owner A
→ Owner B consumes result without recomputing Owner A truth
```

If the symptom can fit more than one specialist, gather the smallest evidence that separates semantic truth from platform implementation before loading another Skill. Canonical scenario probes and handoff payloads live in `docs/04-system/skill-routing.md`.

Do not create standalone Skills for Java, Rust, TypeScript, Maven, Gradle, Tauri, Svelte, testing, or CI alone. Paper Plugin Development and Fabric Mod Development exist because each represents a repeated platform-specific implementation procedure, not merely a programming language or build tool.

## Development Discipline

Every mutation follows `docs/04-system/development-discipline.md`.

Default order:

```text
No change required?
→ delete unnecessary path?
→ reuse current owner/path?
→ native/platform/existing dependency?
→ smallest complete addition
→ new abstraction/system/Skill only with proven repeated responsibility
```

Never trade away trust-boundary validation, security, bounded resource limits, data-loss prevention, recoverability, required error handling, accessibility basics, or explicit user requirements merely to reduce code.

## Architecture Discipline

- One responsibility has one canonical owner and one primary execution path.
- One persisted fact has one authority; other layers derive/present it.
- Paper/Fabric implementation specialists consume semantic contracts; they do not become duplicate product/domain/wire/UI authorities.
- Do not create duplicate managers, registries, caches, routers, config systems, schedulers, process markers, or compatibility layers without evidence.
- Commands/UI/listeners are adapters; business rules live in explicit semantic owners.
- Shared Paper/Fabric contracts live in `shared/protocol`; desktop loopback HTTP remains Desktop Runtime.
- Extract abstractions only after a real repeated responsibility exists.
- Internal maintenance stays internal unless it represents a real user decision.

## Source Precedence

```text
current user requirement
→ current source/proof
→ nearest AGENTS.md
→ canonical domain doc
→ matching Skill
→ current operations state when material
→ history
```

Source is implementation truth; docs/Skills must not preserve a stale model against current source evidence.

## Proof Ceiling

Execution context and proof type are separate.

```text
REMOTE_GITHUB → may produce source/static, executed-source, integration-fixture, package-smoke, or rendered-visual evidence only when the matching capabilities/workflow evidence are actually available and exercised
LOCAL_CODE    → may additionally prove local build/filesystem/package/integration behavior when the required local capabilities are available
LIVE_SERVER   → may produce live Paper/Minecraft runtime evidence only when `LIVE_RUNTIME_ACCESS` is available and the exact changed path is exercised
```

Canonical proof types are defined in `docs/04-system/development-discipline.md`:

```text
STATIC_SOURCE
EXECUTED_SOURCE
INTEGRATION_FIXTURE
PACKAGE_SMOKE
VISUAL_SIMULATED
VISUAL_RENDERED
LIVE_RUNTIME
NATIVE_ACCEPTANCE
```

Do not infer proof strength from where a check ran. `windows-latest` is not automatically `NATIVE_ACCEPTANCE`; a Minecraft screenshot is not automatically `LIVE_RUNTIME`; a running Paper server/client is not feature proof unless the changed behavior was exercised.

Use the cheapest proof capable of falsifying the changed claim. A green unrelated check is not acceptance evidence. A packaged artifact is not live runtime proof.

Reusable CI artifacts should be traceable to the exact producing commit when provenance is material. Provenance records evidence; it does not raise the proof type or ceiling.

## Work Discipline

- Diagnose before editing; fix the first wrong owner.
- Prefer deletion/consolidation when accepted behavior stays unchanged.
- Reuse fresh evidence; no reassurance scans.
- Fewest files consistent with clean ownership wins.
- One coherent outcome should normally be one reviewable commit.
- Stop the same failed direction after two attempts without new evidence.
- `No change required` is valid.
- Stop once accepted behavior + matching proof are complete; future-proofing is not completion work.

## Canonical Owners

```text
repository routing       → AGENTS.md
minimum-flow + diagnosis → docs/04-system/development-discipline.md
specialist/jobdesk map   → docs/04-system/skill-routing.md
GitHub execution         → GITHUB_RULES.md
stable project facts     → CONTEXT.md
documentation entry      → docs/README.md
product scope            → docs/01-product/
world management         → docs/02-world-management/
Fabric client UI         → docs/03-client-ui/
system ownership         → docs/04-system/
current operations       → docs/05-operations/
shared wire contracts    → shared/protocol/
Paper plugin code        → plugins/**
Fabric mod code          → mods/**
```

Do not create duplicate roadmaps, decision logs, status archives, or parallel state systems. Git history is the archive.