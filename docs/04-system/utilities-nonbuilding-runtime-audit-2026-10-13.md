# Utilities — Non-Building Runtime Audit (2026-10-13)

**Scope:** Paper Utilities Manager Movement, commands/permissions/help, reload/session lifecycle, and Paper↔Fabric Compact Debug telemetry. Fabric Utility client is inspected only for the telemetry lifecycle. **Explicitly deferred:** Builder Extension, building/editing/selection, Build Helpers, World Safety build-protection decisions and all unrelated builder tools.

**Repository source:** `Local` at `cf905b712d5c071c3ae3a4b4eead122909b804cf` at review start. Follow-up changes require rereading HEAD. **Evidence:** source review + official documentation; Maven, Fabric, Paper + EssentialsX/LuckPerms runtime verification **not executed**.

## Existing design worth preserving

- Familiar commands `/gmc /gms /gma /gmsp /fly [speed] /noclip /nightvision (/nv)`; `/lb` is an optional discovery/help surface rather than a replacement language.
- `MovementFeature` already centralizes Fly, temporary Noclip and Night Vision state by player UUID. Fly multiplier is finite and bounded, and modes use the Bukkit API.
- Noclip refuses to create a fake session if the player is already in Spectator.
- Movement events are deregistered on disable; per-player state is cleared on quit.
- Utilities reload first parses/validates a replacement config, then tries to restore the previous runtime if candidate activation fails. A rollback failure disables the plugin rather than advertising full health.
- Feature enable is modular. Command label collision detection, per-feature READY/DISABLED/FAILED status and safe-to-view `/lb` are already present.
- Paper server telemetry sends a bounded 512-byte wire format only to subscribed players and samples once per 40 ticks; Fabric treats stale snapshots as unavailable after six seconds.

## Prioritized blind spots

### P1 — Gamemode success is claimed without confirmed mode change

Source: `MovementFeature.handleNoclip`, `handleGameMode`, `changeGameModeInternally`.

The feature sets its own `noclipActive` and baseline state before or during
`player.setGameMode`, then reports success without checking that
`player.getGameMode()` actually matches the requested mode. Paper 1.21.4
`PlayerGameModeChangeEvent` is cancellable by another plugin.

**Required behavior:** After requesting a mode transition, reconcile the
observable result before changing the session ownership and claiming success.
When denied, preserve the original baseline and show one concise explanation.
Do not bypass another plugin's cancellation or retry indefinitely.

### P1 — Reload disrupts active Fly sessions

Source: `UtilitiesManagerPlugin.reloadUtilities` calls `oldRegistry.disableAll()`;
`MovementFeature.disable()` restores all online players, clears all state,
and only then new Movement state is installed.

Even an unchanged Movement setting causes a disconnect between the command
status and active flight intent. In-world reload can disable Fly abruptly for
a player in the air. Source proves the teardown; gameplay/fall consequences
still require Paper runtime reproduction.

**Required behavior:** Treat an unchanged Movement configuration as
non-disruptive. For a real disabling change, decide an explicit player-safety
policy without silently adding flight rights or teleporting players. Preserve
the existing transactional configuration rollback and one runtime owner.

### P1 — Night Vision restores stale state over newer external effects

Source: `MovementFeature.handleNightVision`, `restoreNightVision`.

When enabled, LazyBuilder snapshots an earlier potion effect. On off/quit/
disable it unconditionally removes the current NIGHT_VISION and reapplies the
captured one. A beacon, potion or other plugin can replace that effect while
LazyBuilder's state flag stays set. Paper 1.21.4
`EntityPotionEffectEvent` exposes the old/new effect and change cause.

**Required behavior:** Revert only if LazyBuilder still owns the active
effect. If another source changed it, relinquish the ownership and do not
replace the new effect with a stale snapshot. Keep mutation observation
event-driven, not a polling loop.

### P1/P2 — Fly intention can override a more recent policy

Source: `MovementFeature.onGameModeChange` schedules next-tick
`applyFly` after an external gamemode change and does not re-evaluate current
permission/world policy. It also captures `flightBaseline` after external mode
changes. This is valid when Fly is meant to span modes, but potentially
incompatible with restricted survival/adventure worlds, permission revocation
or another plugin's flight ownership.

**Required behavior:** Define precedence before coding:
(1) explicit operator/permission denial;
(2) actual Paper player gamemode/world state;
(3) active LazyBuilder Fly intent;
(4) captured baseline restore only while still owned.
An editor convenience cannot silently reinstate a right newly revoked by
server policy. Test cross-world and external gamemode changes.

### P2 — Granularity of gamemode permissions

