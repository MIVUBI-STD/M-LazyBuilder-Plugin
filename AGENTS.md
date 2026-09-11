# LazyBuilder Plugin — Agent Rules

This file is the entry point for repository work. Keep work small, evidence-driven, and aligned to one canonical owner.

## Branch authority

```text
Local = active development / working authority
main  = stable / release authority
```

Do not modify `main` during ordinary development unless the user explicitly requests a release/promotion.

## Execution context

Classify by actual capability, not by the product name being used:

```text
REMOTE_GITHUB = GitHub repository + CI evidence only
LOCAL_CODE    = checked-out repository + JDK/build tool/tests/filesystem
LIVE_SERVER   = LOCAL_CODE + running Minecraft 1.21.4 test server with the current plugin build loaded
```

A context label never proves a capability. Never claim runtime/server behavior from source inspection alone.

## Work routing

For every non-trivial task, establish:

```text
Goal
Owner / first wrong layer
In scope
Out of scope
Acceptance
Proof required
STOP condition
```

Then read the smallest source/document set that can answer the task.

## Architecture discipline

- One responsibility has one canonical owner.
- One behavior has one primary execution path.
- Do not create duplicate managers, registries, caches, routers, config systems, command frameworks, schedulers, service locators, or compatibility layers without demonstrated need.
- Prefer explicit dependencies and small interfaces over global/static access.
- Keep Bukkit/Paper API code at infrastructure boundaries; domain logic should remain testable without a live server where practical.
- Commands, listeners, scheduled tasks, persistence, integrations, and configuration must delegate to application/domain owners rather than contain business logic themselves.
- Cross-feature calls go through intentional public contracts, not direct access to another feature's internals.
- Avoid speculative abstractions. Extract only after the responsibility is real and repeated.

## Change discipline

- Diagnose before editing.
- Fix the first wrong owner, not the easiest file.
- Do not mix unrelated cleanup with feature or bug work.
- One coherent outcome should normally be one reviewable commit.
- No checkpoint commits, temporary branches, scratch manifests, dead compatibility code, or placeholder architecture.
- Generated/build output does not belong in source control unless explicitly required.

## Proof ceiling

```text
REMOTE_GITHUB -> source/static/CI claims only
LOCAL_CODE    -> compilation/unit/integration test claims
LIVE_SERVER   -> enable/disable, command, event, scheduler, persistence, reload/restart, and gameplay/runtime claims
```

Use the cheapest proof that can falsify the changed claim. Do not run broad verification as ceremony.

## Documentation owners

```text
repository routing          -> AGENTS.md
GitHub workflow/integrity   -> GITHUB_RULES.md
contribution conventions    -> CONTRIBUTING.md
stable project facts        -> CONTEXT.md
documentation index         -> docs/README.md
architecture                -> docs/architecture.md (when implementation begins)
operations/current handoff  -> docs/operations.md (only when persistent operational state becomes necessary)
```

Do not create parallel roadmaps, duplicate architecture documents, or chronological status archives. Git history is the archive.

## STOP

Stop when the requested outcome is implemented and the relevant proof is satisfied. Report any genuinely higher-context proof as remaining work; do not expand scope to fill time.
