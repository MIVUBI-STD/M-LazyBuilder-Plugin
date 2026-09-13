---
name: lazybuilder-desktop-runtime
description: Own LazyBuilder desktop runtime semantics: workspace lifecycle, server process ownership/recovery, provisioning, managed Java/Paper/core, runtime configuration, startup safety, and desktop loopback control. Do not use for Svelte presentation, Paper world rules, shared Paper/Fabric protocol, or third-party plugin lifecycle.
---

# LazyBuilder Desktop Runtime

Own desktop runtime semantics and orchestration. Follow `docs/04-system/development-discipline.md` for minimum-flow decisions and `docs/04-system/skill-routing.md` for handoffs.

## Owns

```text
workspace create/open/adopt/activate/close runtime behavior
Paper process start/stop/restart/detached recovery
one process marker / one recovery path
managed Java provisioning
Paper provisioning and manual Paper updates
bundled core compatibility/synchronization
server runtime/resource configuration
startup safety
local desktop HTTP/control bootstrap
runtime persistence and rollback semantics
```

## Does Not Own

```text
Svelte/Tauri presentation           → desktop-ui
Paper world lifecycle/import/export → world-management
third-party Paper plugin lifecycle  → plugin-management
shared Paper/Fabric wire contracts  → protocol
Fabric/Xaero client UX              → client-ui
```

Desktop loopback HTTP is **this Skill's boundary**, even when it talks to World Manager. `shared/protocol` is not the owner of desktop HTTP transport.

## Canonical Context

1. `docs/04-system/development-discipline.md`
2. `docs/04-system/skill-routing.md`
3. exact affected runtime source
4. `docs/04-system/README.md` only when ownership changes
5. `docs/05-operations/` only when unfinished/current proof state is material

Do not preload World Manager internals unless the runtime contract itself crosses that boundary.

## Procedure

```text
name exact runtime responsibility
→ find current owner/state authority
→ identify duplicate process/config/update/recovery ownership
→ reuse or consolidate existing path
→ smallest recoverable mutation
→ cheapest matching proof
→ STOP
```

Provider details (`java_runtime`, `paper_provider`, bundled core source) stay separate from orchestration only when they have distinct provider responsibilities.

## Runtime Invariants

- one Paper process lifecycle owner;
- one active process identity/marker authority;
- one server-config reader/writer authority;
- one provisioning path;
- one recovery path;
- network lookup after a committed mutation must not manufacture a false failure;
- destructive/runtime replacement keeps a previous-valid state when needed for recoverability;
- bundled core maintenance stays internal unless the user truly has a product decision.

## Proof Boundary

Source/static evidence can prove ownership and failure-path structure. Real Paper start/stop/recovery, Java launch, filesystem permission behavior, and restart persistence require appropriate local/live proof.
