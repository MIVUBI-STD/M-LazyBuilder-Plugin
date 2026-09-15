# Minimum-Flow Development Discipline

Canonical execution discipline for LazyBuilder development. This is the repo-local adaptation of a "do the least that correctly solves the problem" approach.

This document owns **how much to build and how to diagnose before mutation**. Domain Skills own **where and how to execute within their boundary**.

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

Do not skip directly to a new manager, registry, cache, router, config system, compatibility layer, scheduler, background worker, dependency, or specialist Skill.

## Evidence Before Mutation

For every non-trivial mutation, obtain the cheapest evidence that can falsify the current diagnosis before editing.

```text
reported symptom / requested outcome
→ current behavior or source evidence
→ failure classification
→ first wrong owner / boundary
→ smallest complete change
→ matching proof
→ STOP
```

Do not begin with a broad repository scan when a narrower owner can answer the question. Do not patch downstream presentation, compatibility, retry, or fallback layers before proving the upstream owner is correct.

Exceptions are limited to truly mechanical changes where the owner and acceptance are already explicit, such as an exact rename or typo correction.

## Failure Classification

Classify the **first material failure**, not every visible symptom. Use the smallest category that identifies the owner or the next separating evidence.

```text
REQUIREMENT            = requested behavior/acceptance is contradictory or materially unresolved
AGENT_REASONING        = source/evidence is sufficient but the execution decision is wrong
SKILL_INSTRUCTION      = specialist procedure causes or preserves the wrong execution behavior
ROUTING                = wrong semantic owner/specialist/context was selected
DESKTOP_RUNTIME        = launcher/workspace/process/provisioning/update/recovery/runtime semantics
PLUGIN_LIFECYCLE       = third-party Paper plugin scan/install/update/enable/remove/dependency semantics
WORLD_RUNTIME          = world lifecycle/import/export/conversion/persistence/Paper world behavior
PROTOCOL               = shared Paper/Fabric request-result or wire contract
UI_PRESENTATION        = presentation/input/focus/feedback/layout/client-only state
BUILD_TOOLCHAIN        = Maven/Gradle/Rust/Node/build/package/version orchestration
DEPENDENCY             = an external or internal dependency contract is wrong or incompatible
INSTALL_ENVIRONMENT    = local Java/Node/Rust/Paper/Windows/tool installation or machine state
PAPER_RUNTIME          = behavior is only wrong/provable on a running Paper server
FABRIC_RUNTIME         = behavior is only wrong/provable in the Fabric/Minecraft client runtime
STALE_TEST             = implementation is correct but test/fixture expectation is obsolete
CI_PROOF               = implementation may be correct but workflow/artifact/provenance proof is wrong or incomplete
RECOVERY               = restart/rollback/partial-failure/data-recovery behavior is wrong
UNKNOWN                = available evidence cannot yet distinguish the owner
```

`UNKNOWN` is valid only when paired with the **next evidence that would distinguish the competing owners**. Do not convert uncertainty into a speculative implementation.

The classification is diagnostic routing, not a new durable state system. Do not store issue taxonomies, duplicate status databases, or parallel review records merely to preserve it.

## Before Editing

Answer only what can change the implementation decision:

```text
What user-visible or runtime result is required?
Does this behavior need to exist at all?
What evidence shows the current behavior is wrong?
What failure class best identifies the first material boundary?
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

## Ambiguity Resolution

There is no meta development Skill. Resolve ambiguity directly before loading a specialist:

```text
state competing semantic owners
→ classify UNKNOWN if evidence cannot yet separate them
→ gather only evidence that can separate them
→ identify first wrong owner / smallest contract boundary
→ choose one primary specialist from skill-routing.md
→ continue under that owner
```

If success criteria themselves are unclear, write a temporary development contract in the working notes/reasoning only:

```text
Goal
Success metric
Non-goal / forbidden proxy
First evidence required
Failure classification / first wrong owner
In scope / out of scope
Execution partition
Proof required
STOP condition
```

Do not create a durable planning layer merely to represent ambiguity.

## Execution Partition

Always finish the cheapest independently provable partition before escalating context.

```text
REMOTE_GITHUB
→ source/static diagnosis
→ repository policy/contracts
→ deterministic tests or CI-verifiable changes
→ exact-commit evidence

LOCAL_CODE
→ compile/build/integration/filesystem/package proof that cannot be established remotely

LIVE_SERVER
→ only the Paper/Minecraft runtime residue that genuinely requires a live server/client
```

A higher-context requirement does not transfer the whole task upward. Prepare and prove everything independent first, then hand off only the minimum residue with its first action, acceptance, and what must not be redone.

## Context Economy

```text
AGENTS.md
→ development-discipline.md
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
repository routing/policy     → repository contract verifier
pure branch/parser/policy     → one focused runnable test
build/compile contract        → targeted build/test
filesystem transaction       → focused local/integration fixture
packaged artifact identity    → exact-SHA provenance + package smoke proof
Paper/client runtime behavior → LIVE_SERVER / appropriate live context
```

A green unrelated check is not acceptance evidence. A build artifact is not automatically runtime proof. A runtime log from another commit is not proof for the current source.

Do not build a test framework, fixture hierarchy, benchmark system, or review layer solely to prove one bounded change.

## Exact-Commit Proof

When CI emits reusable artifacts, the accepted proof should be traceable to the exact repository commit that produced them.

Prefer a machine-readable provenance manifest containing, where applicable:

```text
repository
branch/ref
commit SHA
CI run identity
Minecraft/Paper target
artifact paths + content hashes
which verification stages passed
```

Provenance records evidence; it does not upgrade a test into a stronger proof class. For example, a compiled JAR with provenance is still not live gameplay proof unless the matching runtime gate also passed.

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
→ produce the smallest handoff artifact/result
→ owner B consumes it
```

Do not keep multiple specialists active for the same decision.

## STOP Conditions

Stop when:

- the accepted behavior is complete;
- the first wrong owner is fixed;
- matching proof is sufficient for the current execution context;
- reusable artifacts are tied to the exact commit when provenance is material;
- remaining ideas are optional optimization, future-proofing, or unrelated cleanup.

Do not continue polishing merely because more code could be improved.
