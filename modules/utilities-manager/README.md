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
/lb help [movement|build|minecraft|worldedit]
/lb status     # admin diagnostics
/lb reload     # admin config reload
```

The player-facing help stays concise. When WorldEdit/FAWE is present, `/lb` can point builders toward a small curated set of familiar commands without reimplementing them.

## Permission model

Assign the role permission rather than OP solely for Utilities access:

```text
lazybuilder.builder
lazybuilder.admin
```

`lazybuilder.builder` grants normal Utilities command and build-helper permissions. `lazybuilder.admin` includes the builder role plus status/reload diagnostics. Fine-grained child permissions remain available for servers that need them.

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
- `/noclip` temporarily enters Spectator and restores the previous game mode when disabled.
- Running a gamemode shortcut while Noclip is active ends LazyBuilder's temporary Noclip ownership and applies the requested game mode.
- Calling `/noclip` while already in normal Spectator does not create a fake Noclip session.
- Fly intent is reapplied across explicit gamemode changes and temporary Noclip transitions.
- transient state is restored on player quit and plugin shutdown.

### Build Helpers

Build helpers use stable Bukkit block-data APIs and only run for players with `lazybuilder.utilities.build`.

- interactive helpers accept the main hand only, preventing duplicate off-hand execution;
- Iron Door Toggle runs on right-click;
- Double Slab Break uses the sneak guard by default, leaves one bottom slab, and does not create item drops in Creative;
- Glazed Terracotta Rotate uses sneak + right-click by default and rotates cardinal facing clockwise.

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

`all` protects every world except exclusions. `include` protects only explicitly included worlds, still honoring exclusions.

## Configuration and reload

The three canonical feature sections are required. Missing entire sections or invalid World Safety scope modes are rejected instead of silently enabling defaults under a typo.

`/lb reload` validates the candidate config before shutting down the current feature runtime. If validation fails, the current runtime is kept.

## Maintenance constraints

- no background polling/worker for Utilities;
- no dependency on World-Manager internals;
- Paper/Bukkit API first, no NMS without a proven requirement;
- no duplicate Vanilla/WorldEdit command families;
- command/help adapters do not become new business-rule owners;
- gameplay behavior still requires LIVE_SERVER verification even after source/build/tests are green.
