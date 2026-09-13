---
name: lazybuilder-plugin-management
description: Specialist for LazyBuilder-managed third-party Paper plugin lifecycle: discovery, install, update, enable/disable, duplicate handling, safe removal, dependencies, and rollback. Use when plugin lifecycle is the primary change.
---

# LazyBuilder Plugin Management

Own third-party Paper plugin lifecycle in the desktop product. Do not absorb LazyBuilder core module synchronization or World Manager behavior.

## Use This Owner For

- plugin scan/metadata/dependency validation;
- install/update/enable/disable/remove;
- duplicate JAR detection/resolution;
- rollback/backup behavior required by plugin mutations;
- plugin-manager UI contract only when lifecycle semantics change.

Route elsewhere when the primary owner is:

```text
bundled World/Utilities core sync → lazybuilder-desktop-runtime
Paper world behavior              → lazybuilder-world-management
Svelte-only presentation          → lazybuilder-desktop-ui
shared protocol                   → lazybuilder-protocol
```

## Canonical Context

1. `docs/04-system/skill-routing.md`
2. exact plugin-manager source
3. `docs/04-system/README.md` only when ownership/boundaries change
4. operations docs only when continuation is material

## Rules

- Keep plugin state derivable from filesystem + plugin metadata where practical.
- Do not create a plugin database when filesystem/metadata already owns truth.
- Prefer one previous-valid rollback snapshot over historical backup systems unless a real restore UX exists.
- Do not expose destructive plugin-data deletion without an explicit supported product flow.
- Presentation-only categories must not become runtime authority.
- No hot reload; restart-required semantics stay explicit.
- Duplicate resolution must never partially delete candidates without recoverability.

## Completion

Confirm the mutation path is singular, recoverable, and no internal maintenance feature was promoted into product scope without need.
