# Utilities-Manager

Utilities-Manager owns small, familiar builder conveniences that do not belong to world lifecycle, desktop process management, plugin installation, or performance tuning.

## Builder-first command contract

LazyBuilder preserves normal Minecraft/WorldEdit muscle memory. Frequently used builder commands stay short and direct:

```text
/gmc /gms /gma /gmsp
/fly [speed]
/noclip
/nightvision (/nv)
/lb
```

`/lb` is a discovery/help hub, not a replacement namespace. Vanilla commands such as `/tp`, `/time`, `/weather`, and `/gamerule`, plus familiar WorldEdit/FAWE commands, remain owned by those tools instead of being duplicated by LazyBuilder.

### `/lb`

```text
/lb
/lb help [movement|build|world|minecraft|worldedit]
/lb status     # admin diagnostics
/lb reload     # admin config reload
```

The help hub itself is safe and visible by default so a non-OP user is not left without discovery. Actual builder actions remain permission-gated and the help output only presents actions available under the current feature/config/permission state. WorldEdit/FAWE references are curated and never reimplement those commands.

## Permission model

Assign the role permission rather than OP solely for Utilities access:

```text
lazybuilder.builder
lazybuilder.admin
```

`lazybuilder.builder` grants normal Utilities command and build-helper permissions. `lazybuilder.admin` includes the builder role plus status/reload diagnostics. Fine-grained child permissions remain available.

The legacy `lazybuilder.utilities.gamemode` grant still includes all four gamemode shortcuts. Its child permissions (`.creative`, `.survival`, `.adventure`, `.spectator`) allow per-mode restrictions, including explicit negative permissions through LuckPerms. Existing role grants remain valid. `lazybuilder.utilities.help` is intentionally safe-by-default; it does not grant the actions it describes.

## Feature families

```text
Movement
  /gmc /gms /gma /gmsp
  /fly [speed]
  /noclip
  /nightvision (/nv)

Build Helpers
  Iron Door Toggle
  Double Slab Break
  Glazed Terracotta Rotate

World Safety
  Explosion block-damage protection
  Leaves decay protection
  Farmland trample protection
  Dragon egg interaction protection
```

### Movement behavior

Movement uses one per-player runtime state so Fly, Noclip, gamemode shortcuts, and Night Vision do not independently fight over player state.

- `/fly` toggles flight.
- `/fly <speed>` enables flight or updates speed while flight is already active.
- Fly speed accepts only finite values from `0.1` through `10`.
- `/noclip` temporarily enters Spectator and restores the previous game mode when disabled. It checks the resulting mode before confirming success, respecting cancellations by another plugin.
- Running a gamemode shortcut while Noclip is active ends LazyBuilder's temporary Noclip ownership and applies the requested game mode.
- Calling `/noclip` while already in normal Spectator does not create a fake Noclip session.
- Fly intent is reapplied across explicit gamemode changes and temporary Noclip transitions.
- transient state is restored on player quit and plugin shutdown.
- Night Vision relinquishes ownership when another source changes the potion effect, avoiding a stale baseline being applied over a newer potion or beacon effect.
- after an external gamemode change, Fly intent is only reapplied if the player still has LazyBuilder Fly permission.
- familiar Movement commands remain bound even when the Movement family is intentionally disabled, so users receive an explicit disabled-state response instead of ambiguous fallback behavior.

Configuration uses the simple key `abilities.fly`. The previous `abilities.advanced-fly` key is still read when `fly` is absent so existing Local configs do not silently change behavior.

### Build Helpers

Build helpers use stable Bukkit block-data APIs and only run for players with `lazybuilder.utilities.build`.

- interactive helpers accept the main hand only, preventing duplicate off-hand execution;
- Iron Door Toggle resolves both door halves and applies one logical open/close state;
- Double Slab Break leaves one bottom slab and does not create item drops in Creative;
- Glazed Terracotta Rotate rotates cardinal facing clockwise;
- the help text reflects whether the configured sneak guards are actually required.

`/lb help build` reports the current helper switches rather than documenting disabled behavior as available.

### World Safety

World Safety remains independent from World-Manager. Scope is configured locally:

```yaml
features:
  world-safety:
    scope:
      mode: all        # all | include
      include-worlds: []
      exclude-worlds: []
```

`all` protects every world except exclusions. `include` protects only explicitly included worlds, still honoring exclusions. Include mode requires at least one nonblank world while World Safety is enabled. World names are matched case-insensitively and surrounding whitespace is ignored.

`/lb help world` exposes the active protections and a safe scope summary so builders can understand non-vanilla behavior such as protected TNT or leaves. Exact include/exclude world names are only shown to users with the Utilities status permission.

## Configuration and reload

The three canonical feature sections are required. Boolean switches, scope mode, and world-list shapes are validated before activation so malformed values are rejected instead of silently becoming defaults.

`/lb reload` validates the candidate config before shutting down the current feature runtime. An unchanged effective configuration no longer tears down active Movement state. If changed settings would disrupt an active Fly/Noclip/Night Vision session, reload is deferred with an actionable message; players can finish their sessions before the admin retries. If candidate activation fails, Utilities attempts to restore the previous runtime; if rollback fails, the plugin disables rather than claiming a healthy partial state.

## Compact Debug telemetry ownership

Paper continues using its existing 40-tick conditional telemetry task. Fabric subscribes when Compact Debug is enabled and now unsubscribes when disabled, so Paper removes that subscriber immediately. No new polling service, UI screen or server-side metric database was added. Older clients can still use the original Subscribe request.

## Diagnostics

`/lb status` distinguishes feature states:

```text
READY
DISABLED
FAILED
```

A failed feature includes a concise reason. Command diagnostics verify that canonical commands are registered to the expected LazyBuilder executor rather than merely checking for a non-null Bukkit executor. Potential short-label collisions are reported separately so another plugin shadowing `/fly`, `/gmc`, or similar names is visible to admins.

## Verification

Utilities-Manager is compiled and tested through the repository Maven reactor in the integrated `Verify` workflow's `paper` job, which also builds World-Manager and stages the tested core JARs for that commit. There is no standalone `utilities` job. Verification workflows are manual-only (`workflow_dispatch`); use `DEV.cmd verify paper` for focused development checks. Source/build success does not replace the required live-server proof or the full repository readiness gate.

## Maintenance constraints

- no background polling/worker for Utilities;
- no dependency on World-Manager internals;
- Paper/Bukkit API first, no NMS without a proven requirement;
- no duplicate Vanilla/WorldEdit command families;
- command/help adapters do not become new business-rule owners;
- gameplay behavior still requires LIVE_SERVER verification even after source/build/tests are green.
