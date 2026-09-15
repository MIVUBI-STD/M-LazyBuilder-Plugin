# Contributing to LazyBuilder Plugin

## Branch model

```text
Local -> active development
main  -> stable/release
```

Normal work targets `Local`. Promotion to `main` is a deliberate release action.

## Developer command surface

Use the repository-root developer CLI rather than calling internal scripts directly for normal integrated operations:

```text
DEV.cmd setup
DEV.cmd check
DEV.cmd build
DEV.cmd test
DEV.cmd finalize-local
```

`DEV.cmd` delegates to `tooling/windows-toolchain/dev.ps1`. Internal scripts remain implementation owners for their domains, but new root-level developer shortcuts should not be added.

Normal `Local` commits do not automatically run full CI. Use targeted local/module proof during development and `DEV.cmd finalize-local` before requesting the integrated final `Verify` pass for a coherent checkpoint.

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

Use the cheapest proof that can falsify the changed claim. Full integrated build/runtime acceptance is a checkpoint tool, not a substitute for focused development tests.

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
- temporary exports, profiling captures, or debug dumps;
- `dist/` or `.runtime-proof/` generated state.

Do not introduce a new framework, task runner, or dependency when the current canonical owner or a small local implementation is sufficient.
