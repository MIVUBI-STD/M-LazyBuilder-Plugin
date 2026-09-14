# LazyBuilder Utility Manager

LazyBuilder Utility Manager is the passive, non-building Fabric client convenience layer for Minecraft Java 1.21.4.

## Boundary

Approved scope includes window/client behavior, loading and reload UX, chat convenience, reconnect behavior, preference persistence, notifications, screenshot convenience, and small contextual clipboard actions.

It must not own building/editing tools, palettes, measurement, placement helpers, camera build tools, renderer internals, performance engines, or generic clipboard/history systems.

## Product rules

- one Manager = one Fabric mod = one output JAR;
- no mandatory default keybinds;
- vanilla controls remain authoritative where they already provide a familiar workflow;
- no dependency on Map Manager implementation packages;
- no pollers/watchers/background workers unless an active feature proves they are required;
- preferences exist only for implemented behavior, not for speculative future features.

## Implemented Utility behavior

Current client-side behavior remains deliberately small and vanilla-shaped:

- Extended Chat History: enabled by default and retains more vanilla chat lines/history without replacing the chat screen;
- Keep Chat Draft: enabled by default and restores an unsent draft within the current Minecraft session;
- Reconnect Button: enabled by default and adds one action to the existing vanilla disconnect layout when a previous multiplayer target is known;
- Copy Connection Details: contextual action on the disconnect screen for copying the known server target and disconnect reason;
- Borderless Window: opt-in and applied once at client startup, using the monitor that contains most of the Minecraft window; exclusive fullscreen is left alone;
- Shared Notifications: Utility features use Minecraft's native system-toast surface instead of creating separate HUD or popup systems;
- Resource Reload Notice: startup resource loading stays silent, while later client-resource reloads report completion through the shared notification surface;
- Contextual Screenshot Names: opt-in and keeps the vanilla F2 capture path while adding a safe multiplayer/singleplayer context prefix to automatically named screenshots.

Reconnect state is session-only. Utility Manager does not persist the last server address to disk.

Borderless Window changes only window presentation. Focus-based FPS/resource throttling is explicitly owned by Performance Manager and must not be implemented here.

Screenshot naming does not depend on Map Manager and does not create a replacement screenshot system. Explicit filenames supplied by Minecraft or another mod are left unchanged.

Clipboard helpers are contextual actions only. World/project copy actions belong in the Map Manager UI that owns those values; block, structure, NBT, and other build-data clipboard behavior remains outside Utility Manager.

## Preferences

Preferences are stored in Fabric's normal config directory as `lazybuilder-utility-manager.properties`.

The active preference surface is intentionally limited to implemented features:

```properties
window.borderless=false
chat.extended_history=true
chat.keep_draft=true
connection.reconnect_button=true
screenshots.contextual_names=false
```

The previous `screenshots.organize_by_project` key is accepted as a read-only migration alias so existing local configs continue to work. New saves use `screenshots.contextual_names`.

Dormant options for Compact Info, Chat Timestamps, and Auto Reconnect were removed from the active config surface. They may only return if a later audit proves that the feature itself is worth implementing.
