# Utility Manager Audit Lock

## Scope

This audit locks the current cleanup target for passive client utilities, with chat as the immediate priority.

Utilities Manager is the consolidation destination for small passive Fabric utilities that fit its boundary. Consolidation means one maintained product surface, one mod id, one preference surface, and one output client JAR, while internal source remains modular.

## Single-mod consolidation rule

The final Utility architecture is intentionally **one Fabric mod**, not a collection of small utility mods.

Target runtime artifact:

```text
lazybuilder-utility-manager.jar
```

The following external utility JARs are migration sources only:

- Chat Patches;
- Chat Signing Hider;
- Narrus Yeetus;
- No Chat Reports.

Their retained behavior must be independently rebuilt inside Utility Manager, verified, and then the external JAR retired. They must not be shaded, nested, unpacked, or copied into the LazyBuilder JAR.

Internal modularity remains required. One JAR does **not** mean one implementation class. Chat, signing, accessibility, connection, notification, inventory, debug, screenshot, and window behavior remain separate source domains under Utility Manager.

New third-party passive utility JARs should not become permanent dependencies when the behavior fits this boundary. The default workflow is audit -> retain/drop decision -> first-party implementation -> verification -> Utility ownership.

## Current baseline

Already implemented in Utility Manager:

- extended chat history;
- keep-chat-draft;
- compact local `HH:mm` timestamps;
- bounded session chat search with a native `Ctrl+F` overlay;
- conservative in-place duplicate collapse for explicitly eligible machine messages;
- compact right-click chat context menu with copy-message and conservative copy-player-name actions;
- session separators;
- message classification/routing foundation;
- human-readable warning and command-error presentation foundation;
- presentation-only signing indicator suppression;
- narrator suppression through Minecraft accessibility options;
- reconnect UX;
- connection-detail copy;
- shared native notifications;
- resource reload notice;
- contextual screenshot naming;
- instant creative search;
- compact debug HUD;
- borderless window behavior.

These features are not migration work and must not be reimplemented under new names or split back into separate mods.

## Chat cleanup target

The current chat surface is too noisy because player messages, vanilla gameplay events, system output, warnings, command diagnostics, and developer-facing detail compete in one presentation layer.

The cleanup target is not a full chat replacement. It is a bounded routing and presentation layer that preserves Minecraft's familiar chat behavior while reducing noise and duplicate ownership.

### Message classes

The approved classes are:

| Class | Purpose | Default destination |
| --- | --- | --- |
| `CHAT` | player communication | chat |
| `GAME` | join/advancement/gameplay feedback | compact chat |
| `SYSTEM` | concise LazyBuilder/server state relevant to player | compact chat or toast |
| `WARNING` | actionable conflict/degraded behavior | compact chat/toast + detailed log |
| `ERROR` | actionable failure | chat/toast + detailed log |

Developer-only diagnostics must not be routed into normal chat.

## External utility migration matrix

This matrix is behavior-oriented. Third-party source/JARs are requirements references, not code to bundle into LazyBuilder.

| Area | Decision | Current status | LazyBuilder action |
| --- | --- | --- | --- |
| Extended chat history | `ALREADY OWNED` | implemented | keep current bounded implementation |
| Keep unsent draft | `ALREADY OWNED` | implemented | keep current session-scoped behavior |
| Chat timestamps | `REBUILD` | implemented | keep lightweight `HH:mm` presentation |
| Duplicate system-message collapsing | `REBUILD` | implemented conservatively | collapse only explicitly eligible machine messages; never player chat |
| Chat search | `REBUILD` | implemented | keep bounded in-memory search; no background index/database |
| Chat context actions | `REBUILD` | implemented minimally | keep copy message; expose player-name copy only when sender parsing is unambiguous |
| Session separator | `REBUILD` | implemented | preserve compact connection boundary marker |
| Command-error presentation | `REBUILD` | implemented foundation | continue runtime verification against real Brigadier messages |
| Signing-indicator hiding | `REBUILD` | implemented presentation-only | keep UI suppression isolated from protocol logic |
| Narrator suppression | `REBUILD` | implemented | keep inside accessibility boundary using Minecraft-native options |
| Signing/reporting compatibility | `REBUILD` | pending | isolate under `chat/signing`; verify protocol behavior independently |
| Third-party config/UI duplication | `DROP` | migration-ready | use one Utility Manager preference surface and Minecraft-native UI |
| Third-party background workers/indexers | `DROP` | locked | do not introduce unless a concrete verified requirement exists |
| Plugin-specific chat prefixes as primary taxonomy | `DROP` | locked | classify by message purpose instead |
| Repeated raw translation keys in user chat | `DROP` | implemented foundation | resolve human-readable names; retain raw key only in diagnostics |

## External mod retirement status

Retirement is based on retained behavior coverage, not mod name. A JAR is removable only after every retained behavior has first-party coverage or has been explicitly dropped, and runtime verification passes.

