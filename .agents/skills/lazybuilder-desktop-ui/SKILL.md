---
name: lazybuilder-desktop-ui
description: Specialist for the LazyBuilder Tauri/Svelte desktop presentation layer: workspace launcher, dashboard, settings, plugins, worlds UI, typed frontend bridge, and interaction flow. Use when desktop UX/presentation is the primary change.
---

# LazyBuilder Desktop UI

Own desktop presentation and interaction flow. The UI presents state and requests actions; runtime/domain rules stay with their backend owner.

## Use This Owner For

- Svelte pages/components and desktop navigation;
- workspace launcher/adoption/provisioning presentation;
- dashboard, settings, plugins, worlds UI;
- frontend TypeScript request/result types and the single Tauri bridge surface;
- loading/error/empty/progress interaction behavior.

Route elsewhere when the primary owner is:

```text
workspace/server/runtime semantics → lazybuilder-desktop-runtime
world behavior                     → lazybuilder-world-management
plugin lifecycle/business rules    → lazybuilder-plugin-management
shared wire contract               → lazybuilder-protocol
Fabric/Xaero client UI             → lazybuilder-client-ui
```

## Canonical Context

1. `docs/01-product/README.md` for product flow when needed
2. `docs/04-system/skill-routing.md`
3. exact desktop frontend source
4. backend contract source only when the UI contract depends on it

## Rules

- Keep exactly one frontend runtime bridge; do not stack facade-on-facade passthrough layers.
- UI validation may improve feedback but must not become the security/domain authority.
- Prefer a small recommended path over exposing internal maintenance operations as user choices.
- Avoid duplicate state that can be derived from backend snapshots.
- Do not expose configuration knobs without a confirmed product use-case.
- Keep Fabric/Xaero UI ownership outside the desktop UI skill.

## Efficiency Check

For each screen/control ask:

```text
Does this change a real user decision?
Does this state need to persist?
Can this value be derived?
Is this internal runtime maintenance accidentally exposed as UX?
```

If the answer shows no durable user value, prefer deletion or automatic internal behavior.

## Proof

Static source can prove routing/types. Actual desktop interaction and platform behavior require local desktop proof.
