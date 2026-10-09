# LazyBuilder Documentation

Single entry point for human and AI documentation discovery.

## Current next action

The current repository phase is **source remediation and synchronization**, not Local PC acceptance and not architecture expansion.

Start here:

[`05-operations/local-pc-remediation-2026-09-15.md`](05-operations/local-pc-remediation-2026-09-15.md)

Current order:

```text
reproduced Local PC findings
→ source remediation
→ runtime/build/product synchronization
→ regression coverage
→ DEV.cmd finalize-local where applicable
→ integrated Verify
→ canonical installer proof
→ final source/repository audit
→ explicit decision to reopen Local PC acceptance
```

Focused Paper/Launcher/visual workflows may be run manually when their specialist evidence is useful, but they are not parallel repository-readiness authorities.

Only after that gate opens, use:

[`05-operations/local-pc-validation-plan.md`](05-operations/local-pc-validation-plan.md)

## Load Rule

Resolve the task domain first, then load only the smallest canonical set.

```text
PRODUCT / FLOW          → 01-product/
WORLD MANAGEMENT        → 02-world-management/
FABRIC CLIENT / MAP UI  → 03-client-ui/
SYSTEM / OWNERSHIP      → 04-system/
DEVELOPMENT OPERATIONS  → 04-system/development-operations.md
CURRENT OPERATIONS      → 05-operations/
CURRENT REMEDIATION     → 05-operations/local-pc-remediation-2026-09-15.md
LATER LOCAL ACCEPTANCE  → 05-operations/local-pc-validation-plan.md
```

Core system documents answer different questions:

```text
How much should we build?        → 04-system/development-discipline.md
Who owns the decision?           → 04-system/skill-routing.md
How do we build/test/deliver?    → 04-system/development-operations.md
```

Do not read every Skill or every domain by default.

## Canonical Hierarchy

```text
docs/
├── 01-product/            product identity, scope, end-to-end feature flow
├── 02-world-management/   world lifecycle, create, settings, transfer, safety
├── 03-client-ui/          Fabric client UI, first-party map interaction, navigation
├── 04-system/             architecture, minimum-flow discipline, ownership, networking, routing, development operations
└── 05-operations/         current remediation, verification, distribution proof, later Local PC acceptance
```

Xaero may appear only as an interaction-quality reference where explicitly documented. It is not LazyBuilder's map authority, networking owner, or runtime dependency.

## Fast Task Routing

```text
optimize / simplify / reduce overdevelopment
→ 04-system/development-discipline.md
→ 04-system/skill-routing.md only if owner is unclear

change product behavior / user flow
→ 01-product/README.md

change world creation/settings/lifecycle/import/export/conversion
→ 02-world-management/README.md
→ lazybuilder-world-management when its procedure materially helps

change presentation/input
→ lazybuilder-ui
→ Desktop branch for Tauri/Svelte
→ Fabric branch for Map/Utility client presentation

change workspace/server/provisioning/process/runtime/resources
→ 04-system/skill-routing.md
→ lazybuilder-desktop-runtime

change third-party Paper plugin lifecycle
→ 04-system/skill-routing.md
→ lazybuilder-plugin-management

change shared Paper/Fabric wire contract
→ 04-system/networking.md
→ lazybuilder-protocol

change build/bootstrap/verification/package/deployment operations
→ 04-system/development-operations.md
→ tooling/windows-toolchain/
→ preserve DEV.cmd as the sole root developer command surface

change architecture/module ownership
→ 04-system/README.md
→ 04-system/development-discipline.md
→ 04-system/skill-routing.md
→ resolve one primary specialist; no meta development Skill

continue remediation / interpret current proof
→ 05-operations/local-pc-remediation-2026-09-15.md
→ 05-operations/current-verification.md

begin target-PC validation (only after remediation gate opens)
→ 05-operations/local-pc-validation-plan.md
```

## Context Policy

1. Start here only when the task domain is not already known.
2. Apply `development-discipline.md` for mutation/optimization work.
3. Read the selected domain README.
4. Load only the canonical owner needed for the current decision.
5. Load exactly one primary specialist when its procedure materially helps.
6. For `lazybuilder-ui`, select only Desktop or Fabric branch context unless the task truly spans both surfaces.
7. Add another specialist only after semantic ownership actually changes.
8. `05-operations/` is current-state context, not durable design authority.
9. During remediation, fix reproduced/source-proven defects and synchronization contradictions before reopening target-machine acceptance.
10. Supporting evidence is opt-in: audit, lock, status, handoff, migration, and historical remediation documents are loaded only when they can materially change the current decision or when historical proof/rationale is requested.
11. Do not broad-scan evidence documents for reassurance after the canonical owner/source already answers the task.
12. Git history owns superseded architecture and rationale.

## Authority Roles

```text
Docs     = durable semantic policy / contracts
Skills   = specialist execution procedure
Source   = current implementation/runtime truth
Ops docs = current remediation/continuation/proof and later Local PC handoff
Evidence = supporting proof/history, opt-in only
```

One concern must have one canonical semantic owner. Link instead of duplicating rules.

## Specialist Set

```text
lazybuilder-desktop-runtime
lazybuilder-plugin-management
lazybuilder-world-management
lazybuilder-ui
lazybuilder-protocol
lazybuilder-paper-plugin-development
lazybuilder-fabric-mod-development
```

The canonical jobdesk map is [`04-system/skill-routing.md`](04-system/skill-routing.md). Do not add a Skill for an implementation language/tool alone, or for one-off planning/ambiguity.

## Knowledge identity, graph, and retrieval

Stable document identity is its canonical repository-relative path. Keep one owner for each fact; do not mirror these documents into a second database, cache, index authority, or invented knowledge graph. Read only the path needed for the decision.

```text
Task → AGENTS.md (root router) → docs/README.md (catalog)
     → docs/04-system/skill-routing.md (semantic owner)
     → relevant domain contract and current implementation (bounded retrieval)
     → affected producer/consumer → scoped decision + proof

Launcher/Desktop source → apps/launcher/
Paper code → plugins/
Fabric code → mods/
Neutral Paper↔Fabric producer/consumer DTOs → shared/protocol/
```

- REQUIRED: selected canonical owner, its current source and only the directly relevant specialist procedure.
- CONDITIONAL: a directly affected producer/consumer contract, history, native proof, or supporting evidence required to separate competing explanations.
- EXCLUDED: unrelated domains, retired aliases, broad audit archives, and whole-repository scanning used solely for reassurance.
- CANONICAL: current source/domain meaning; REFERENCE: outside evidence; HISTORICAL: superseded design rationale; DERIVED: search result, summary, graph edge or generated proof. None of the last three overrides verified canonical source.
- Reuse loaded context only while the branch/ref and relevant owner revision remain valid. Change canonical definitions and their directly affected consumers together; do not duplicate derived state.

See `04-system/module-boundaries.md` for dependency direction and `04-system/development-discipline.md` for evidence and impact-scoped verification. The graph above is a description of existing supported relationships, not a new executable graph engine.
