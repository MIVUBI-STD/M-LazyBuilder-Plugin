---
name: lazybuilder-development-brief
description: Temporary owner for genuinely complex/ambiguous LazyBuilder work when architecture, cross-owner ambiguity, or unresolved success criteria prevents selecting one primary specialist. Exit as soon as ownership is resolved. Do not use for audits, bounded maintenance, or clear specialist work.
---

# LazyBuilder Development Brief

Use only to resolve ambiguity, then hand off. Follow `docs/04-system/development-discipline.md` and `docs/04-system/skill-routing.md`.

## Enter Only When

- two or more semantic owners plausibly claim the same decision;
- architecture/ownership itself is the requested change;
- success/acceptance criteria are materially unresolved;
- a material unknown can change the owner or implementation boundary.

Do **not** enter merely because work spans many files, languages, or modules.

## Canonical Context

1. root `AGENTS.md`
2. `docs/04-system/development-discipline.md`
3. `docs/04-system/skill-routing.md`
4. only evidence needed to resolve owner/acceptance
5. ops/continuity context only when unfinished prior state can change the decision

## Output Contract

Resolve only:

```text
Goal
Success metric
First wrong owner / primary semantic owner
Material unknowns that still block execution
In scope / out of scope
Execution partition / handoff order
Proof required
STOP condition
```

`UNKNOWN` is valid only with the exact evidence needed to resolve it.

## Procedure

```text
separate fact / proposal / history / unknown
→ apply minimum-flow discipline
→ identify the first wrong owner
→ choose exactly one primary specialist for the next decision
→ name sequential handoffs only if truly cross-owner
→ exit Development Brief
```

## Owner Selection

```text
desktop workspace/server/runtime → desktop-runtime
desktop Svelte/Tauri UX         → desktop-ui
third-party plugin lifecycle     → plugin-management
world/Paper lifecycle            → world-management
Fabric/Xaero client UI           → client-ui
shared Paper/Fabric wire         → protocol
```

Development Brief is never a permanent super-owner, implementation layer, or review ceremony. Once ownership is clear, hand control back and STOP.