| External mod | Status | Reason |
| --- | --- | --- |
| Chat Patches | `NEAR RETIREMENT` | retained core UX has first-party coverage: history, draft, timestamps, search, context copy, session boundaries, compact presentation, and duplicate collapse. Runtime verification and final gap review are still required. Persistent chat-log/database behavior is not an approved requirement. |
| Chat Signing Hider | `NEAR RETIREMENT` | presentation-only signing indicator suppression now exists first-party. Runtime verification is still required before removal. |
| Narrus Yeetus | `NEAR RETIREMENT` | narrator suppression now exists first-party under Utility accessibility ownership. Runtime verification is still required before removal. |
| No Chat Reports | `KEEP FOR NOW` | protocol/reporting behavior is broader than presentation. Utility Manager will only replace retained portions through an independent `chat/signing` implementation after protocol verification. |

The end state remains one `lazybuilder-utility-manager.jar`; `KEEP FOR NOW` means migration is incomplete, not that a permanent second utility mod is accepted.

## Priority order

Implementation order is locked to reduce regression risk:

1. message classification and routing contract;
2. warning/system deduplication;
3. human-readable warning/error presentation;
4. session boundary handling;
5. timestamp presentation;
6. chat search;
7. contextual actions;
8. signing compatibility migration;
9. narrator/accessibility migration;
10. remove external overlapping mods only after runtime verification.

Steps 1-7 and narrator/signing-presentation ownership now have first-party implementations. The remaining high-risk migration item is retained No Chat Reports-equivalent signing/reporting behavior plus runtime verification.

## Signing boundary

Signing presentation and signing/reporting protocol logic are separate responsibilities.

Presentation layer may:

- hide or simplify signing indicators;
- keep normal chat readable;
- expose no packet/signature internals to ordinary chat.

Protocol layer may only be implemented under `utility/chat/signing/` after its required behavior is explicitly documented and independently verified against Minecraft 1.21.4. Packet mutation must never be introduced casually into `ChatHud` or `ChatScreen` mixins.

## Accessibility boundary

Narrator handling belongs under Utility accessibility ownership, not inside chat rendering. Suppression may set Minecraft narrator options to disabled while preserving accessibility infrastructure so it remains available when suppression is turned off.

## Keybind-warning rule

Keybind conflicts are not chat spam.

A conflict should produce at most one concise user-facing warning per stable conflict fingerprint in a session. Human-readable action names should be shown. Raw translation keys and subsystem implementation identifiers belong in diagnostics.

Example target presentation:

```text
Keybind conflict: Terraform Panel ↔ Axion Editor UI
```

not:

```text
key.lazybuilder.terraform.toggle_panel
```

## Command-error rule

Command errors must preserve correctness while reducing Brigadier noise.

Preferred user-facing forms:

```text
Unknown command: /games
```

or:

```text
Invalid argument: expected radius
```

The original command and detailed parser context remain available in diagnostics/logging when needed.

## Dedupe rule

Only low-risk machine-generated categories are eligible for automatic collapse:

- `GAME`;
- `SYSTEM`;
- `WARNING`.

`CHAT` is never automatically collapsed by default.

The ChatHud adapter is stricter than the semantic policy: only messages explicitly marked by the classifier/Utility path are eligible for in-place replacement. Lookalike plugin text must not be collapsed by text shape alone.

A dedupe fingerprint must be derived from stable semantic fields rather than rendered color/style so presentation changes do not alter grouping behavior.

## Notification routing rule

Use existing Minecraft-native Utility notifications instead of creating another popup system.

| Event | Chat | Toast | Detailed log/console |
| --- | :---: | :---: | :---: |
| player message | yes | no | no |
| join/gameplay event | compact | no | optional |
| screenshot saved | no | yes | no |
| resource reload complete | no | yes | yes |
| keybind conflict | compact once | yes once | yes |
| developer-only internal warning | no | no | yes |
| invalid command | yes | no | yes |
| reconnect failure | compact | yes | yes |

## Rejected directions

Do not introduce:

- Discord-style replacement chat;
- avatars or social-profile UI;
- custom fonts as a requirement;
- animated message cards;
- database-backed chat indexing;
- chat-specific background worker threads;
- large replacement `ChatHud`/`ChatScreen` without a proven need;
- one configuration switch per external plugin/source mod;
- duplicate notification systems;
- shaded or nested third-party chat mods inside Utility Manager;
- permanent split-out LazyBuilder utility mods for functionality that belongs to Utility Manager.

## Completion criteria

The cleanup phase is complete only when:

- one Utility Manager mod owns the retained passive utility behavior;
- source ownership is documented and internally modular;
- one preference surface replaces overlapping external configs;
- the chat UX has one LazyBuilder owner;
- existing Utility behavior remains intact;
- noisy repeated warnings are suppressed safely;
- user-facing messages contain human-readable labels;
- developer details remain available outside normal chat;
- retained signing/reporting behavior has a verified first-party implementation or is explicitly dropped;
- Chat Patches, Chat Signing Hider, Narrus Yeetus, and No Chat Reports are no longer required at runtime;
- runtime verification passes on Minecraft Java 1.21.4.
