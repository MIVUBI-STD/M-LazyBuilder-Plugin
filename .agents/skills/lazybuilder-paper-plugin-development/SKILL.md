---
name: lazybuilder-paper-plugin-development
description: Implement and evolve LazyBuilder-owned Paper plugin code: module/bootstrap structure, lifecycle, commands/listeners/services, Paper/Bukkit API adapters, scheduler/thread boundaries, plugin resources/descriptors/config, Maven packaging, internal dependencies, and runtime integration. Use for coding internal Paper plugins, not third-party plugin lifecycle, world-domain policy, shared Paper/Fabric wire semantics, or presentation.
---

# LazyBuilder Paper Plugin Development

Own the implementation mechanics of LazyBuilder-owned Paper plugins under `plugins/**`. Global diagnosis/proof rules come from `docs/04-system/development-discipline.md`; semantic owner selection and cross-owner handoff come from `docs/04-system/skill-routing.md`.

This Skill is consumer-neutral: ChatGPT and Codex use the same Paper implementation procedure. Adapt only execution mechanics to capabilities actually available; unavailable build or live Paper proof remains explicit residue.

## Entry gate

Use this Skill when the requested change is primarily about implementing or changing an internal Paper plugin:

```text
new LazyBuilder Paper plugin/module
plugin bootstrap / enable-disable lifecycle
commands / permissions / listeners / event wiring
application service wiring inside a Paper plugin
Paper/Bukkit adapter implementation
scheduler / sync-async boundary
plugin resources / descriptors / configuration wiring
Maven module/dependency/shading/package behavior
Paper-side adapter for an already-defined shared protocol contract
focused Paper runtime integration
```

Do not use this Skill for third-party plugin discovery/install/update/remove; that belongs to `lazybuilder-plugin-management`.

Do not redefine semantic contracts owned by another Skill:

```text
world lifecycle/import/export policy → lazybuilder-world-management
neutral Paper↔Fabric payload meaning → lazybuilder-protocol
presentation/copy/layout              → lazybuilder-ui
Launcher/server process/provisioning  → lazybuilder-desktop-runtime
```

If another Skill owns the semantic decision, freeze/prove that contract first, emit the typed handoff, then implement it here.

## Owns

```text
Paper plugin module structure
plugin entrypoint/bootstrap wiring
commands/listeners/permissions registration mechanics
application-service to Paper API adaptation
Paper/Bukkit API usage correctness
main-thread vs async execution boundary
plugin resource/config loading mechanics
Maven module/build/package/shading mechanics
internal plugin dependency wiring
Paper-side protocol adapter implementation after contract freeze
plugin runtime startup/shutdown integration
```

It does not own product/domain semantics merely because they execute inside a Paper plugin.

## Context loading

### Default load

```text
this SKILL.md
→ exact plugin module under plugins/**
→ exact pom.xml + relevant src/main/java/resources files
→ exact semantic contract/handoff consumed by the implementation
```

### Required if

```text
Paper/Bukkit API behavior is uncertain
→ exact API/source for the repository target version

thread/scheduler behavior is material
→ exact call path + exact Paper/Bukkit thread requirement

descriptor/bootstrap behavior is uncertain
→ exact current plugin.yml / entrypoint + version-matched Paper documentation

shared Paper↔Fabric message meaning changes
→ STOP and hand to lazybuilder-protocol

world lifecycle/filesystem meaning changes
→ STOP and hand to lazybuilder-world-management

build/package failure is the first wrong owner
→ exact module pom + root pom/pluginManagement + produced artifact evidence
```

### Do not load if

- do not load every plugin module when one module is affected;
- do not open Protocol/World/UI/Desktop Skills merely because the plugin touches those systems; consume their typed contract instead;
- do not research generic Bukkit/Paper patterns when current LazyBuilder source already establishes the correct local pattern;
- do not add libraries/frameworks for ordinary command/listener/service wiring when Paper/Java/current dependencies suffice;
- do not migrate plugin bootstrap style merely because a newer Paper mechanism exists.

### Escalate when

