# Launcher Quality Gates

Use only for a **non-trivial Launcher acceptance audit** after owner, scope, and implementation direction are already clear from `lazybuilder-desktop-runtime/SKILL.md`.

This is an applicability checklist, not a roadmap and not a second proof system. Global proof vocabulary and STOP rules remain in `docs/04-system/development-discipline.md`.

## Acceptance order

```text
1. ownership / source of truth
2. destructive + process safety
3. restart / retry / recovery
4. state/queryability
5. diagnostics/supportability
6. packaged/native Windows boundary when applicable
7. presentation handoff
```

Do not fail a bounded feature because unrelated future capabilities are absent.

## Core gates

### Ownership

```text
[ ] one durable owner for each changed fact
[ ] Svelte does not persist/infer backend truth independently
[ ] Tauri command validates/delegates instead of duplicating domain logic
[ ] no duplicate process/settings/updater/backup/recovery authority
```

### Long-running/restart-sensitive work

```text
[ ] stable operation identity + target
[ ] legal lifecycle is explicit/queryable
[ ] duplicate conflicting execution is blocked
[ ] cancel exists only at safe boundaries
[ ] retry reconciles authoritative state first
[ ] committed success is not relabeled failed by later refresh error
```

Read `operations-and-recovery.md` only when these semantics are materially involved.

### Filesystem/destructive work

```text
[ ] trusted identity derives the mutation path
[ ] source/destination safety validated before mutation
[ ] staging/validation/publication boundary is explicit where needed
[ ] partial failure cannot register half-created output
[ ] deterministic recovery/previous-valid state exists when replacement is risky
[ ] ENOSPC / permission denial / lingering process are handled when applicable
```

### Persistence/readiness

```text
[ ] one schema/default/readiness authority
[ ] migration is deterministic and newer/unknown schema fails safely
[ ] startup reconciliation completes before readiness is claimed
[ ] expensive readiness checks are bounded/progressive where relevant
```

### Update/distribution

Apply only when packaging/self-update is touched:

```text
[ ] Launcher update remains separate from Paper/runtime update
[ ] authenticity/integrity checked before activation
[ ] updater state/lock/identity is singular
[ ] signing secrets stay outside source/logs/artifacts
[ ] package evidence is not overstated as native Windows acceptance
```

Read `windows-distribution-and-update.md` for current implementation boundaries.

### Diagnostics/supportability

```text
[ ] durable failures have stable machine-readable identity where useful
[ ] logs/history are bounded
[ ] support evidence includes relevant build/runtime/operation context
[ ] secrets/tokens/private keys are excluded
[ ] events accelerate presentation but are not the only state source
```

### Performance

Check only architecture-level hazards relevant to the change:

```text
no large filesystem recursion on UI thread
no blocking external-process wait on UI thread
bounded logs/history/progress emission
large copy/hash/archive work off UI thread
optional network work does not unnecessarily block usable local startup
```

## Presentation boundary

`lazybuilder-ui` owns layout, wording, accessibility, focus, density, and visual proof. Desktop Runtime must return enough canonical state that UI does not invent readiness, operation result, retry/cancel availability, process truth, or recovery requirement.

## Proof

Select proof from the canonical vocabulary by the changed claim. Typical mappings:

```text
state/validation/migration          → EXECUTED_SOURCE
filesystem transaction/recovery    → INTEGRATION_FIXTURE
installer/package identity         → PACKAGE_SMOKE
real Windows/process/native behavior → NATIVE_ACCEPTANCE or matching LIVE_RUNTIME
visual presentation                → lazybuilder-ui proof lane
```

## Completion

The audit is complete when all **applicable** gates are satisfied and any higher-capability residue is named precisely. Stop; do not use this checklist to justify unrelated platform work.