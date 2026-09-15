---
name: lazybuilder-plugin-management
description: Own LazyBuilder-managed third-party Paper plugin lifecycle: scan/metadata/dependencies, install/update, enable/disable, duplicate handling, safe JAR removal, and minimum rollback state. Do not use for bundled LazyBuilder core modules, desktop runtime, or presentation-only plugin UI.
---

# LazyBuilder Plugin Management

Own third-party Paper plugin lifecycle semantics. Follow `docs/04-system/development-discipline.md` and `docs/04-system/skill-routing.md`.

## Entry gate

Use this Skill only when the decision changes third-party Paper plugin lifecycle truth.

```text
plugin discovery / identity / metadata / dependency semantics
install / update / enable / disable / remove
conflicting or duplicate JAR resolution
rollback of a plugin mutation
```

Do not enter merely because a Launcher page displays plugins or because Java/Paper files are touched. Presentation stays with `lazybuilder-ui`; bundled LazyBuilder core remains `lazybuilder-desktop-runtime`.

Before mutation, identify the first evidence that can distinguish:

```text
bad source artifact
bad detected identity/version
unsatisfied dependency
conflicting duplicate
unsafe mutation sequencing
restart-required state
Paper runtime/load failure
UI-only stale presentation
```

## Owns

```text
plugin discovery/metadata/dependency validation
install/update
enable/disable
safe JAR removal while preserving plugin data
duplicate detection/resolution
one previous-valid rollback snapshot per plugin mutation
```

## Does Not Own

```text
bundled World/Utilities core sync → lazybuilder-desktop-runtime
plugin presentation/UI            → lazybuilder-ui
Paper world behavior              → lazybuilder-world-management
shared client/server protocol     → lazybuilder-protocol
```

If lifecycle semantics change and the UI message/control follows, Plugin Management decides the result first; `lazybuilder-ui` only presents it.

## Canonical Context

1. `docs/04-system/development-discipline.md`
2. `docs/04-system/skill-routing.md`
3. exact plugin-manager source
4. exact filesystem/metadata evidence needed to separate the failure
5. system docs only when ownership changes

Do not scan unrelated plugins, Launcher surfaces, or server history when one plugin identity/mutation path can answer the question.

## Failure patterns

Classify the domain failure before editing:

```text
DISCOVERY          plugin JAR is missed, duplicated, or incorrectly grouped
IDENTITY           name/version/provider/source is resolved incorrectly
DEPENDENCY         required/optional dependency semantics are wrong
COMPATIBILITY      supported runtime/API compatibility is represented incorrectly
MUTATION           install/update/enable/disable/remove sequencing is wrong
DUPLICATE          competing JARs can coexist or unsafe winner selection occurs
ROLLBACK           previous-valid artifact cannot be restored safely
RESTART_STATE      UI/runtime claims active state that requires restart
PAPER_RUNTIME      source contract is correct but Paper rejects/fails the plugin
PRESENTATION_ONLY  canonical lifecycle result is correct; UI is stale/misleading
UNKNOWN            current evidence cannot separate the above
```

For `UNKNOWN`, gather the smallest separating evidence; do not add fallback logic.

## Procedure

```text
identify requested lifecycle result
→ capture exact plugin identity + current filesystem/metadata evidence
→ classify failure
→ find first unsafe/duplicated semantic owner
→ reuse one mutation gate/path
→ validate target/dependencies/conflicts before destructive mutation
→ stage previous-valid rollback state when mutation can replace/remove a JAR
→ perform smallest complete mutation
→ verify canonical post-mutation state
→ targeted proof at the cheapest sufficient level
→ hand presentation-only residue to lazybuilder-ui
→ STOP
```

## Plugin Invariants

- filesystem + plugin metadata remain primary truth; no plugin database without a proven need;
- no hot reload; restart-required semantics stay explicit;
- duplicate resolution cannot partially delete candidates without recoverability;
- persistent rollback state is one previous-valid JAR snapshot, not historical backup retention;
- plugin-data deletion/quarantine is outside current product flow; remove preserves plugin data;
- categories are derived presentation metadata, never a persisted runtime authority;
- one plugin mutation has one transaction/rollback owner;
- plugin identity must be stable across list/detail/action paths; display filename alone is not sufficient authority when canonical metadata exists;
- a failed refresh after a committed mutation must not relabel the mutation itself as failed;
- capability/action availability derives from canonical lifecycle state, not UI-local assumptions.

## Proof matrix

Use the cheapest evidence capable of disproving the changed claim:

```text
metadata parsing / identity / dependency rule
→ focused unit/source-contract test

transaction ordering / rollback selection / duplicate resolution
→ focused filesystem fixture or integration test

compile/package compatibility
→ repository build/CI artifact proof

Paper discovery/load/enable behavior
→ LIVE_SERVER with the exact tested JAR/runtime

Launcher/Minecraft visual state only
→ hand result to lazybuilder-ui for its proof lane
```

A successful scan does not prove loadability. A successful compile does not prove Paper enable/runtime behavior.

## Handoff / exit contract

Hand off only when semantic ownership changes:

```text
canonical plugin result + capability/state
→ lazybuilder-ui for presentation

bundled LazyBuilder core compatibility/synchronization
→ lazybuilder-desktop-runtime
```

Finish when:

- canonical lifecycle result is correct;
- destructive/replacement work has a valid rollback boundary where required;
- duplicate/dependency behavior has one owner;
- matching proof is complete for the available context;
- any remaining Paper live proof or UI presentation residue is named precisely.

Do not continue into unrelated plugin cleanup or generic plugin-framework design.