Escalate only when current plugin source plus its consumed semantic contract cannot decide whether the defect is implementation, semantic ownership, build/toolchain, descriptor/lifecycle, or live-runtime behavior. Name the exact separating evidence before loading more context.

## Temporal status

The ownership/threading/safety rules in this Skill are `STABLE_RULE`.

Current repository facts such as Java release, Paper version, Maven plugin versions, module list, shading rules, descriptor type, command registration shape, and dependency set are `IMPLEMENTATION_SNAPSHOT`. Verify them against the current root/module POMs and resources before relying on exact versions or filenames.

Current LazyBuilder source uses standard `plugin.yml` + `JavaPlugin` style for internal Paper plugins. That is a source-derived snapshot, not a permanent ban on future Paper plugin bootstrap APIs.

Official Paper documentation is `EXTERNAL_REFERENCE`. Use it only when platform behavior is uncertain, and prefer documentation/API behavior that matches the current repository target version. Newer generic docs do not override LazyBuilder source or an already-proven local adapter.

## Platform anchors

When external Paper behavior must be checked, prefer the official Paper documentation for the exact topic:

```text
plugin lifecycle / JavaPlugin
→ https://docs.papermc.io/paper/dev/how-do-plugins-work/

scheduler / sync-async boundary
→ https://docs.papermc.io/paper/dev/scheduler/

Paper plugin bootstrap APIs
→ https://docs.papermc.io/paper/dev/getting-started/paper-plugins/
```

Important platform rules:

- normal Bukkit/Paper plugins use a main class declared by plugin metadata and lifecycle hooks such as `onLoad`, `onEnable`, and `onDisable`;
- do not perform Paper/Bukkit API work in the `JavaPlugin` constructor; runtime/plugin-manager state is not ready there;
- Paper's `paper-plugin.yml`/Paper-plugin bootstrap model is experimental in current official documentation and is not a drop-in replacement for normal `plugin.yml` semantics;
- LazyBuilder therefore keeps `plugin.yml` as the default current pattern unless an explicit requirement proves a Paper-plugin-only lifecycle/capability is needed.

## Failure taxonomy

```text
MODULE_STRUCTURE      module/source/resource layout or parent wiring wrong
BOOTSTRAP             plugin entrypoint/descriptor or enable-disable initialization wrong
COMMAND_EVENT         command/permission/listener/event registration or dispatch wrong
API_ADAPTER           Paper/Bukkit translation of a proven semantic contract wrong
THREADING             sync/async/scheduler/main-thread boundary unsafe
LIFECYCLE             startup/shutdown/reload/resource ownership leaks or duplicates work
PERSISTENCE_CONFIG    plugin-owned config/data loading/writing mechanics wrong
DEPENDENCY_PACKAGE    Maven dependency/scope/shading/package artifact wrong
PROTOCOL_ADAPTER      shared contract is correct but Paper adapter is stale/wrong
PAPER_RUNTIME         source/build contract correct but behavior remains runtime-only
OWNERSHIP             another semantic Skill is the first wrong owner
UNKNOWN               next separating evidence required
```

`OWNERSHIP` is not patched here. Reclassify through `skill-routing.md`, hand off, and stop this Skill until the semantic contract returns frozen.

## Canonical procedure

```text
name exact Paper-side behavior to implement
→ identify semantic contract/owner supplying that behavior
→ load exact plugin module + build/resources
→ verify current descriptor/bootstrap/toolchain snapshot
→ capture current failing/missing implementation evidence
→ classify local subtype
→ verify Paper API/thread/lifecycle constraints only where material
→ reuse existing module/service/adapter pattern
→ smallest complete implementation
→ focused build/source/package proof available in current capability
→ LIVE_RUNTIME only when the changed Paper path is actually exercised
→ typed handoff if ownership changes
→ STOP
```

## Lifecycle contract

Treat the plugin lifecycle as an ownership boundary, not merely method names.

