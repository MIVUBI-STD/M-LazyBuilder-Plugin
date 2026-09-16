# Utilities Manager Architecture Lock

## Status

This document locks the ownership boundary and migration direction for LazyBuilder Utilities on the Minecraft Java 1.21.4 client.

## Ownership

Utilities Manager owns passive, non-building client convenience behavior only:

- chat presentation and convenience;
- notification routing;
- reconnect UX;
- creative inventory convenience;
- compact debug presentation;
- screenshot convenience;
- borderless window behavior;
- small contextual clipboard actions;
- accessibility helpers that do not replace Minecraft's accessibility system.

Utilities Manager must not own:

- terrain/build/edit tools;
- placement or palette systems;
- world management;
- performance optimization engines;
- server lifecycle;
- generic renderer internals;
- background workers unless a concrete implemented feature requires them.

## Packaging lock

One Manager equals one Fabric mod and one client output JAR.

The source tree may contain many internal modules, but the product must not return to a stack of overlapping small utility mods for features that fit the Utilities boundary.

Paper/server code remains a separate runtime artifact. Client and Paper implementations may belong to one product family, but they must not be forced into one binary.

## Chat ownership lock

Utilities Manager is the single client-side owner for LazyBuilder chat UX.

The chat implementation must remain modular rather than becoming one large mixin or replacement screen. Preferred internal ownership:

```text
utility/chat/
├── classify/
├── history/
├── input/
├── search/
├── presentation/
├── context/
└── signing/
```

Thin mixins adapt Minecraft events and screens into these modules.

### Stable message categories

Only five primary presentation categories are approved:

1. `CHAT` — player communication.
2. `GAME` — normal gameplay feedback.
3. `SYSTEM` — concise LazyBuilder/server information relevant to the player.
4. `WARNING` — actionable degraded behavior or conflict.
5. `ERROR` — actionable failure.

Do not create a category per plugin or subsystem.

### Routing policy

Chat is not a developer console.

- Player-relevant information may appear in chat.
- Developer-only diagnostics go to logs or launcher/server console.
- Information relevant to both uses a concise in-game message plus detailed log/console output.
- Internal translation keys, stack traces, packet/signing internals, and implementation identifiers are not normal user-facing chat content.
- Repeated `GAME`, `SYSTEM`, and `WARNING` messages may be collapsed using a stable fingerprint.
- Normal player chat is never deduplicated by default.

### Presentation policy

The default visual direction stays Minecraft-native and compact.

- Do not replace the entire chat screen unless a verified requirement cannot be met with bounded mixins/adapters.
- Timestamps, if implemented, default to `HH:mm`; seconds are optional.
- System/game feedback uses lower visual emphasis than player chat.
- Warning/error emphasis is reserved for actionable conditions.
- Session boundaries may be rendered as compact separators instead of repeated connection noise.
- Command errors should be simplified for the player while retaining raw detail in diagnostics.
- Repeated keybind warnings must be deduplicated and must resolve human-readable names rather than exposing translation keys.

### Search and contextual actions

Chat search and context actions are permitted when implemented without a background indexer or database.

Preferred behavior:

- search operates over retained in-memory chat history;
- contextual actions remain small and relevant, such as Copy Message, Copy Player Name, or Copy Raw Text;
- coordinate actions are allowed only when coordinates are parsed confidently;
- no oversized Discord-style context menu or replacement social UI.

### Signing compatibility

Message signing/reporting compatibility is a transport/security concern, not a presentation concern.

Signing compatibility must live behind a dedicated internal boundary so packet/signing behavior cannot become entangled with rendering, history, search, or input handling.

Third-party chat/signing mods must not be embedded or shaded into Utilities Manager as a shortcut. Required behavior must be independently implemented, audited, and verified before an external dependency is retired.

### Narrator/accessibility

Narrator-related convenience belongs under an accessibility helper boundary, not inside the chat renderer.

Utilities Manager may suppress unwanted narrator behavior or redundant narrator warnings when explicitly configured, but it must not remove Minecraft accessibility infrastructure wholesale.

## External-mod consolidation policy

The current consolidation target covers behavior historically provided by small overlapping client mods such as chat-patching, chat-signing hiding/reporting helpers, and narrator suppression.

Every external feature must be classified before migration:

- `KEEP` — behavior is valuable and should be reproduced inside Utilities Manager;
- `REBUILD` — useful behavior exists but must be reimplemented to fit LazyBuilder architecture;
- `DROP` — redundant, unsafe, noisy, or unnecessary behavior;
- `ALREADY OWNED` — equivalent behavior already exists in Utilities Manager.

Removal of an external mod is allowed only after required `KEEP`/`REBUILD` behavior is implemented and runtime-verified.

## Current implementation lock

Already-owned Utility behavior includes:

- extended chat history;
- keep-chat-draft;
- reconnect button and connection-detail copy;
- shared Minecraft-native notifications;
- resource reload completion notice;
- contextual screenshot naming;
- instant creative search;
- compact debug HUD;
- borderless window preference.

Do not duplicate these features during consolidation.

## Verification gates

Chat consolidation is not complete until all of the following are true:

1. one Utility Manager client JAR owns the approved utility behavior;
2. no duplicate mixins compete for the same chat/signing surface;
3. player chat remains readable during repeated join/system/warning traffic;
4. repeated warning events do not spam identical lines;
5. internal translation keys are absent from normal user-facing warnings;
6. command errors remain actionable and understandable;
7. chat history and draft behavior still pass existing tests;
8. no new background polling loop is introduced;
9. Minecraft-version-sensitive mixins are covered by upgrade verification;
10. external chat/narrator mods are removed only after replacement behavior passes runtime validation.
