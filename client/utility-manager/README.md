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

## C2 foundation

The current implementation provides only the Manager bootstrap and preference persistence foundation. Preferences are stored in Fabric's normal config directory as `lazybuilder-utility-manager.properties`.

All behavior-changing preferences currently default to `false`. Loading the configuration does **not** activate borderless mode, compact HUD, timestamps, auto-reconnect, screenshot organization, or any other client behavior yet. Each feature will be implemented and reviewed independently before it is wired to its preference.

This keeps Utility Manager buildable and persistent without introducing hidden behavior or a new workflow during the foundation phase.
