---
name: lazybuilder-plugin-management
description: Own LazyBuilder-managed third-party Paper plugin lifecycle: discovery/identity/metadata/dependencies, install/update, enable/disable, duplicate handling, safe JAR removal, restart-required state, and minimum rollback. Do not use for bundled LazyBuilder core modules, desktop runtime, or presentation-only plugin UI.
---

# LazyBuilder Plugin Management

Own third-party Paper plugin lifecycle semantics. Global diagnosis/proof rules come from `docs/04-system/development-discipline.md`; cross-owner selection/handoff comes from `docs/04-system/skill-routing.md`.

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

Do not enter because a Launcher page displays plugins or Java/Paper files are touched. Bundled LazyBuilder core belongs to `lazybuilder-desktop-runtime`.

## Owns

```text
plugin discovery + canonical identity
metadata/version/dependency/compatibility interpretation
install/update/enable/disable/remove semantics
duplicate detection/resolution
restart-required state
safe JAR replacement/removal with plugin data preserved
one previous-valid rollback snapshot per replacing mutation
```

Does not own bundled core synchronization, presentation, Paper world behavior, or shared Paper↔Fabric protocol.

## Minimal context

```text
exact plugin-manager source
→ exact JAR/metadata/filesystem evidence
→ Paper live evidence only when load/enable behavior cannot be decided from source
```

Do not scan unrelated plugins, Launcher surfaces, or server history for reassurance.

## Failure taxonomy

```text
DISCOVERY      JAR missed/duplicated/grouped incorrectly
IDENTITY       canonical identity/version/source wrong
DEPENDENCY     required/optional dependency semantics wrong
COMPATIBILITY  runtime/API support state wrong
MUTATION       lifecycle mutation sequencing unsafe
DUPLICATE      competing candidates coexist/winner selection unsafe
ROLLBACK       previous-valid artifact cannot be restored safely
RESTART_STATE  claimed active state requires restart
PAPER_RUNTIME  source contract correct but Paper rejects/fails plugin
PRESENTATION   canonical lifecycle result correct; UI wrong
UNKNOWN        next separating evidence required
```

Cross-owner labels follow the global bridge in `development-discipline.md`. `PAPER_RUNTIME` changes the proof requirement, not plugin semantic ownership.

## Mutation contract

```text
PRECHECK
→ canonical identity + target + dependency/conflict state

STAGE
→ preserve one previous-valid JAR when replacement/removal needs rollback

APPLY
→ one bounded filesystem mutation path

VERIFY
→ rescan authoritative filesystem/metadata state

COMMIT
→ canonical result + restart requirement

or ROLLBACK
→ restore previous-valid state
```

A later optional refresh/UI failure must not relabel a committed mutation as failed.

## Canonical procedure

```text
name requested lifecycle result
→ capture exact plugin identity + evidence
→ classify local subtype
→ confirm Plugin Management remains first wrong owner
→ validate dependency/conflict/restart semantics
→ stage rollback only when required
→ smallest complete mutation through one path
→ rescan canonical post-mutation state
→ matching proof
→ typed handoff if ownership changes
→ STOP
```

## Invariants

- filesystem + canonical plugin metadata are primary truth;
- display filename alone is not identity when canonical metadata exists;
- no hot reload; restart-required behavior stays explicit;
- plugin data is preserved on ordinary remove/disable flows;
- categories are presentation metadata only;
- duplicate resolution is recoverable and never partially deletes candidates;
- one mutation has one transaction/rollback owner;
- rollback stores one previous-valid artifact, not unlimited history;
- action availability derives from canonical lifecycle/capability state;
- install/update/remove cannot silently mutate unrelated plugin files;
- discovery/load success and runtime correctness are distinct proof claims.

## Proof matrix

```text
identity / metadata / dependency / compatibility → EXECUTED_SOURCE
mutation ordering / rollback / duplicate         → INTEGRATION_FIXTURE
JAR compile/package structure                    → EXECUTED_SOURCE
artifact identity/contents when required         → PACKAGE_SMOKE
Paper discovery/load/enable/restart              → LIVE_RUNTIME
presentation-only state                          → lazybuilder-ui
```

A built JAR does not prove Paper discovery/enable. A restart is not proof unless the exact changed lifecycle path is exercised.

## Handoff / exit

Use canonical typed handoffs from `skill-routing.md`.

```text
to ui
→ canonical plugin identity + version + lifecycle/capability + dependency/compatibility summary + restartRequired + stable result/error

to desktop-runtime
→ workspace/server identity + bundled-core mismatch only when the artifact is LazyBuilder-managed core
```

UI must not parse JAR metadata, resolve dependencies, choose duplicate winners, or decide compatibility again.

Finish when canonical identity/lifecycle are correct, replacing work has required rollback, dependency/duplicate/restart semantics have one owner, and matching proof covers the claim. Stop before unrelated plugin cleanup, generic plugin-framework design, or new persistence layers.