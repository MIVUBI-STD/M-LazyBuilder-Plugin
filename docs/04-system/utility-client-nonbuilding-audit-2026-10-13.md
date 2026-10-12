# Fabric Utility Manager — Non-building familiarity and reliability audit

**Scope:** Chat, connection/reconnect, accessibility and existing Settings UI on Fabric Minecraft Java 1.21.4. Deliberately **out of scope:** builder tools, editing, Axiom, command/build helpers, and new feature categories.

**Verification state:** SOURCE_IMPLEMENTED / SOURCE_REVIEWED. This document is not evidence of an integrated Fabric/Windows run. No new background worker, settings subsystem or screen family was introduced.

## User-facing contract

1. Preserve Minecraft's familiar chat, social reporting, accessibility controls, multiplayer list, and Settings interactions by default. Enhancements are opt-in and reversible wherever feasible.
2. Existing sessions retain explicit saved preferences; new profiles default to showing native chat-signing indicators, report buttons and narrator controls.
3. Reconnect reuses the attempted destination, including Direct Connection (which may lack a saved ServerInfo), without writing server addresses to a new disk store.
4. One narrow Search Settings field retains its text, focus and caret when the result list is refreshed.
5. Plain messages may be compacted conservatively, but clickable/hoverable or translated Minecraft Text, player chat and report/signing protocol are not rewritten for aesthetic purposes.
6. Feedback should use existing buttons, chat lines and short error text; avoid replacing Minecraft screens or adding redundant toolbars, mod-specific search engines or radial menus.

## Changes recorded in this batch

### P1 — Accessibility suppression originally one-way

The previous NarratorSuppressionController applied NarratorMode.OFF and disabled the narrator hotkey, without a counterpart when `Keep Narrator Off` was unchecked. The Settings UI separately set OFF immediately, before the shared preference handler could remember the previous mode. This made a switch-off unable to restore its previous value.

**Remediation:** One session-scoped controller captures the current vanilla narrator mode/hotkey at enable time; disable restores the captured values only if the imposed OFF values remain in force. Settings no longer forces OFF before this owner runs. The Minecraft options are persisted after the preference transition. When setting was already saved as enabled in an old config, it remains enabled; the previous narrator mode prior to an earlier Minecraft session is not recoverable retrospectively.

New profiles now default `suppressNarrator=false`. Explicit persisted selections still take precedence.

**Proof gap:** Reversible setting must be demonstrated on a real Fabric 1.21.4 client across enabled → disabled, repeated toggles, settings reset, session restart and vanilla Narrator choices. In a conflicting external modification, a changed value must not be silently overwritten by LazyBuilder.

### P1 — Reconnect can lose Direct Connection target

Previously ConnectScreen captured only ServerInfo, and `UtilityManagerClient` captured `client.getCurrentServerEntry()` on JOIN. Null info cleared the existing target, even when the attempted ServerAddress had been available earlier.

**Remediation:** Capture attempt accepts a valid ServerAddress when ServerInfo is null and creates a **session-only** temporary ServerInfo for the existing Minecraft ConnectScreen API. JOIN does not clear a valid prior target when its server list entry is null. Invalid new attempts clear the previous target rather than reconnecting to the wrong server.

**Proof gap:** Test saved server, Direct Connection, failed pre-JOIN attempt, redirect/join, server switch, disconnect/reconnect, cancelled reconnect, and singleplayer transitions.

### P1/P2 — New-user defaults should not hide native safety controls

The previous default `hideChatSigningIndicators=true`, `hideChatReportButton=true` and `suppressNarrator=true` reduced familiarity and disabled accessibility without an explicit new-user choice.

**Remediation:** Those three *default* preferences are now false, preserving vanilla UI for new profiles. The saved properties file still owns previous explicit values; there is no migration that overwrites an existing configuration. This changes presentation only; no chat-signing packets, reportability state or account security data are modified.

