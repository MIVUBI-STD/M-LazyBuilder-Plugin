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

## Implemented Utility behavior

Current client-side behavior remains deliberately small and vanilla-shaped:

- Extended Chat History: enabled by default and retains more vanilla chat lines/history without replacing the chat screen;
- Keep Chat Draft: enabled by default and restores an unsent draft within the current Minecraft session;
- Reconnect Button: enabled by default and adds one action to the existing vanilla disconnect layout when a previous multiplayer target is known.

Reconnect state is session-only. Utility Manager does not persist the last server address to disk.

## Preferences

Preferences are stored in Fabric's normal config directory as `lazybuilder-utility-manager.properties`.

Visual or automatic behavior remains opt-in by default, including borderless window mode, compact info, timestamps, automatic reconnect, and screenshot organization. Features are implemented independently so Utility Manager does not become a collection of unrelated replacement workflows.
