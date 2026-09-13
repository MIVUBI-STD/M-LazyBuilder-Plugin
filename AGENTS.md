# LazyBuilder Plugin — Agent Routing

User-authorized work proceeds through verified checkpoints; never claim proof above the available execution context.

## Branch and boot

- `Local` is working authority; `main` changes only by explicit promotion.
- Material GitHub work follows `GITHUB_RULES.md`.
- Canonical documentation starts at `docs/README.md`; resolve one domain before loading deeper context.
- Canonical specialist/jobdesk routing is `docs/04-system/skill-routing.md`.
- Canonical minimum-flow discipline is `docs/04-system/development-discipline.md`.
- Current readiness is resolved from current `Local` source plus `docs/05-operations/` only when continuation/proof is material.

## Execution Context Gate

```text
REMOTE_GITHUB = repository + CI/static evidence
LOCAL_CODE    = local checkout + JDK/build/tests/filesystem
LIVE_SERVER   = LOCAL_CODE + running Minecraft 1.21.4 Paper server with current build
```

Use the lowest sufficient provable context. `LIVE_SERVER` is never assumed.

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

## Development Gate

Classify work as `Bounded`, `Standard`, or `Complex`.

**Bounded**
```text
Goal
First wrong owner
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
In scope / out of scope
Execution partition
Proof required
STOP condition
```

Use `.agents/skills/lazybuilder-development-brief/SKILL.md` only when architecture, cross-owner ambiguity, or unresolved success criteria prevents a reliable Standard contract. Clear optimization/audit work does not become Complex merely because several files are involved.

## Specialist Routing

Select the specialist by the **semantic decision**, not by implementation language or edited file. Load exactly one primary specialist. Add another only after ownership actually changes.

```text
architecture/cross-owner ambiguity
→ lazybuilder-development-brief

workspace / provisioning / Java / Paper / core / server process / recovery / resources
→ lazybuilder-desktop-runtime

Tauri/Svelte desktop presentation and frontend bridge
→ lazybuilder-desktop-ui

third-party Paper plugin lifecycle
→ lazybuilder-plugin-management

World Manager / Paper world behavior / import-export-conversion
→ lazybuilder-world-management

Fabric client UI / Xaero
→ lazybuilder-client-ui

shared Paper/Fabric request-result/wire contract
→ lazybuilder-protocol
```

Canonical conflict/handoff rules: `docs/04-system/skill-routing.md`.

Do not create standalone Skills for Rust, Java, TypeScript, Maven, Gradle, or implementation mechanics.

## Development Discipline

Every mutation follows `docs/04-system/development-discipline.md`.

Default order:

```text
No change required?
→ delete unnecessary path?
→ reuse current owner/path?
→ native/platform/existing dependency?
→ smallest complete addition
→ new abstraction/system only with proven repeated responsibility
```

Never trade away trust-boundary validation, security, bounded resource limits, data-loss prevention, recoverability, required error handling, accessibility basics, or explicit user requirements merely to reduce code.

## Architecture Discipline

- One responsibility has one canonical owner and one primary execution path.
- One persisted fact has one authority; other layers derive/present it.
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

```text
REMOTE_GITHUB → source/static/CI claims
LOCAL_CODE    → compile/unit/integration/build claims
LIVE_SERVER   → enable/disable, world lifecycle, teleport, persistence, gameplay/runtime claims
```

Use the cheapest proof capable of falsifying the changed claim. A green unrelated check is not acceptance evidence.

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
minimum-flow discipline  → docs/04-system/development-discipline.md
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
```

Do not create duplicate roadmaps, decision logs, status archives, or parallel state systems. Git history is the archive.
