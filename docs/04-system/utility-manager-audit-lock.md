# Utility Manager Audit Lock

## Scope

This audit locks the current cleanup target for passive client utilities, with chat as the immediate priority.

Utilities Manager is the consolidation destination for small passive Fabric utilities that fit its boundary. Consolidation means one maintained product surface and one client JAR, while internal source remains modular.

## Current baseline

Already implemented in Utility Manager:

- extended chat history;
- keep-chat-draft;
- reconnect UX;
- connection-detail copy;
- shared native notifications;
- resource reload notice;
- contextual screenshot naming;
- instant creative search;
- compact debug HUD;
- borderless window behavior.

These features are not migration work and must not be reimplemented under new names.

## Chat cleanup target

The current chat surface is too noisy because player messages, vanilla gameplay events, system output, warnings, command diagnostics, and developer-facing detail compete in one presentation layer.

The cleanup target is not a full chat replacement. It is a bounded routing and presentation layer that preserves Minecraft's familiar chat behavior while reducing noise and duplicate ownership.

### Message classes

The approved classes are:

| Class | Purpose | Default destination |
| --- | --- | --- |
| `CHAT` | player communication | chat |
| `GAME` | join/advancement/gameplay feedback | compact chat |
| `SYSTEM` | concise player-relevant LazyBuilder/server state | compact chat or toast |
| `WARNING` | actionable conflict/degraded behavior | compact chat/toast + detailed log |
| `ERROR` | actionable failure | chat/toast + detailed log |

Developer-only diagnostics must not be routed into normal chat.

## External utility migration matrix

This matrix is intentionally behavior-oriented. Third-party source/JARs are references for requirements, not code to bundle into LazyBuilder.

| Area | Decision | LazyBuilder action |
| --- | --- | --- |
| Extended chat history | `ALREADY OWNED` | keep current bounded implementation |
| Keep unsent draft | `ALREADY OWNED` | keep current session-scoped behavior |
| Chat timestamps | `REBUILD` | implement lightweight presentation only if retained after UX verification; default `HH:mm` |
| Duplicate system-message collapsing | `REBUILD` | add bounded fingerprint dedupe for `GAME`/`SYSTEM`/`WARNING` only |
| Chat search | `REBUILD` | in-memory search over retained history; no background index/database |
| Chat context actions | `REBUILD` | small contextual menu: copy message/name/raw text; optional safe coordinate action |
| Session separator | `REBUILD` | represent connection boundaries compactly instead of repeated noise |
| Command-error presentation | `REBUILD` | show human-readable summary; keep raw Brigadier detail in diagnostics |
| Signing/reporting compatibility | `REBUILD` | isolate under `chat/signing`; verify protocol behavior independently |
| Signing indicator hiding | `REBUILD` | presentation-only behavior must not own packet logic |
| Narrator suppression | `REBUILD` | move to accessibility helper boundary; do not remove accessibility infrastructure |
| Third-party config/UI duplication | `DROP` | use Utility Manager preferences and Minecraft-native surfaces |
| Third-party background workers/indexers | `DROP` | do not introduce unless a concrete verified requirement exists |
| Plugin-specific chat prefixes as primary taxonomy | `DROP` | classify by message purpose instead |
| Repeated raw translation keys in user chat | `DROP` | resolve human-readable names; retain raw key only in diagnostics |

## Priority order

Implementation order is locked to reduce regression risk:

1. message classification and routing contract;
2. warning/system deduplication;
3. human-readable warning/error presentation;
4. session boundary handling;
5. optional timestamp presentation;
6. chat search;
7. contextual actions;
8. signing compatibility migration;
9. narrator/accessibility migration;
10. remove external overlapping mods only after runtime verification.

Signing and narrator work intentionally come after basic presentation cleanup so protocol/accessibility concerns do not block the visible UX cleanup.

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

A dedupe fingerprint must be derived from stable semantic fields rather than rendered color/style so presentation changes do not alter grouping behavior.

## Notification routing rule

Use existing Minecraft-native Utility notifications instead of creating another popup system.

Recommended routing:

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
- one configuration switch per plugin/source mod;
- duplicate notification systems;
- shaded third-party chat mods inside the Utility Manager JAR.

## Completion criteria

The cleanup phase is complete only when:

- source ownership is documented and non-overlapping;
- the chat UX has one LazyBuilder owner;
- existing Utility behavior remains intact;
- noisy repeated warnings are suppressed safely;
- user-facing messages contain human-readable labels;
- developer details remain available outside normal chat;
- external overlapping chat/narrator mods can be removed without losing approved behavior;
- runtime verification passes on Minecraft Java 1.21.4.
