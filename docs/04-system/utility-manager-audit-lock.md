# Utility Manager Audit Lock

## Scope

This audit locks the current cleanup target for passive client utilities, with chat as the immediate priority.

Utilities Manager is the consolidation destination for small passive Fabric utilities that fit its boundary. Consolidation means one maintained product surface and one client JAR, while internal source remains modular.

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
| Signing/reporting compatibility | `REBUILD` | pending | isolate under `chat/signing`; verify protocol behavior independently |
| Signing indicator hiding | `REBUILD` | pending | presentation-only behavior must not own packet logic |
| Narrator suppression | `REBUILD` | pending | move to accessibility helper boundary; do not remove accessibility infrastructure |
| Third-party config/UI duplication | `DROP` | migration-ready | use Utility Manager preferences and Minecraft-native surfaces |
| Third-party background workers/indexers | `DROP` | locked | do not introduce unless a concrete verified requirement exists |
| Plugin-specific chat prefixes as primary taxonomy | `DROP` | locked | classify by message purpose instead |
| Repeated raw translation keys in user chat | `DROP` | implemented foundation | resolve human-readable names; retain raw key only in diagnostics |

## External mod retirement status

Retirement is based on behavior coverage, not on mod name. A third-party JAR is removable only after every behavior we intentionally retain from it has either been rebuilt or explicitly dropped and the replacement passes runtime verification.

| External mod | Status | Reason |
| --- | --- | --- |
| Chat Patches | `NEAR RETIREMENT` | approved core UX now has first-party coverage: history, draft, timestamps, search, context copy, session boundaries, compact presentation, and duplicate collapse. Runtime verification and final feature-gap review are still required before removal. Persistent chat-log/database-style behavior is not an approved requirement. |
| Chat Signing Hider | `KEEP FOR NOW` | signing-indicator behavior has not yet been rebuilt independently. Do not remove until presentation-only signing compatibility is verified. |
| No Chat Reports | `KEEP FOR NOW` | this mod changes substantially more than presentation and touches signing/reporting protocol behavior. Utility Manager does not yet provide an equivalent, and no packet-level behavior should be copied blindly. |
| Narrus Yeetus | `KEEP FOR NOW` | narrator/accessibility suppression has not yet been rebuilt under the Utility accessibility boundary. |

No external JAR should be shaded, unpacked, or source-copied into Utility Manager as a migration shortcut.

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

Steps 1-7 now have first-party implementations. Current development priority therefore moves to signing compatibility and narrator/accessibility migration, while continuing runtime verification of the completed chat UX.

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
