# LazyBuilder Plugin — Agent Routing

User-authorized work proceeds through verified checkpoints; never claim proof above the available execution context.

## Branch and boot

- `Local` is working authority; `main` changes only by explicit promotion.
- Material GitHub work follows `GITHUB_RULES.md`.
- Canonical documentation starts at `docs/README.md`; resolve one domain before loading deeper context.
- Canonical specialist/jobdesk routing is `docs/04-system/skill-routing.md`.
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
→ GITHUB_RULES.md core rules when GitHub execution matters
→ docs/04-system/skill-routing.md when ownership is not already obvious
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

## Domain Routing

```text
product / feature intent          → docs/01-product/
world lifecycle / create/settings → docs/02-world-management/
Fabric client UI / Xaero          → docs/03-client-ui/
system / ownership / boundaries   → docs/04-system/
current continuation / proof      → docs/05-operations/
```

## Specialist Routing

Load exactly one primary specialist when its execution procedure materially helps. Add another only when semantic ownership actually changes.

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

Canonical jobdesk details: `docs/04-system/skill-routing.md`.

Do not create standalone Skills for Rust, Java, TypeScript, Maven, Gradle, or other implementation mechanics. Route them to the semantic owner above.

## Architecture Discipline

- One responsibility has one canonical owner and one primary execution path.
- Do not create duplicate managers, registries, caches, routers, config systems, schedulers, process markers, or compatibility layers without evidence.
- Shared Paper/Fabric transport contracts are owned by `shared/protocol`; client code must not compile implementation source directly from a Paper module.
- Commands/UI/listeners are adapters; business rules live in explicit application/domain owners.
- Keep Paper/Bukkit access at infrastructure boundaries where practical.
- Prefer small explicit contracts over global/static coordination.
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
- Prefer deletion/consolidation when accepted behavior can stay unchanged.
- Complete GitHub-verifiable work before escalating local/server residue.
- Reuse fresh evidence; no reassurance scans.
- One coherent outcome should normally be one reviewable commit.
- Stop the same failed direction after two attempts without new evidence.
- `No change required` is valid.

## Canonical Owners

```text
repository routing       → AGENTS.md
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
