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
safe JAR removal
duplicate detection/resolution
minimum rollback state required by plugin mutation
```

## Does Not Own

```text
bundled World/Utilities core sync → desktop-runtime
Svelte-only plugin presentation   → desktop-ui
Paper world behavior              → world-management
shared client/server protocol     → protocol
```

If lifecycle semantics change and the UI message/control follows, Plugin Management decides the result first; Desktop UI only presents it.

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
- do not create historical backup systems without a real restore UX;
- do not expose plugin-data deletion unless an explicit supported product flow requires it;
- presentation-only categories must not become runtime authority;
- one plugin mutation should have one transaction/rollback owner.

## Proof Boundary

Static/source tests can prove metadata, dependency, and transaction logic. Real Paper plugin enable/load behavior requires appropriate local/live runtime proof.