**Proof gap:** Confirm new profile defaults, existing saved values, category Reset and social screen rebuild when toggled during play. The existing report-button presentation mixin hides the widget at construction; toggling it live may require reopening the Social screen, and this should be described rather than implying immediate widget creation.

### P2 — Search Settings loses caret and overflows compact view

Search Settings rebuilds widgets on each query edit and scroll. Previously it reconstructed the search field but did not restore its caret, so editing text in the middle could jump to the end. The layout also forced a 280-pixel minimum even on narrower windows.

**Remediation:** Carry over the query text and caret into the reconstructed field, and respect a narrower available screen width. The same native TextFieldWidget, screen hierarchy and result vocabulary remain; there is no global settings database or separate search worker.

**Proof gap:** Type before the middle of an existing query, delete, select, paste, scroll, resize; check focus and keyboard accessibility on the configured Minecraft GUI scale.

## Existing behavior reviewed but intentionally unchanged

- Chat Search History is bounded (1000 rendered lines) and session scoped; Ctrl+F uses ChatScreen, not a full chat replacement.
- Duplicate collapsing is restricted to Utility-classified GAME/WARNING messages and is disabled while scrollback is active. Player CHAT is not eligible.
- The message bridge's `Text.getLiteralString()` is a Minecraft/Yarn API explicitly restricted to unstyled, sibling-free literal text. Thus the current guard is stronger than a manual `getString()` shape check and was **not** loosened.
- Unsent drafts are cleared on multiplayer disconnect, limiting accidental reuse on another server. Their single-server persistence while reopening the chat remains.
- The reporter/social signing display policy is presentation-only; no packet-signature modification was introduced.
- Settings UI is already extensive; changing its entire navigation would be a regression risk and is not authorized by this audit.

## Official API grounding

- Yarn 1.21.4 GameOptions narrator controls:
  https://maven.fabricmc.net/docs/yarn-1.21.4%2Bbuild.1/net/minecraft/client/option/GameOptions.html
- Yarn 1.21.4 Text and literal-string contract:
  https://maven.fabricmc.net/docs/yarn-1.21.4%2Bbuild.4/net/minecraft/text/Text.html
- Yarn 1.21.4 TextFieldWidget caret API:
  https://maven.fabricmc.net/docs/yarn-1.21.4%2Bbuild.8/net/minecraft/client/gui/widget/TextFieldWidget.html
- Yarn 1.21.4 ServerInfo/ServerAddress:
  https://maven.fabricmc.net/docs/yarn-1.21.4%2Bbuild.8/net/minecraft/client/network/ServerInfo.html
  https://maven.fabricmc.net/docs/yarn-1.21.4%2Bbuild.8/net/minecraft/client/network/ServerAddress.html
- Fabric ClientPlayConnectionEvents:
  https://maven.fabricmc.net/docs/fabric-api-0.119.2%2B1.21.4/net/fabricmc/fabric/api/client/networking/v1/ClientPlayConnectionEvents.html

## Cohesive acceptance batches

1. **New vs existing profile:** default native reporting/narrator indicators, save/load, no overrides of existing preferences and reset behavior.
2. **Reconnect:** saved-list server, Direct Connection, failed attempt, successive servers and JOIN metadata null; confirm no wrong-host reconnect and correct vanilla buttons.
3. **Chat + Settings:** typed mid-string search queries, keyboard focus, multiline styled/clickable chat preserved, Ctrl+F, copy context, reconnect session boundaries, and draft privacy after server change.
4. **Accessibility:** narrator mode/hotkey before/after opt-in, disable/re-enable and external modification; confirm no suppression after opt-out.

Required integrated gate: **exact-head Fabric 1.21.4 Gradle build**, native keyboard/UI acceptance, and representative Windows desktop testing. Static source checks are not a substitute. Keep these tests as a few realistic scenarios rather than multiplying superficial micro-tests.

**Next non-building subsystem:** Captures/Screenshots and resource/performance comfort, only after the current Chat/Reconnect/Accessibility/Settings UX is validated.