Current `/gmc /gms /gma /gmsp` all require
`lazybuilder.utilities.gamemode`. EssentialsX supports separate
`essentials.gamemode.creative`, `.survival`, `.adventure`, `.spectator`
nodes in addition to its parent permission.

**Required behavior:** Offer target-mode permissions under the existing
Utilities permission family with a documented non-breaking migration for
existing servers. No new command aliases, roles database or OP requirement.
Use LuckPerms world/server contexts if already installed; do not create a
second role engine.

### P2 — Observability confuses command registration with usability

`UtilitiesManagerPlugin.isCommandReady()` checks registration/executor/label,
not whether Movement is enabled or user permissions grant an action.
`/lb status` currently says `Commands: N/N ready` even when Movement
is intentionally DISABLED; feature status is shown separately. Rename this
diagnostic to `Command bindings` or clearly distinguish registration
from usable actions. The quick menu now checks canonical labels, but the
`/nv` alias is not independently checked for third-party conflicts.

### P2 — Compact Debug subscribe has no matching unsubscribe

Paper telemetry protocol v1 supports `Subscribe`, not `Unsubscribe`.
Server stores subscriber UUIDs until quit/stop and pushes every 40 ticks.
Fabric `UtilityManagerClient.updatePreferences` clears the Compact Debug
state when disabled but sends no unsubscribe, so Paper continues sending
while the HUD is hidden. Joining with HUD disabled avoids subscribing,
which is good.

**Required behavior:** Introduce explicit unsubscribe or bounded, renewed
subscriptions using an additive compatibility policy; stop server work
when the last subscriber leaves and avoid sending to hidden HUD clients.
Do not add a permanent background worker. Existing 40-tick conditional
timer is adequate if subscription ownership is correct.

### P2 — Login, logout and failure recovery

`MovementFeature.onPlayerQuit` calls `restorePlayer`, including reverting
Spectator and Fly; this is intentionally transient behavior. Test quitting
while airborne, reconnecting under Survival, plugin shutdown, world transfer
and disconnect during permission change. Do not automatically keep elevated
abilities across sessions or rewrite player saved gamemodes.

## Comparison references

- EssentialsX official permissions and per-gamemode access:
  https://www.essentialsx.net/permissions
  https://www.essentialsx.net/wiki/improvements
- LuckPerms inheritance and contexts:
  https://luckperms.net/wiki/Usage
- Paper 1.21.4 cancellable gamemode changes:
  https://jd.papermc.io/paper/1.21.4/org/bukkit/event/player/PlayerGameModeChangeEvent.html
- Paper 1.21.4 potion-effect change event:
  https://jd.papermc.io/paper/1.21.4/org/bukkit/event/entity/EntityPotionEffectEvent.html

Use the **principles**, not a wholesale implementation or additional
external runtime dependency. Maintain the existing Minecraft command muscle
memory, server authority and modest default UI.

## UX policy

For all Movement actions:
- Explicit command acknowledgement must describe what *actually happened*,
  not what was merely requested.
- If a third-party plugin rejects an action, say so and leave the original
  player state unchanged.
- No unexpected fall from an unchanged configuration reload.
- Night Vision should not erase potion/beacon state applied by another source.
- `/lb` should reveal only available commands and never make a shadowed alias
  look like a working LazyBuilder shortcut.
- Error detail belongs in console; normal player chat gets one short,
  actionable line. Do not add a new menu, HUD or persistent status loop.

## Batch-level acceptance, not many microtests

1. **Two-plugin player-state scenario:** EssentialsX + LazyBuilder;
   Fly, /noclip, cancelled gamemode transition, a plugin-modified potion effect,
   permission revocation and world change. Verify resulting player state and
   acknowledgement after each user-visible action.
2. **Reload and lifecycle scenario:** one airborne player and one Spectator
   Noclip player; reload with identical config, changed config, malformed
   config and failed activation. Confirm no unexpected fall and truthful
   rollback/feature status.
3. **Permission and command scenario:** LuckPerms per-world contexts,
   Creative-only role, Spectator-denied role, /fly and /nv alias collisions;
   verify command execution, suggestions, /lb help and diagnostics agree.
4. **Telemetry scenario:** compact debug on/off, reconnect, changing world,
   disconnect, mixed mod versions; verify subscription count, update cadence,
   server overhead and no stale/hidden HUD updates.

Run the exact same commit through Maven, Fabric and Paper 1.21.4. A test source
file, old GitHub Action run or static inspection must not be represented as
live proof.

## Deferred until requested

All building/editing and Builder Extension/Build Helper/World Safety mutation
work; new user-facing command suites; custom permission database; global
state polling; chat/signing protocol changes. The next independent non-building
client audits may cover **Fabric Utility Manager chat, reconnect, accessibility,
settings UX and capture** separately, one coherent subsystem per pass.
