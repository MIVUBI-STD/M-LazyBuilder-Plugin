# LazyBuilder Plugin — Stable Context

## Product

LazyBuilder Plugin is a Minecraft Java plugin project targeting Minecraft 1.21.4.

## Repository authority

```text
Local = active development / source authority
main  = stable / release authority
```

## Project objective

The repository exists to modernize and simplify the plugin stack used by the local Minecraft server.

The working goal is not to preserve every existing plugin as-is. Existing plugins must first be audited by function, dependency, maintainability, operational complexity, and compatibility with the current server target.

For each existing plugin, choose the smallest stable outcome:

```text
KEEP
→ already suitable and maintainable

UPDATE / CLEAN UP
→ useful design, but implementation, dependency, configuration, or API usage is outdated

MERGE / CONSOLIDATE
→ multiple plugins overlap and are better owned by one clear domain

REBUILD
→ required functionality exists, but the current plugin is too legacy, fragile, or difficult to operate reliably

NEW PLUGIN
→ a new implementation is cleaner and more stable than extending or preserving legacy behavior

RETIRE / REPLACE
→ redundant, obsolete, or better served by another maintained solution
```

The desired end state is a smaller, clearer, stable, and maintainable plugin ecosystem for Minecraft 1.21.4, with simple operation and no unnecessary duplicate systems.

## Architecture direction

- Prefer one repository with a multi-module structure when several related custom plugins are required.
- Plugins may remain independently deployable `.jar` files when their lifecycle or responsibility is genuinely separate.
- Do not create a mandatory master/core runtime plugin unless real cross-plugin runtime requirements justify it.
- Shared code should remain an internal library/module when runtime coordination is unnecessary.
- One responsibility has one canonical owner; do not split implementation layers into separate plugins merely because they are commands, listeners, services, storage, or UI.
- External third-party plugins that are mature and already solve a problem well should not be rebuilt without evidence of a real maintenance, compatibility, reliability, or operational problem.

## Current phase

Plugin inventory and architecture discovery.

Feature implementation is intentionally deferred until the existing local-server plugin stack has been audited and classified. The immediate next work should establish what already exists, what remains valuable, what is legacy or overlapping, and what should become part of the LazyBuilder plugin ecosystem.

## Stable engineering principles

- simple, maintainable architecture;
- one canonical owner per responsibility;
- no duplicate systems;
- minimal framework overhead;
- explicit Paper/Minecraft runtime boundaries;
- efficient runtime behavior and development workflow;
- source/CI proof is kept distinct from live-server proof;
- prefer maintenance simplicity over preserving legacy structure;
- modernize incrementally when that is sufficient; rebuild only when it produces a materially cleaner and more reliable result;
- live-server behavior must ultimately be verified on the target Minecraft 1.21.4 server before being considered production-ready.

## Current non-goals

Until the plugin inventory and concrete functional requirements are known, do not prebuild speculative command frameworks, persistence layers, packet/NMS abstractions, database systems, plugin APIs, compatibility layers, generic manager hierarchies, or a mandatory master plugin.
