---
name: lazybuilder-plugin-management
description: Own LazyBuilder-managed third-party Paper plugin lifecycle: scan/metadata/dependencies, install/update, enable/disable, duplicate handling, safe JAR removal, and minimum rollback state. Do not use for bundled LazyBuilder core modules, desktop runtime, or presentation-only plugin UI.
---

# LazyBuilder Plugin Management

Own third-party Paper plugin lifecycle semantics. Follow `docs/04-system/development-discipline.md` and `docs/04-system/skill-routing.md`.

## Owns

```text
plugin discovery/metadata/dependency validation
install/update
enable/disable
safe JAR removal while preserving plugin data
duplicate detection/resolution
one previous-valid rollback snapshot per plugin mutation
```

## Does Not Own

```text
bundled World/Utilities core sync → lazybuilder-desktop-runtime
plugin presentation/UI            → lazybuilder-ui
Paper world behavior              → lazybuilder-world-management
shared client/server protocol     → lazybuilder-protocol
```

If lifecycle semantics change and the UI message/control follows, Plugin Management decides the result first; `lazybuilder-ui` only presents it.

## Canonical Context

1. `docs/04-system/development-discipline.md`
2. `docs/04-system/skill-routing.md`
3. exact plugin-manager source
4. system docs only when ownership changes

## Procedure

```text
identify requested lifecycle result
→ derive current truth from filesystem + plugin metadata
→ find first unsafe/duplicated mutation path
→ reuse one mutation gate/path
→ keep only minimum rollback state
→ targeted mutation proof
→ STOP
```

## Plugin Invariants

- filesystem + plugin metadata remain primary truth; no plugin database without a proven need;
- no hot reload; restart-required semantics stay explicit;
- duplicate resolution cannot partially delete candidates without recoverability;
- persistent rollback state is one previous-valid JAR snapshot, not historical backup retention;
- plugin-data deletion/quarantine is outside current product flow; remove preserves plugin data;
- categories are derived presentation metadata, never a persisted runtime authority;
- one plugin mutation has one transaction/rollback owner.

## Proof Boundary

Static/source tests can prove metadata, dependency, and transaction logic. Real Paper plugin enable/load behavior requires appropriate local/live runtime proof.
