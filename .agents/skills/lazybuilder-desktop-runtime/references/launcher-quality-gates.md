# Launcher Quality Gates

Use this reference only for a **non-trivial Launcher acceptance audit** after ownership and implementation direction are already clear from `lazybuilder-desktop-runtime/SKILL.md`.

This file is a quality checklist, not a feature roadmap. A missing capability is not automatically work to add.

## Acceptance order

Evaluate in this order:

```text
1. ownership / source of truth
2. destructive and process safety
3. restart / retry / recovery
4. state/queryability
5. diagnostics / supportability
6. packaged Windows behavior
7. presentation handoff
```

Do not fail a bounded feature because unrelated future capabilities are absent.

## Ownership gate

```text
[ ] one durable owner for the changed fact
[ ] Svelte does not persist or infer backend truth independently
[ ] Tauri command validates/delegates instead of becoming a second domain owner
[ ] one process / settings / updater / backup / recovery authority
[ ] no new queue, registry, cache, settings store, or recovery path without proven need
```

## Operation gate

For long-running or restart-sensitive work:

```text
[ ] operation identity and target are stable
[ ] legal states/transitions are explicit
[ ] current state is queryable after UI reload
[ ] progress is semantic when measurable
[ ] duplicate conflicting execution is blocked
[ ] cancel is exposed only at safe boundaries
[ ] retry reconciles authoritative state before repeating effects
[ ] committed success cannot be relabeled failure by a later refresh problem
```

Read `operations-and-recovery.md` when any of these are materially involved.

## Filesystem / destructive gate

```text
[ ] mutation path derives from trusted registry/manifest identity
[ ] source and destination are validated before destructive work
[ ] staging + validate + publish is used where practical
[ ] partial failure cannot register a half-created result
[ ] previous-valid state or deterministic recovery exists where replacement is risky
[ ] ENOSPC / permission denial / lingering process are handled explicitly
[ ] user server workspaces are never treated as disposable app data
```

## Server-library gate

When a change affects server/workspace library behavior:

```text
[ ] missing path remains visible rather than silently reattached
[ ] relocate/adopt validates workspace identity
[ ] process state is reconciled against the actual process, not labels alone
[ ] readiness/health comes from one Rust authority
[ ] expensive checks are bounded/progressive for large libraries
[ ] duplicate/remove/delete/backup/restore semantics remain distinct
```

## Persistence / migration gate

```text
[ ] schema/default authority is singular
[ ] migration is deterministic
[ ] newer/unknown schema fails safely
[ ] risky migration preserves previous-valid data
[ ] startup reconciliation happens before UI claims readiness
```

## Update / distribution gate

When launcher self-update or packaging is touched:

```text
[ ] Launcher update remains separate from Paper/runtime update
[ ] artifact authenticity/integrity is verified before activation
[ ] update state is explicit and queryable
[ ] only one updater operation may run
[ ] product/executable/installer/updater identity stays consistent
[ ] private signing material never enters source/logs/artifacts
[ ] package proof is not overstated as real Windows acceptance
```

Read `windows-distribution-and-update.md` for details.

## Diagnostics gate

```text
[ ] technical failures have stable machine-readable categories where durable handling needs them
[ ] logs/history are bounded
[ ] support evidence includes version/build and relevant runtime/operation state
[ ] secrets/tokens/private keys are excluded
[ ] events accelerate presentation but are not the only state source
```

## Performance gate

Professional desktop behavior requires:

```text
no large filesystem recursion on UI thread
no blocking external-process wait on UI thread
bounded log/history memory
large copy/hash/archive work off the UI thread
bounded progress emission
startup usable from local state before optional network work
```

Optimize after measurement, but do not knowingly introduce blocking architecture.

## Presentation handoff

`lazybuilder-ui` owns layout, wording, accessibility, focus, density, and visual proof. Desktop Runtime must provide enough authoritative state so UI does not invent:

```text
readiness
operation success/failure
retry/cancel availability
plugin/world/process truth
recovery requirement
```

## Proof gate

Choose proof by claim:

```text
state machine / validation / migration / path guards
→ focused source/unit tests

filesystem transaction / recovery
→ isolated integration fixture

packaging / installer contents
→ build + package / installer smoke

real process / Windows filesystem / updater restart
→ packaged Local-PC proof

visual presentation
→ lazybuilder-ui proof lane
```

A green compile is not runtime proof; a packaged installer is not proof of every Windows UX detail.

## Completion

A non-trivial Launcher feature is acceptable when all **applicable** gates above are satisfied and remaining higher-context proof is named precisely. Stop there; do not turn this checklist into justification for unrelated platform work.