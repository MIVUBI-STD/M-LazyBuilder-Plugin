# LazyBuilder Plugin — Agent Routing

User-authorized work proceeds through verified checkpoints; never claim proof above the available execution context.

## Branch and boot

- `Local` is working authority; `main` changes only by explicit promotion.
- Material GitHub work follows `GITHUB_RULES.md`.
- Canonical documentation starts at `docs/README.md`; resolve one domain before loading deeper context.

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
AGENTS.md → GITHUB_RULES.md core rules
→ smallest canonical owner/evidence
→ CONTEXT.md / docs/05-operations only when continuity matters
→ report → STOP
```

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

Use `.agents/skills/lazybuilder-development-brief/SKILL.md` only when architecture, cross-owner ambiguity, or unresolved success criteria prevent a reliable Standard contract.

## Domain Routing

```text
product / feature intent          → docs/01-product/
world lifecycle / create/settings → docs/02-world-management/
client UI / Xaero integration     → docs/03-client-ui/
system / ownership / boundaries   → docs/04-system/
current continuation / proof      → docs/05-operations/
```

When world-management source work begins, use `.agents/skills/lazybuilder-world-management/SKILL.md`. For client UI/Xaero integration work, use `.agents/skills/lazybuilder-client-ui/SKILL.md`.

## Architecture Discipline

- One responsibility has one canonical owner and one primary execution path.
- Do not create duplicate managers, registries, caches, routers, config systems, schedulers, or compatibility layers without evidence.
- Commands/UI/listeners are adapters; business rules live in explicit application/domain owners.
- Keep Paper/Bukkit access at infrastructure boundaries where practical.
- Prefer small explicit contracts over global/static coordination.
- Extract abstractions only after a real repeated responsibility exists.

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

## Proof Ceiling

```text
REMOTE_GITHUB → source/static/CI claims
LOCAL_CODE    → compile/unit/integration/build claims
LIVE_SERVER   → enable/disable, world lifecycle, teleport, persistence, gameplay/runtime claims
```

## Work Discipline

- Diagnose before editing; fix the first wrong owner.
- Complete GitHub-verifiable work before escalating local/server residue.
- Reuse fresh evidence; no reassurance scans.
- One coherent outcome should normally be one reviewable commit.
- Stop the same failed direction after two attempts without new evidence.
- `No change required` is valid.

## Canonical Owners

```text
repository routing       → AGENTS.md
GitHub execution         → GITHUB_RULES.md
stable project facts     → CONTEXT.md
documentation entry      → docs/README.md
product scope            → docs/01-product/
world management         → docs/02-world-management/
client UI                → docs/03-client-ui/
system ownership         → docs/04-system/
current operations       → docs/05-operations/
```

Do not create duplicate roadmaps, decision logs, status archives, or parallel state systems. Git history is the archive.
