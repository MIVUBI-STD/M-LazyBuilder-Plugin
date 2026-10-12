# Paper Utilities Manager — Comparative Audit (2026-10-13)

## Boundary and proof status

Reviewed `plugins/utilities-manager/` on branch `Local` after World Manager freeze.
This audit is SOURCE_REVIEW, not a passing Maven build or live Paper proof.
The earlier World Manager branch head was `06e39fe20b7cf9a7ab1609739b0f00f745cdae5f`.
Paper Utilities is a **separate runtime** from the Fabric client Utility Manager.
Do not combine their JARs, authorities or architecture locks.

References: EssentialsX per-gamemode permissions
https://www.essentialsx.net/wiki/improvements ;
WorldGuard region protection queries
https://worldguard.enginehub.org/en/latest/developer/regions/protection-query/ ;
FAWE edit region restrictions
https://intellectualsites.gitbook.io/fastasyncworldedit/customization/permissions ;
LuckPerms role/context inheritance
https://luckperms.net/wiki/Usage .

## Retain — familiar and low-overhead

- `/lb` discovery/help, `/gmc /gms /gma /gmsp /fly /noclip /nightvision /nv` muscle memory.
- Feature families: Movement, passive Build Helpers and World Safety.
- One per-player Movement state with cleanup on quit/shutdown.
- Command collision diagnostics and fail-closed config reload.
- World Safety include/exclude scope and explicit feature configuration.
- No duplicate Vanilla, WorldEdit or FAWE commands.
- Builder Extension protocol caps (`MAX_BATCH_ENTRIES=256`, message and payload limits),
  dimension matching, compare-before-write and rollback attempts.
- No second background scheduler or world registry for builder convenience.

## Findings, ordered by impact

### P1 — Builder Extension has broader authority than ordinary builder conveniences

**Observed:** `plugin.yml` makes `lazybuilder.utilities.builder-extension` a
child of `lazybuilder.builder`. PaperBuilderExtensionPayloadAdapter requires
that one permission, then mutates biome, entities and block-entity NBT through
direct server APIs. The reviewed mutation paths check matching dimension and
before/after state but do not query region-protection policy.

**Consequence:** on a shared server using WorldGuard, PlotSquared or FAWE
region limits, granting the generic builder role can also grant advanced
non-block authority with no demonstrated per-region restriction. This is
an authorization/integration risk; live bypass is NOT YET REPRODUCED.

**Plan:** separate ordinary utilities from trusted editor authority at the
permission-policy layer, preserving existing installations through an explicit
migration. Add one server-side per-position edit-authorization boundary,
reusing region-provider adapters rather than inventing a second region system.
Fail closed if a present required protection integration cannot answer.
Preflight the entire batch before changes; never silently skip denied entries.
Do not automatically remove existing builder-extension rights before a
compatibility and user-role migration has been reviewed.

### P1 — Entity Batch has potentially unbounded main-thread scans

**Observed:** `markedEntitiesInWorld` iterates `world.getEntities()` for
each mutation and also during state verification / rollback. Up to 256 mutations
can arrive in one protocol batch. Entity placement/removal also occurs on the
server thread.

**Consequence:** CPU load is a function of (world entity count × operations),
not merely MAX_BATCH_ENTRIES. This can delay ticks for very large maps.

**Plan:** one request-local indexed view of Builder-owned entity markers,
plus bounded slot queries. Use a clear mutation journal, update the request
index after success/failure, and retain conflict detection before writes.
Prefer an explicit per-batch processing/time budget over per-entry full-world scans.
Do not add an always-on entity registry, global index, or polling worker.

### P1/P2 — Build Helpers need an explicit protection compatibility contract

**Observed:** helpers mutate blocks via `Block.setBlockData` or manually
drop items during `PlayerInteractEvent` / `BlockBreakEvent`. They honor the
event's cancellation state at their listener priority; they do not independently
query configured plot/region editing permissions.

**Risk:** interaction priorities, bypass permissions, and other protection
plugins can have semantics that are not equivalent to these utility mutations.
It is not proven that all cross-plugin combinations deny edits reliably.

