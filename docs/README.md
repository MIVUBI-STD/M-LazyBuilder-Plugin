# LazyBuilder Documentation

Single entry point for human and AI documentation discovery.

## Load Rule

Resolve the task domain first, then load only the smallest canonical set.

```text
PRODUCT / FLOW       → 01-product/
WORLD MANAGEMENT     → 02-world-management/
CLIENT UI / XAERO    → 03-client-ui/
SYSTEM / OWNERSHIP   → 04-system/
CURRENT OPERATIONS   → 05-operations/
```

## Canonical Hierarchy

```text
docs/
├── 01-product/            product identity, scope, end-to-end feature flow
├── 02-world-management/   world lifecycle, create, settings, transfer, safety
├── 03-client-ui/          client UI, keybinds, Xaero integration, interaction flow
├── 04-system/             module/source ownership, context loading, boundaries
└── 05-operations/         current status, next action, proof/handoff
```

## Current Handoff

`REMOTE_GITHUB` source/CI development is complete. The canonical handoff into local/live validation is:

```text
docs/05-operations/remote-github-complete.md
```

For the current phase, read in this order:

```text
CONTEXT.md
→ docs/05-operations/README.md
→ docs/05-operations/remote-github-complete.md
```

The final verified remote source gate is recorded there. Local/live validation must not be inferred from remote CI success.

## Fast Task Routing

```text
change world creation/settings/lifecycle
→ 02-world-management/README.md

change client UI/map interaction
→ 03-client-ui/README.md

change architecture/module ownership
→ 04-system/README.md

continue prior work / interpret current proof
→ 05-operations/README.md

start LOCAL_CODE / LIVE_SERVER validation
→ 05-operations/remote-github-complete.md
```

## Context Policy

1. Start here only when the task domain is not already known.
2. Read the selected domain README.
3. Load only the canonical owner needed for the current decision.
4. Do not preload sibling domains unless the task crosses that boundary.
5. `05-operations/` is current-state context, not durable design authority.
6. Git history owns superseded architecture and rationale.
7. After the REMOTE_GITHUB completion gate, speculative remote refactors are out of scope unless local/live evidence justifies them.

## Authority Roles

```text
Docs     = durable semantic policy / contracts
Skills   = execution procedure
Source   = implementation/runtime truth
Ops docs = current continuation/proof only
```

One concern must have one canonical semantic owner. Link instead of duplicating rules.
