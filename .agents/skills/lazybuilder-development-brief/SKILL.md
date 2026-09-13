---
name: lazybuilder-development-brief
description: Use only for genuinely complex or ambiguous LazyBuilder development where architecture, cross-owner ambiguity, or unresolved success criteria prevents a reliable Standard contract. Do not use for clear bounded maintenance, audits, or normal specialist work.
---

# LazyBuilder Development Brief

Use only when root `AGENTS.md` cannot form a reliable Standard contract. This Skill resolves ambiguity; it does not become a permanent super-owner.

## Entry Boundary

Enter for:

- architecture/redesign with unresolved ownership;
- material cross-owner ambiguity;
- unclear success/acceptance criteria;
- a material unknown that can change the owner or implementation boundary.

Do **not** load for bounded maintenance, ordinary audits, clear efficiency cleanup, or work already owned by one specialist.

## Canonical Context

1. root `AGENTS.md`
2. `docs/04-system/skill-routing.md`
3. only the domain docs/source needed to resolve the ambiguity
4. `docs/05-operations/` only when unfinished prior state is material

## Development Contract

Make these decision-ready:

```text
Goal
Success Metric
Forbidden Proxy / Non-Goal
First Evidence Required
Failure Classification / first wrong owner
In Scope / Out of Scope
Execution Partition
Proof Required
STOP Condition
```

`UNKNOWN` is valid when paired with the evidence needed to resolve it.

## Procedure

1. Recover only material context; separate fact, proposal, history, and unknown.
2. Identify the first wrong owner or prove ownership is genuinely ambiguous.
3. Choose the smallest specialist set; normally exactly one specialist after ambiguity is resolved.
4. Prefer deletion/consolidation over parallel systems.
5. Partition GitHub-verifiable work from local/live residue.
6. Hand control back to the exact specialist and STOP.

## Owner Selection

```text
desktop workspace/server/runtime → lazybuilder-desktop-runtime
desktop Svelte/Tauri UX         → lazybuilder-desktop-ui
third-party plugin lifecycle     → lazybuilder-plugin-management
world/Paper lifecycle            → lazybuilder-world-management
Fabric/Xaero client UI           → lazybuilder-client-ui
shared Paper/Fabric wire         → lazybuilder-protocol
```

Do not create a new specialist merely because a task is large or implemented in another language.
