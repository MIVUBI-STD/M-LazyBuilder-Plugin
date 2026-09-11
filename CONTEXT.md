# LazyBuilder Plugin — Stable Context

## Product

LazyBuilder Plugin is a Minecraft Java plugin project targeting Minecraft 1.21.4.

## Repository authority

```text
Local = active development / source authority
main  = stable / release authority
```

## Current phase

Repository bootstrap and architecture preparation. Feature implementation has not started yet.

## Stable engineering principles

- simple, maintainable architecture;
- one canonical owner per responsibility;
- no duplicate systems;
- minimal framework overhead;
- explicit Paper/Minecraft runtime boundaries;
- efficient runtime behavior and development workflow;
- source/CI proof is kept distinct from live-server proof.

## Current non-goals

Until concrete feature requirements are defined, do not prebuild speculative command frameworks, persistence layers, packet/NMS abstractions, database systems, plugin APIs, compatibility layers, or generic manager hierarchies.
