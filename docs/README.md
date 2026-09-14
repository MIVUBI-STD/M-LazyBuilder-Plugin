# LazyBuilder Documentation

Single entry point for human and AI documentation discovery.

## Load Rule

Resolve the task domain first, then load only the smallest canonical set.

```text
PRODUCT / FLOW          → 01-product/
WORLD MANAGEMENT        → 02-world-management/
FABRIC CLIENT / MAP UI  → 03-client-ui/
SYSTEM / OWNERSHIP      → 04-system/
CURRENT OPERATIONS      → 05-operations/
```

Two system documents answer different questions:

```text
How much should we build? → 04-system/development-discipline.md
Who owns the decision?    → 04-system/skill-routing.md
```

Do not read every Skill or every domain by default.

## Canonical Hierarchy

```text
docs/
├── 01-product/            product identity, scope, end-to-end feature flow
├── 02-world-management/   world lifecycle, create, settings, transfer, safety
├── 03-client-ui/          Fabric client UI, first-party map interaction, navigation
├── 04-system/             architecture, minimum-flow discipline, ownership, networking, specialist routing
└── 05-operations/         current status, next action, proof/handoff
```

Xaero may appear only as an interaction-quality reference where explicitly documented. It is not LazyBuilder's map authority, networking owner, or runtime dependency.

## Fast Task Routing

```text
optimize / simplify / reduce overdevelopment
→ 04-system/development-discipline.md
→ 04-system/skill-routing.md only if owner is unclear

change product behavior / user flow
→ 01-product/README.md

change world creation/settings/lifecycle/import/export
→ 02-world-management/README.md
→ lazybuilder-world-management when its procedure materially helps

change presentation/input
→ lazybuilder-ui
→ Desktop branch for Tauri/Svelte
→ Fabric branch for Map/Utility/Performance client presentation

change workspace/server/provisioning/process/runtime/resources
→ 04-system/skill-routing.md
→ lazybuilder-desktop-runtime

change third-party Paper plugin lifecycle
→ 04-system/skill-routing.md
→ lazybuilder-plugin-management

change shared Paper/Fabric wire contract
→ 04-system/networking.md
→ lazybuilder-protocol

change architecture/module ownership
→ 04-system/README.md
→ 04-system/development-discipline.md
→ 04-system/skill-routing.md
→ resolve one primary specialist; no meta development Skill

continue prior work / interpret current proof
→ 05-operations/README.md
→ 05-operations/current-verification.md
```

## Context Policy

1. Start here only when the task domain is not already known.
2. Apply `development-discipline.md` for mutation/optimization work.
3. Read the selected domain README.
4. Load only the canonical owner needed for the current decision.
5. Load exactly one primary specialist when its procedure materially helps.
6. For `lazybuilder-ui`, select only Desktop or Fabric branch context unless the task truly spans both surfaces.
7. Add another specialist only after semantic ownership actually changes.
8. `05-operations/` is current-state context, not durable design authority.
9. Git history owns superseded architecture and rationale.

## Authority Roles

```text
Docs     = durable semantic policy / contracts
Skills   = specialist execution procedure
Source   = current implementation/runtime truth
Ops docs = current continuation/proof only
```

One concern must have one canonical semantic owner. Link instead of duplicating rules.

## Specialist Set

```text
lazybuilder-desktop-runtime
lazybuilder-plugin-management
lazybuilder-world-management
lazybuilder-ui
lazybuilder-protocol
```

The canonical jobdesk map is [`04-system/skill-routing.md`](04-system/skill-routing.md). Do not add a Skill for an implementation language/tool alone, or for one-off planning/ambiguity.
