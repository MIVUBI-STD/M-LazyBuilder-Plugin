# LazyBuilder Utility Manager

LazyBuilder Utility Manager is the passive, non-building Fabric client convenience layer for Minecraft Java 1.21.4.

## Boundary

Approved scope includes window/client behavior, loading and reload UX, chat convenience, reconnect behavior, preference persistence, notifications, screenshot organization, clipboard helpers, and optional compact information display.

It must not own building/editing tools, palettes, measurement, placement helpers, camera build tools, renderer internals, or performance engines.

## Product rules

- one Manager = one Fabric mod = one output JAR;
- no mandatory default keybinds;
- vanilla controls remain authoritative where they already provide a familiar workflow;
- no dependency on Map Manager implementation packages;
- no pollers/watchers/background workers unless an active feature proves they are required.

C2 begins with this buildable no-behavior scaffold so the module boundary can be verified before features are added.
