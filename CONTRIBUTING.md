# Contributing to LazyBuilder Plugin

## Branch model

```text
Local -> active development
main  -> stable/release
```

Normal work targets `Local`. Promotion to `main` is a deliberate release action.

## Before coding

1. Identify the feature/domain owner.
2. Define expected behavior and non-goals.
3. Read only the source needed for that decision.
4. Decide the cheapest meaningful verification.
5. Keep the change bounded to one logical outcome.

## Java conventions

- Prefer clear Java over clever abstractions.
- Keep classes focused on one reason to change.
- Prefer constructor injection for dependencies.
- Avoid service locators and mutable global state.
- Avoid static managers except true stateless utilities/constants.
- Keep Bukkit/Paper entrypoints thin.
- Validate configuration at startup and fail with actionable messages when required state is invalid.
- Never swallow exceptions silently.
- Logging must be useful to server operators; avoid per-tick/per-event spam.
- Do not block the Minecraft main thread with file, network, database, or expensive compute work.
- Do not call unsafe Bukkit/Paper operations from async threads.

## Package direction

When implementation starts, organize by feature/domain first, with explicit boundary packages where needed. Avoid a giant horizontal `managers/`, `utils/`, `handlers/`, or `services/` dumping ground.

Preferred shape:

```text
<root package>/
  LazyBuilderPlugin.java       # bootstrap only
  feature/<feature-name>/      # cohesive feature owner
  platform/                    # Paper/Bukkit adapters and infrastructure
  shared/                      # only genuinely cross-feature primitives
```

Exact packages should be created only when real code requires them.

## Tests

Test behavior, not implementation trivia. Domain/application logic should be unit-testable without a server when practical. Use server/integration tooling only for behavior that actually depends on the Paper runtime.

## Commit discipline

Use conventional categorized commits, for example:

```text
feat(selection): add region selection lifecycle
fix(session): clean up player state on disconnect
refactor(command): separate parsing from execution
test(selection): cover invalid corner ordering
build: configure Java toolchain
```

## Repository hygiene

Do not commit:

- compiled jars/classes;
- build directories;
- local Minecraft servers/worlds;
- logs/crash reports;
- IDE metadata not intentionally shared;
- secrets and local environment files;
- temporary exports, profiling captures, or debug dumps.

Do not introduce a new framework or dependency when a small local implementation is sufficient.