**Plan:** define expected edit permission and protection ownership per helper.
When WorldGuard/plots are used, verify that a permission-denied region never
changes a door, slab or glazed terracotta, and no item drops occur. Add adapter
logic only if the actual integration proves a gap; do not simulate arbitrary
block events or assume a permission node implies region access.

### P2 — Gamemode permission too broad for shared servers

**Observed:** all four shortcuts currently require the single
`lazybuilder.utilities.gamemode` node. EssentialsX instead supports
independent permission nodes per target gamemode.

**Plan:** preserve commands, add target-mode permission policy compatible with
the existing role, and migrate in a documented, nonbreaking manner. In the help
hub, only suggest modes available to the current user. A builder may need
Creative but not Spectator, or vice versa. Do not introduce another command UI.

### P2 — The World Safety default is global

**Observed:** default scope is `all`, so explosion block damage, leaves
decay, farmland trampling and dragon egg teleport protection apply to all
worlds unless the operator excludes them.

**Risk:** intentional gameplay mechanics in imported adventure/minigame worlds
can be changed silently. The feature is working as configured, not defective.
Provide a clear first-run scope explanation and preserve world include/exclude
settings. Do not automatically infer gameplay types or modify world ownership.

### P2 — Movement/potion ownership across third-party changes

**Observed:** Fly, Noclip and Night Vision restore captured player state.
Restoring captured Night Vision can overwrite a newer third-party effect.
On gamemode transitions, Fly schedules a next-tick reapply.

**Plan:** check observed effect identity/state before restoring and only
restore the previous baseline when LazyBuilder still owns that effect;
preserve externally granted player changes. Test with EssentialsX, beacons,
gamemode changes, disconnect, and permission removal.

### P2 — Help hub short-command collision

**Observed:** `/lb` quick-link availability used only settings/permission,
even when short command labels were shadowed by another plugin. This makes
a suggested `/fly` potentially invoke a different plugin.

**Patched:** the quick-link availability policy now also checks the existing
`isCommandReady` ownership test for Fly, Noclip, Night Vision and Creative.
This prevents misleading one-click entries without renaming familiar commands.
Remaining: verify `/nv` alias resolution and namespaced fallback on live Paper.

### P2 — Native NMS block-entity bridge is version-sensitive

**Observed:** `PaperBlockEntityNbtBridge` reflectively locates 1.21.4 NMS
methods; it disables that capability if startup reflection fails. This is
correct fail-closed behavior, but requires exact Paper-build compatibility
evidence before advertising NBT functionality.

**Plan:** test target Paper build, invalid NBT, conflict rollback, loaded/unloaded
chunk behavior, maliciously large but protocol-valid payloads and multi-block
batch failure. Do not replace with unchecked NMS writes or hide failure as
success.

## Product UX acceptance contract

- `/lb` stays the simple entry point. Feature-disabled and command-conflict
  states are actionable, not raw implementation identifiers.
- Normal builders receive only their intended capabilities; advanced builder
  mutation access must not be hidden in a convenience role.
- Build helper interactions remain familiar and should not mutate protected
  plots or create phantom item drops.
- Protection scope should be understandable to users without exposing
  privileged lists of restricted world names.
- Expensive entity or NBT work must not freeze the game or suggest success
  after a partial failure.
- Operator diagnostics and detailed logs stay separate from ordinary chat.
- No extra generic Builder Manager, permanent HUD, new keybinds or polling service.

## Execution order — large coherent batches

1. **Authority and conflict boundary:** role matrix, per-region mutation
   authorization, candidate preflight and representative protected-plot
   integration; include Builder Extension and passive helpers.
2. **Bounded batch performance:** eliminate repeated full-world entity scans,
   set predictable workload ceilings while preserving transaction behavior.
3. **Builder UX:** target-mode permission help, short-command alias resolution,
   potion/flight ownership and clear safety-scope explanation.
4. **Proof gate:** same-commit Maven test, Paper 1.21.4 integration with
   EssentialsX / WorldGuard / FAWE or PlotSquared as applicable, and
   server-tick/rollback evidence on a representative large world.

Do not infer live correctness from static checks or prior commits. Prefer
a small set of end-to-end integration scenarios over many superficial microtests.
