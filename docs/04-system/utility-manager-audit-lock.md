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
- presentation-only chat-signing indicator suppression;
- narrator suppression through Minecraft's own narrator options/hotkey;
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

| Class | Purpose | Default destination |
| --- | --- | --- |
| `CHAT` | player communication | chat |
| `GAME` | join/advancement/gameplay feedback | compact chat |
| `SYSTEM` | concise player-relevant LazyBuilder/server state | compact chat or toast |
| `WARNING` | actionable conflict/degraded behavior | compact chat/toast + detailed log |
| `ERROR` | actionable failure | chat/toast + detailed log |

Developer-only diagnostics must not be routed into normal chat.

## External utility migration matrix

Third-party source/JARs are references for requirements, not code to bundle into LazyBuilder.

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
| Signing/reporting protocol compatibility | `REBUILD` | intentionally not implemented | keep isolated from presentation; do not alter packets/reports without a separately verified requirement |
| Signing indicator hiding | `REBUILD` | implemented | suppress only `MessageIndicator` presentation; preserve signatures and packet behavior |
| Narrator suppression | `REBUILD` | implemented | set Minecraft narrator mode `OFF` and disable narrator hotkey when Utility suppression is enabled; keep accessibility infrastructure intact |
| Third-party config/UI duplication | `DROP` | migration-ready | use Utility Manager preferences and Minecraft-native surfaces |
| Third-party background workers/indexers | `DROP` | locked | do not introduce unless a concrete verified requirement exists |
| Plugin-specific chat prefixes as primary taxonomy | `DROP` | locked | classify by message purpose instead |
| Repeated raw translation keys in user chat | `DROP` | implemented foundation | resolve human-readable names; retain raw key only in diagnostics |

## External mod retirement status

Retirement is based on behavior coverage, not on mod name. A third-party JAR is removable only after every behavior intentionally retained from it has either been rebuilt or explicitly dropped and the replacement passes runtime verification.

| External mod | Status | Reason |
| --- | --- | --- |
| Chat Patches | `NEAR RETIREMENT` | approved core UX now has first-party coverage: history, draft, timestamps, search, context copy, session boundaries, compact presentation, and duplicate collapse. Runtime verification and final feature-gap review remain. |
| Chat Signing Hider | `NEAR RETIREMENT` | Utility Manager now suppresses the vanilla `MessageIndicator` at presentation time without altering message signatures or packets. Runtime verification with signed/unsigned chat remains before removal. |
| No Chat Reports | `KEEP FOR NOW` | it changes substantially more than presentation and touches signing/reporting protocol behavior. Utility Manager deliberately does not claim equivalence. Packet/reporting behavior requires an independent requirement and compatibility audit. |
| Narrus Yeetus | `NEAR RETIREMENT` | Utility Manager now has first-party narrator suppression using Minecraft's own narrator mode/hotkey. Runtime verification must confirm this also eliminates the specific unwanted narrator behavior/errors before removing the external mod. |

No external JAR should be shaded, unpacked, decompiled into, or source-copied into Utility Manager as a migration shortcut.

## Priority order

1. message classification and routing contract;
2. warning/system deduplication;
3. human-readable warning/error presentation;
4. session boundary handling;
5. timestamp presentation;
6. chat search;
7. contextual actions;
8. presentation-only signing compatibility;
9. narrator/accessibility migration;
10. runtime verification and external-mod retirement;
11. separately decide whether No Chat Reports protocol behavior is still a product requirement.

Steps 1-9 now have first-party implementations. Current priority is runtime verification, then removal of external mods whose retained behavior is fully covered. No Chat Reports stays separate from this retirement decision because its packet/reporting scope is materially broader.

## Keybind-warning rule

A conflict should produce at most one concise user-facing warning per stable conflict fingerprint in a session. Human-readable action names should be shown. Raw translation keys and subsystem implementation identifiers belong in diagnostics.

## Command-error rule

Command errors must preserve correctness while reducing Brigadier noise. The original command and detailed parser context remain available in diagnostics/logging when needed.

## Dedupe rule

`CHAT` is never automatically collapsed. The ChatHud adapter only collapses messages explicitly marked by the classifier/Utility path. Lookalike plugin text must not be collapsed by text shape alone.

## Signing boundary

Signing presentation and signing protocol are separate concerns.

Utility Manager may hide a vanilla chat signing indicator when configured, but must not strip signatures, rewrite signing packets, disable reporting protocol, or claim No Chat Reports equivalence as a side effect of UI cleanup. Any future protocol-level change requires its own compatibility/security audit.

## Accessibility boundary

Narrator suppression must use Minecraft's supported narrator options where possible. Utility Manager must not remove accessibility classes, screens, or APIs. Disabling the narrator hotkey is part of suppression so accidental toggles do not restore narration during normal builder use.

## Rejected directions

Do not introduce Discord-style replacement chat, avatars, custom fonts as a requirement, animated cards, database-backed chat indexing, chat background workers, large replacement `ChatHud`/`ChatScreen`, duplicate notification systems, or shaded third-party chat mods.

## Completion criteria

The cleanup phase is complete only when source ownership is non-overlapping, the chat UX has one LazyBuilder owner, existing Utility behavior remains intact, external replacements pass Minecraft Java 1.21.4 runtime verification, and any still-retained protocol/security mod has an explicit documented reason to remain.