```text
constructor
→ Java object construction only
→ no Paper/Bukkit runtime work

onLoad (only when needed)
→ minimal local/bootstrap preparation that does not require enabled gameplay/runtime state

onEnable
→ validate/load plugin-owned configuration
→ construct application services/adapters
→ register commands/listeners/channels/tasks exactly once
→ reconcile current authoritative state where required
→ expose plugin as ready only after required initialization succeeds

running
→ callbacks remain thin
→ validate/capture/delegate
→ long work uses the correct execution context

onDisable
→ stop/cancel plugin-owned tasks
→ close owned executors/resources/transports
→ flush only bounded plugin-owned state when required
→ leave no callback/task able to mutate server state afterward
```

Rules:

- initialization must be idempotent against accidental duplicate registration paths;
- disable/cleanup should tolerate partial initialization and execute safely once;
- do not design around hot reload as a normal production lifecycle;
- a dependency declared optional/soft must remain optional at runtime; guard integration before touching its classes or services;
- a plugin being enabled is not proof that every command/listener/network path works.

## Descriptor / command / permission contract

Current LazyBuilder internal Paper modules use `plugin.yml`. Treat descriptor fields as executable packaging/runtime contract:

```text
name / main / version / api-version
commands + permission nodes
depend/softdepend/load ordering when actually required
```

Rules:

- `main` must resolve to the packaged entrypoint actually shipped;
- descriptor version/artifact identity must not silently diverge from the build version policy;
- commands declared in metadata must map to real handlers and stable permission behavior;
- permissions provide server-side access control/presentation metadata, not a second copy of domain authorization logic;
- `softdepend` means optional integration: absence must not prevent normal plugin startup unless the product contract explicitly requires that dependency;
- do not add descriptor dependencies merely to force load order when a real service/capability dependency does not exist;
- do not introduce `paper-plugin.yml` for novelty or because a current Paper doc advertises it; require a concrete lifecycle/API reason and re-evaluate packaging/runtime implications first.

## Command / event / adapter contract

Paper-facing callbacks should be thin adapters:

```text
command / event / plugin-message callback
→ validate trust-boundary input + caller/context identity
→ capture stable ids/immutable inputs
→ delegate to one semantic/application owner
→ map canonical result/error back to Paper surface
```

Avoid:

- business rules duplicated inside command executors or listeners;
- broad exception swallowing that converts a failed mutation into success;
- heavyweight filesystem/network/conversion work directly in event callbacks;
- trusting client-provided world/plugin/capability/permission facts when the server already owns them;
- using display names or user-facing strings as durable identity when canonical ids exist.

For client-originated requests, bounds/authorization/ownership are revalidated server-side even when Fabric/UI already validated for feedback.

## Scheduler / threading contract

Paper's scheduler distinction is semantic:

```text
sync task
→ server/main-thread work

async task
→ work that is actually safe away from Paper/Bukkit state
```

Rules:

- do not call non-thread-safe Bukkit/Paper world/entity/server APIs from an async task unless the exact target-version API explicitly permits it;
- do not use `Thread.sleep` or blocking external IO on the main server thread;
- move large pure file/hash/archive/network/CPU work off-thread only after capturing the minimum immutable input needed;
- return to the correct server thread before mutating Paper/Bukkit state when required;
- after async work completes, revalidate target identity/lifecycle/lease/player/world state before commit; the world/player may have changed while work was running;
- tasks/executors created by the plugin have one owner and are cancelled/closed on disable;
- cancellation is exposed only where the underlying operation can stop safely;
- avoid one ad-hoc executor per feature when the existing operation/execution owner is sufficient.

## Configuration / persistence contract

`config.yml` and other plugin resources are configuration inputs, not automatic domain databases.

Rules:

- plugin defaults are deterministic and owned in one place;
- user-edited values are not silently overwritten during startup or upgrade;
- validate configuration before exposing dependent functionality;
- secrets/tokens must not be logged or bundled into support output;
- durable world/plugin/runtime facts remain with their semantic owner rather than being copied into a plugin config for convenience;
- if persisted plugin-owned data needs migration/recovery semantics, define the version/rollback boundary explicitly rather than relying on best-effort parsing.

## Implementation invariants

