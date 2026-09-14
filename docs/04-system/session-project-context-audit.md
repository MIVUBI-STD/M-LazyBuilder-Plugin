# Session / Project Context Audit

## Purpose

This audit reviews whether LazyBuilder needs a new builder-facing session/project-context subsystem after the cross-manager architecture lock.

The review is intentionally narrow. It covers context that helps a builder understand which managed world they are working in, but it must not become another build HUD, project database, or replacement workflow.

## Existing source authority

Map Manager already has a canonical current-world signal.

`MapActionWireProtocol` exposes:

```text
CurrentWorldRequest
CurrentWorldResult(worldId, displayName, folderName)
CurrentWorldCleared
```

The current-world result is server-authoritative. `CurrentWorldCleared` explicitly represents the case where the player is outside all managed worlds.

`ClientMapController` already stores this result as session state and clears it on reset. Map actions such as current-world teleport/export already consume that state.

Therefore, a second `ProjectContext`, `SessionContext`, or `ActiveWorldManager` state owner would duplicate existing Map Manager authority.

## Terminology decision

The current product model has a first-class **managed world** concept, not a separate first-class **project** entity in the Fabric client contract.

Do not invent persistent `project` identity merely to make builder features sound more general.

Use the existing terms:

```text
managed world
world id
world display name
world folder name
map / transfer state
```

A future project abstraction would require its own product/domain contract and must not be inferred from a world name, folder, server, or local path.

## Candidate audit

| Candidate | Decision | Reason |
| --- | --- | --- |
| New global Project Context manager | Reject | Duplicates current-world authority and introduces a new state owner. |
| Persist active project/world to config | Reject | Runtime world identity is server-authoritative and session-specific. |
| Infer project from server/world/folder name | Reject | Creates non-authoritative identity and failure ambiguity. |
| Permanent current-project HUD | Reject | Adds persistent UI and overlaps F3/map/world surfaces. |
| Duplicate coordinate HUD | Reject | Vanilla F3 and map/build tools already provide spatial context. |
| Session timer / work timer | Reject for current scope | Generic productivity feature, not a demonstrated LazyBuilder gap. |
| Current managed-world context in existing Map Manager surfaces | Keep | Already owned by Map Manager and backed by authoritative protocol state. |
| Contextual world reference for review/handoff | Candidate | Can reuse existing current-world state without creating build/edit behavior. |

## Context model lock

The correct architecture is:

```text
Paper World Manager
        │ authoritative current managed world
        ▼
MapActionWireProtocol
        │ CurrentWorldResult / CurrentWorldCleared
        ▼
ClientMapController.currentWorld
        │
        ├── existing map actions
        └── future contextual non-editing actions, if justified
```

Do not add another session-context service beside this path.

## UI rule

Current-world context should appear only where it helps an existing task.

Allowed examples:

- existing Map Manager/map surfaces;
- a review/handoff action that needs a world identity;
- a warning where an operation requires a managed world but none is active.

Rejected examples:

- permanent overlay;
- floating project badge;
- new default keybind;
- radial menu;
- second world selector;
- duplicate F3 coordinate panel.

## Handoff candidate

The one useful gap that survives this audit is a **shareable non-editing world reference** for collaboration/review.

A minimal reference could be built from already-authoritative Map Manager data:

```text
World: <display name>
World ID: <world id>
Folder: <folder name>
```

This is not yet an approved implementation. It should be evaluated in the Review / Collaboration audit together with location/issue handoff so we avoid creating several overlapping copy/share actions.

Coordinates, dimension, notes, screenshots, assignee/work-item data, and issue state should not be added to the world-context model merely because they could be useful. Each requires a separate owner and use case.

## Ownership decision

```text
Current managed-world identity -> Map Manager
Generic clipboard mechanism     -> local contextual implementation; no cross-Manager dependency required
Coordinates/debug information   -> Vanilla / existing map/build tools
Build/edit state                -> Axiom / WorldEdit ecosystem
Performance state               -> Performance Manager
```

Utility Manager must not become the owner of world/project context just because it has a small clipboard helper. Map Manager should use its own contextual action when it owns the data.

## Audit result

No new Session Manager or Project Context subsystem is justified.

The current Map Manager protocol/controller path already provides the correct session-scoped world authority. The architecture should reuse that state rather than introducing another abstraction.

The next builder-facing review should move to **Review / Collaboration**, using current-world context only as an input where a concrete handoff workflow requires it.
