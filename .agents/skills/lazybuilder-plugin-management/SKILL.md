---
name: lazybuilder-plugin-management
description: Own LazyBuilder-managed third-party Paper plugin lifecycle: discovery/identity/metadata/dependencies, install/update, enable/disable, duplicate handling, safe JAR removal, restart-required state, and minimum rollback. Do not use for bundled LazyBuilder core modules, desktop runtime, or presentation-only plugin UI.
---

# LazyBuilder Plugin Management

Own third-party Paper plugin lifecycle semantics. Follow `docs/04-system/development-discipline.md` and `docs/04-system/skill-routing.md`.

## Entry gate

Use this Skill only when the decision changes third-party plugin lifecycle truth:

```text
plugin discovery / canonical identity / metadata
required/optional dependency semantics
compatibility state
install / update / enable / disable / remove
duplicate/conflicting JAR resolution
restart-required lifecycle state
rollback of a plugin mutation
```

Do not enter because a Launcher page displays plugins or Java/Paper files are touched. Presentation stays with `lazybuilder-ui`; bundled LazyBuilder core stays with `lazybuilder-desktop-runtime`.

Before mutation, identify exact plugin identity plus the smallest filesystem/metadata evidence that separates source artifact, resolver, mutation sequencing, runtime, and UI failure.

## Owns

```text
plugin discovery and canonical identity
metadata/version/dependency/compatibility interpretation
install/update/enable/disable/remove semantics
duplicate detection/resolution
restart-required state
safe JAR replacement/removal with plugin data preserved
one previous-valid rollback snapshot per replacing mutation
```

## Does not own

```text
bundled World/Utilities core synchronization → lazybuilder-desktop-runtime
plugin presentation/UI                     → lazybuilder-ui
Paper world behavior                       → lazybuilder-world-management
shared Paper/Fabric protocol               → lazybuilder-protocol
```

## Minimal context

Use only:

```text
development-discipline.md
→ skill-routing.md when ownership is unclear
→ exact plugin-manager source
→ exact JAR/metadata/filesystem evidence
→ Paper live evidence only when source cannot decide load/enable behavior
```

Do not scan unrelated plugins, Launcher surfaces, or server history for reassurance.

## Failure taxonomy

```text
DISCOVERY          JAR missed, duplicated, or grouped incorrectly
IDENTITY           canonical plugin identity/version/source is wrong
DEPENDENCY         required/optional dependency semantics are wrong
COMPATIBILITY      supported runtime/API state is wrong
MUTATION           install/update/enable/disable/remove sequencing is unsafe
DUPLICATE          competing candidates coexist or winner selection is unsafe
ROLLBACK           previous-valid artifact cannot be restored safely
RESTART_STATE      source/UI claims an active state that requires restart
PAPER_RUNTIME      source contract is correct but Paper rejects/fails the plugin
PRESENTATION       lifecycle result is correct; UI is stale/misleading
UNKNOWN            evidence cannot separate the above
```

Local labels refine global classification only while Plugin Management remains the first wrong owner. `PRESENTATION` reclassifies to `UI_PRESENTATION`; `PAPER_RUNTIME` maps to the global live-runtime proof class without changing plugin semantic ownership; `ROLLBACK` may additionally require global `RECOVERY`; `UNKNOWN` must name the next separating evidence.

For `UNKNOWN`, gather the smallest separating evidence; never add fallback resolution logic just to continue.

## Mutation contract

Replacing/destructive plugin operations follow one transaction owner:

```text
PRECHECK
→ resolve canonical identity, target, dependency/conflict state

STAGE
→ preserve one previous-valid JAR when replacement/removal needs rollback

APPLY
→ perform one bounded filesystem mutation path

VERIFY
→ rescan authoritative filesystem/metadata state

COMMIT
→ report canonical result + restart requirement

or ROLLBACK
→ restore previous-valid state when mutation did not commit safely
```

Do not report a committed mutation as failed only because a later optional refresh/UI update fails.

## Canonical procedure

```text
name requested lifecycle result
→ capture exact plugin identity + current filesystem/metadata evidence
→ classify failure
→ find first wrong lifecycle owner
→ validate target/dependencies/conflicts/restart semantics
→ stage rollback state only when replacement/removal requires it
→ perform smallest complete mutation through one path
→ rescan authoritative post-mutation state
→ prove changed lifecycle claim at the cheapest sufficient level
→ hand presentation residue to lazybuilder-ui
→ STOP
```

## Invariants

- filesystem + canonical plugin metadata are primary truth; no plugin database without proven need;
- display filename alone is not identity when canonical metadata exists;
- no hot reload; restart-required behavior stays explicit;
- plugin data is preserved on ordinary remove/disable flows;
- categories are presentation metadata, never lifecycle authority;
- duplicate resolution must be recoverable and cannot partially delete competing candidates;
- one mutation has one transaction/rollback owner;
- rollback stores one previous-valid artifact, not unlimited history;
- action availability derives from canonical lifecycle/capability state, not button-local assumptions;
- late refresh failure cannot rewrite an already committed lifecycle result;
- install/update/remove must not silently mutate unrelated plugin files;
- plugin discovery/load success and plugin runtime correctness are distinct proof levels.

## Proof matrix

```text
identity / metadata / dependency / compatibility rule
→ focused unit/source-contract test

mutation ordering / rollback / duplicate resolution
→ filesystem fixture or focused integration test

compile/package compatibility
→ repository build/artifact proof

Paper discovery/load/enable behavior
→ LIVE_SERVER using exact tested JAR/runtime

visual/list/detail/action state only
→ lazybuilder-ui proof lane
```

## Handoff / exit contract

```text
canonical plugin lifecycle result/capability
→ lazybuilder-ui
handoff: canonical plugin identity + version + lifecycle/capability + dependency/compatibility summary + restartRequired + stable result/error
UI must not parse JAR metadata, resolve dependencies, choose duplicate winners, or decide compatibility again

bundled LazyBuilder core compatibility/synchronization
→ lazybuilder-desktop-runtime
handoff only the workspace/server identity + observed bundled-core mismatch; bundled core is not treated as a third-party plugin
```

Finish when:

- canonical plugin identity and lifecycle result are correct;
- destructive/replacing work has the required rollback boundary;
- dependency/duplicate/restart semantics have one owner;
- matching proof is complete at the available context ceiling;
- next owner can consume the canonical result without rescanning plugin truth;
- remaining Paper live proof or UI residue is named precisely.

Do not continue into unrelated plugin cleanup, generic plugin-framework design, or new persistence layers.