- Paper/Bukkit APIs are preferred over NMS/internal server implementation; use NMS only with explicit proven need and version-lock consequences understood.
- listeners/commands/adapters delegate to one application/domain owner instead of embedding duplicate business rules.
- plugin enable/disable must not register duplicate listeners/tasks/services or leave owned tasks/resources running after shutdown.
- one user action should enter one primary execution path; avoid duplicate command/listener/protocol side effects.
- validate untrusted command/network/file inputs at the appropriate trust boundary.
- internal Paper plugins do not become a second shared-protocol definition; consume types/contracts from the canonical shared source.
- configuration/data files are not used as duplicate authorities for facts already owned by another domain registry/service.
- ordinary feature work must not introduce a generic plugin framework, service locator, event bus, scheduler abstraction, or persistence layer without repeated proven responsibility.

## Module creation checklist

For a new internal Paper module, use the existing repository pattern unless current source has intentionally changed it:

```text
root Maven module registration when required
module pom.xml with parent
src/main/java
src/main/resources/plugin.yml
only required config/resources
direct semantic/shared dependencies
focused tests for source-level contracts
package/shading rules only when required
```

Before adding the module, answer:

```text
Does a new plugin process/lifecycle boundary actually need to exist?
Can an existing internal Paper module own this implementation cleanly?
Which semantic Skill owns the behavior?
Does the plugin need a new shared protocol contract, or only consume an existing one?
Which dependency is server-provided vs packaged?
What exact runtime behavior still needs LIVE_RUNTIME proof?
```

Do not split features into separate plugins merely for code organization.

## Build / packaging discipline

Use the repository's current build owner instead of inventing a parallel toolchain.

Current `Local` snapshot uses a Maven parent/module build with Java 21, Paper 1.21.4 API, standard `plugin.yml`, Paper API supplied by the server, and module-specific shading where required. Verify all exact values against current source before using them as facts.

Rules:

- keep `paper-api`/server-provided dependencies out of shaded runtime contents unless the existing build intentionally says otherwise;
- `provided` versus packaged scope is a runtime contract, not only a compile detail;
- relocate bundled libraries only when collision isolation is actually required and the current module pattern supports it;
- exclude signatures/metadata that become invalid during shading when the existing packaging pattern requires it;
- plugin metadata/resources and packaged artifact identity must agree with the module implementation;
- do not change Java/Paper/Maven versions incidentally while implementing a feature;
- a successful Maven package proves packaging/build contracts only, not Paper enable/runtime behavior.

## Proof matrix

```text
pure Java branch/validation/service behavior      → EXECUTED_SOURCE
module compile + API/command/listener wiring       → EXECUTED_SOURCE
descriptor/config/package structure                → EXECUTED_SOURCE or PACKAGE_SMOKE
shading/relocation/artifact-content contract       → PACKAGE_SMOKE
multi-service/filesystem deterministic behavior    → INTEGRATION_FIXTURE
scheduler policy branch/reconciliation              → EXECUTED_SOURCE or INTEGRATION_FIXTURE
actual thread/API timing + enable-disable behavior  → LIVE_RUNTIME
actual command/event/plugin-message changed path    → LIVE_RUNTIME
presentation-only result                            → lazybuilder-ui
```

A built JAR is not proof that Paper loads/enables or that the changed event/command/network path works. A server boot is not `LIVE_RUNTIME` feature proof unless the exact changed path is exercised with the exact artifact.

## Handoff / exit

Use canonical typed handoffs from `skill-routing.md`.

Common outputs:

```text
to protocol
→ Paper-side implementation need + exact shared meaning missing/ambiguous

to world-management
→ Paper adapter evidence + exact world-domain semantic question

to ui
→ proven canonical result/capability/error surfaced by the plugin

to desktop-runtime
→ exact server/process/provisioning symptom after plugin implementation is proven correct
```

Finish when the internal Paper module consumes one proven semantic contract, bootstrap/thread/resource ownership is singular, descriptor/package behavior matches current repository tooling, async work cannot commit stale server state, and matching proof covers the changed implementation at the available capability ceiling. Stop before redesigning domain semantics, third-party plugin management, presentation, or unrelated framework infrastructure.
