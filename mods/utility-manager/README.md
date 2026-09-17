# LazyBuilder Utility Manager

LazyBuilder Utility Manager is the passive, non-building Fabric client convenience layer for Minecraft Java 1.21.4.

## Boundary

Approved scope includes window/client behavior, loading and reload UX, chat convenience, reconnect behavior, preference persistence, notifications, screenshot convenience, creative-inventory convenience, compact builder-facing debug presentation, accessibility convenience, and small contextual clipboard actions.

It must not own building/editing tools, palettes, measurement, placement helpers, camera build tools, renderer internals, performance engines, or generic clipboard/history systems.

## Product rules

- one Manager = one Fabric mod = one output JAR;
- no mandatory default keybinds;
- vanilla controls remain authoritative where they already provide a familiar workflow;
- no dependency on Map Manager implementation packages;
- no pollers/watchers/background workers unless an active feature proves they are required;
- preferences exist only for implemented behavior, not for speculative future features.

## Product direction

Utility Manager is the single client-side owner for passive LazyBuilder utilities. The target is one Fabric utility JAR with modular source ownership, not one monolithic implementation class.

The chat surface keeps Minecraft's familiar interaction while centralizing message presentation, history, draft handling, search/context actions, presentation-only signing compatibility, and notification routing under Utility Manager.

## Implemented Utility behavior

- Extended Chat History: enabled by default and retains more vanilla chat lines/history without replacing the chat screen;
- Keep Chat Draft: enabled by default and restores an unsent draft while the current multiplayer connection/session remains active;
- Chat Search: enabled by default and adds a compact `Ctrl+F` overlay to the vanilla chat screen; search is session-only and does not persist chat to disk;
- Chat Timestamps: enabled by default and prefixes visible chat lines with a low-contrast local `HH:mm` timestamp while preserving original component styling/click behavior;
- Compact Duplicate Messages: consecutive recognized gameplay/warning lines are replaced in place with a single `×N` line within an eight-second window; player chat and unknown system text are never collapsed;
- Chat Context Actions: right-click exposes minimal native-looking copy actions without replacing ChatScreen;
- Signing Indicator Suppression: enabled by default and hides only vanilla `MessageIndicator` presentation. Signatures, packets, and reporting protocol remain untouched;
- Narrator Suppression: enabled by default and uses Minecraft's own narrator option to force `OFF` while disabling the narrator hotkey; accessibility classes/screens remain intact;
- Reconnect Button: enabled by default and adds one action to the existing vanilla disconnect layout when a previous multiplayer target is known;
- Copy Connection Details: contextual action on the disconnect screen for copying the known server target and disconnect reason;
- Borderless Window: opt-in and applied once at client startup;
- Shared Notifications: Utility features use Minecraft's native system-toast surface;
- Resource Reload Notice: later client-resource reloads report completion through the shared notification surface;
- Contextual Screenshot Names: opt-in and keeps the vanilla F2 capture path while adding a safe context prefix;
- Instant Creative Search: enabled by default and enters the existing vanilla Search Items flow as soon as the user types in Creative inventory;
- Compact Debug: enabled by default and replaces the vanilla F3 information wall with a small Minecraft-native builder HUD.

Reconnect state and chat search state are session-only. Utility Manager does not persist the last server address or chat history to disk.

## Chat architecture direction

Chat must not act as a catch-all developer console. Stable presentation categories are intentionally small: `CHAT`, `GAME`, `SYSTEM`, `WARNING`, and `ERROR`.

Player-relevant information may appear in chat; developer-only detail belongs in logs/console; information important to both should use a compact in-game message plus detailed diagnostics. Player chat is never automatically deduplicated.

Signing presentation and signing protocol are separate concerns. Utility Manager may hide the vanilla signing indicator, but it does not strip signatures, rewrite signing packets, disable reporting protocol, or claim No Chat Reports equivalence.

## Maintenance notes

Extended Chat History, timestamps, duplicate collapse, context actions, and signing-indicator suppression all target vanilla ChatHud/ChatScreen APIs and must be reverified on Minecraft-version upgrades.

Narrator suppression uses `GameOptions.getNarrator()` and `getNarratorHotkey()` rather than removing narrator/accessibility infrastructure.

Keep Chat Draft, chat search, chat timestamps, duplicate collapse, signing-indicator suppression, narrator suppression, reconnect actions, screenshot naming, reload notifications, instant creative search, compact debug, and borderless startup application are event/screen-driven. None require a background poller.

## Preferences

Preferences are stored in Fabric's normal config directory as `lazybuilder-utility-manager.properties`.

```properties
window.borderless=false
chat.extended_history=true
chat.keep_draft=true
chat.search=true
chat.timestamps=true
chat.hide_signing_indicators=true
accessibility.suppress_narrator=true
connection.reconnect_button=true
screenshots.contextual_names=false
inventory.instant_creative_search=true
hud.compact_debug=true
```

The previous `screenshots.organize_by_project` key is accepted as a read-only migration alias. Auto Reconnect remains outside the active product surface until implemented and verified.
