# Minimum-Flow Development Discipline

Canonical execution discipline for LazyBuilder development. This is the repo-local adaptation of the proven "do the least that correctly solves the problem" approach used across related projects.

This document owns **how much to build**. Domain Skills own **where and how to execute within their boundary**.

## Core Rule

```text
accepted result
with the fewest new responsibilities,
the fewest active owners,
the fewest durable states,
and the smallest complete change
```

Shorter code is not the goal by itself. Lower maintenance cost **without reducing correctness, recoverability, security, or required product behavior** is the goal.

## Decision Ladder

Stop at the first level that fully satisfies the requirement:

```text
1. No change required
2. Delete/disable an unnecessary path or feature
3. Reuse the existing canonical owner/path
4. Use language/platform/framework capability already present
5. Use an already-installed dependency when it is materially simpler
6. Add the smallest complete code/config/document change
7. Add a new abstraction/system only when repeated responsibility proves it is necessary
```

Do not skip directly to a new manager, registry, cache, router, config system, compatibility layer, scheduler, background worker, or dependency.

## Before Editing

Answer only what can change the implementation decision:

```text
What user-visible or runtime result is required?
Does this behavior need to exist at all?
Who is the current semantic owner?
Can the existing owner satisfy it directly?
What is the first wrong owner/path today?
What is the smallest proof that can falsify the proposed fix?
```

If current behavior already satisfies the requirement, `No change required` is a valid completion.

## Ownership Economy

- One responsibility gets one canonical owner.
- One persisted fact gets one authority; other layers derive or present it.
- One process gets one lifecycle/recovery owner.
- One config concern gets one reader/writer authority.
- One wire contract gets one neutral contract source.
- One user action gets one primary execution path.
- UI/adapters may validate for feedback but do not become business/security authority.

When two owners appear to overlap, prefer consolidating responsibility over coordinating two permanent authorities.

## Context Economy

```text
AGENTS.md
→ exact specialist (only if procedure materially helps)
→ canonical domain doc
→ exact source/evidence
→ proof
→ STOP
```

Do not preload sibling Skills, all docs, historical reports, or broad source trees "just in case".

Read continuation/ops context only when unfinished prior state can change the current decision.

## Change Budget

A normal change should aim for:

- one semantic owner;
- one coherent outcome;
- fewest touched files consistent with clean ownership;
- no speculative extension points;
- no migration/fallback path without a supported consumer or user-data reason;
- no configurable knob for a value the product does not actually let users decide.

A slightly larger change is justified when it **removes** duplicated owners or state and leaves the architecture simpler afterward.

## Proof Budget

Use the cheapest proof capable of disproving the changed claim.

```text
trivial adapter/rename        → source/static check
pure branch/parser/policy     → one focused runnable test
build/compile contract        → targeted build/test
filesystem transaction       → focused local/integration fixture
Paper/client runtime behavior → LIVE_SERVER / appropriate live context
```

Do not build a test framework, fixture hierarchy, benchmark system, or review layer solely to prove one bounded change.

## Never Simplify Away

Do not remove or weaken these merely to reduce code/tool count:

- trust-boundary validation;
- data-loss prevention and recoverable destructive operations;
- authentication/authorization/security checks;
- bounded resource/file/network limits;
- required error handling;
- accessibility basics in user-facing UI;
- explicit user requirements;
- runtime constraints proven by real platform behavior.

Minimum-flow means **less unnecessary system**, not less safety.

## Cross-Owner Work

A task may cross boundaries, but decisions stay sequential:

```text
owner A decides/changes its contract
→ handoff artifact/result
→ owner B consumes it
```

Do not keep multiple specialists active for the same decision. `lazybuilder-development-brief` may resolve ambiguity, then it exits before implementation continues under the selected owner.

## STOP Conditions

Stop when:

- the accepted behavior is complete;
- the first wrong owner is fixed;
- matching proof is sufficient for the current execution context;
- remaining ideas are optional optimization, future-proofing, or unrelated cleanup.

Do not continue polishing merely because more code could be improved.