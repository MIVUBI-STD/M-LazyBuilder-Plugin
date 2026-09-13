---
name: lazybuilder-desktop-runtime
description: Specialist for LazyBuilder desktop runtime ownership: workspace lifecycle, server process ownership, provisioning, Java/Paper/core runtime, resource configuration, recovery, and local desktop-to-Paper control. Use when that runtime boundary is the primary change.
---

# LazyBuilder Desktop Runtime

Own the desktop runtime boundary. Product policy remains in canonical docs; this Skill owns execution procedure only.

## Use This Owner For

- workspace create/open/adopt/activate/close runtime behavior;
- Paper process start/stop/restart/detached recovery;
- managed Java, Paper provisioning/update, bundled core synchronization;
- server resource configuration and startup safety;
- process markers, runtime recovery, local loopback control bootstrap;
- desktop runtime persistence and rollback semantics.

Route elsewhere when the primary owner is:

```text
Svelte presentation / desktop UX      → lazybuilder-desktop-ui
Paper world lifecycle / import/export → lazybuilder-world-management
third-party Paper plugin lifecycle    → lazybuilder-plugin-management
shared Paper/Fabric wire contract     → lazybuilder-protocol
Fabric/Xaero client UI                 → lazybuilder-client-ui
```

## Canonical Context

1. `docs/04-system/skill-routing.md`
2. `docs/04-system/README.md`
3. exact affected runtime source
4. `docs/05-operations/` only when current continuation/proof is material

Do not preload World Manager application internals unless the desktop/runtime contract crosses that boundary.

## Decision Procedure

1. Name the exact runtime responsibility and its current owner.
2. Find duplicated state/config/process ownership before adding code.
3. Prefer one process owner, one config owner, one provisioning path, and one recovery path.
4. Separate provider responsibilities (`java`, `paper`, bundled core) from orchestration.
5. Make the smallest complete change that preserves recoverability.
6. Prove source/static behavior first; reserve live Paper claims for `LIVE_SERVER`.

## Efficiency Rules

- Do not add a second registry, process marker, config parser/cache, updater, or recovery manager for the same concern.
- Internal bundled core compatibility is not a user decision; keep internal synchronization internal.
- Network checks must not turn a committed local mutation into a false failure.
- Persisted runtime mutations require a recoverable previous-valid state when practical.
- Prefer deletion/consolidation over compatibility layers that no supported consumer needs.
- Avoid background polling/workers when an explicit operation boundary is sufficient.

## Completion

Return to the active development route and confirm:

- one canonical runtime owner remains for each changed responsibility;
- failure leaves the previous valid runtime state recoverable or explicitly classified;
- unrelated World Manager/client/plugin behavior was not absorbed;
- any local/live proof gap is named rather than inferred.
