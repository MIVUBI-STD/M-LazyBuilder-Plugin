---
name: lazybuilder-paper-plugin-development
description: Implement and evolve LazyBuilder-owned Paper plugin code: module/bootstrap structure, commands/listeners/services, Paper/Bukkit API adapters, scheduler/thread boundaries, plugin resources, Maven packaging, internal dependencies, and runtime integration. Use for coding internal Paper plugins, not third-party plugin lifecycle, world-domain policy, shared Paper/Fabric wire semantics, or presentation.
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

Do not redefine semantic contracts owned by another Skill. Examples:

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
→ exact API usage/source for the repository target version

thread/scheduler behavior is material
→ exact call path + Paper API thread requirement

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
- do not research generic Bukkit/Paper patterns when current source already establishes the correct local pattern;
- do not add libraries/frameworks for ordinary command/listener/service wiring when Paper/Java/current dependencies suffice.

### Escalate when

Escalate only when current plugin source plus its consumed semantic contract cannot decide whether the defect is implementation, semantic ownership, build/toolchain, or live-runtime behavior. Name the exact separating evidence before loading more context.

## Temporal status

The ownership/threading/safety rules in this Skill are `STABLE_RULE`.

Current repository facts such as Java release, Paper version, Maven plugin versions, module list, shading rules, descriptors, and dependency set are `IMPLEMENTATION_SNAPSHOT`. Verify them against the current root/module POMs and resources before relying on exact versions or filenames.

## Failure taxonomy

```text
MODULE_STRUCTURE      module/source/resource layout or parent wiring wrong
BOOTSTRAP             plugin entrypoint or enable/disable initialization wrong
COMMAND_EVENT         command/permission/listener/event registration or dispatch wrong
API_ADAPTER           Paper/Bukkit translation of a proven semantic contract wrong
THREADING             sync/async/scheduler/main-thread boundary unsafe
LIFECYCLE             shutdown/reload/resource ownership leaks or duplicates work
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
→ capture current failing/missing implementation evidence
→ classify local subtype
→ verify Paper API/thread/lifecycle constraints only where material
→ reuse existing module/service/adapter pattern
→ smallest complete implementation
→ focused build/source proof available in current capability
→ LIVE_RUNTIME only when the changed Paper path is actually exercised
→ typed handoff if ownership changes
→ STOP
```

## Implementation invariants

- Paper/Bukkit APIs are preferred over NMS/internal server implementation; use NMS only with explicit proven need and version-lock consequences understood.
- Bukkit/Paper operations that require the server thread stay on the correct thread; heavy pure file/CPU work may leave the main thread only when the API boundary permits it.
- listeners/commands/adapters delegate to one application/domain owner instead of embedding duplicate business rules.
- plugin enable/disable must not register duplicate listeners/tasks/services or leave owned tasks/resources running after shutdown.
- one user action should enter one primary execution path; avoid duplicate command/listener/protocol side effects.
- validate untrusted command/network/file inputs at the appropriate trust boundary.
- internal Paper plugins do not become a second shared-protocol definition; consume types/contracts from the canonical shared source.
- configuration/data files are not used as duplicate authorities for facts already owned by another domain registry/service.
- ordinary feature work must not introduce a generic plugin framework, service locator, event bus, scheduler abstraction, or persistence layer without repeated proven responsibility.

## Build / packaging discipline

Use the repository's current build owner instead of inventing a parallel toolchain.

Current pattern is Maven parent/module build with Paper API provided by the server and module-specific packaging/shading where required. Exact versions/configuration are source-authoritative and must be verified before mutation.

Rules:

- keep `paper-api`/server-provided dependencies out of shaded runtime contents unless the existing build intentionally says otherwise;
- relocate bundled libraries only when collision isolation is actually required and the current module pattern supports it;
- plugin metadata/resources and packaged artifact identity must agree with the module implementation;
- a successful Maven package proves packaging/build contracts only, not Paper enable/runtime behavior.

## Proof matrix

```text
pure Java branch/validation/service behavior     → EXECUTED_SOURCE
module compile + command/listener/API wiring      → EXECUTED_SOURCE
resource/config/package structure                 → EXECUTED_SOURCE or PACKAGE_SMOKE
shading/relocation/artifact-content contract      → PACKAGE_SMOKE
multi-service/filesystem deterministic behavior   → INTEGRATION_FIXTURE
Paper enable/disable/command/event/scheduler path → LIVE_RUNTIME
presentation-only result                          → lazybuilder-ui
```

A built JAR is not proof that Paper loads/enables or that the changed event/command path works. A running server is not proof unless the exact changed behavior is exercised.

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

Finish when the internal Paper module consumes one proven semantic contract, implementation/thread/lifecycle ownership is singular, package behavior matches current repository tooling, and matching proof covers the changed implementation at the available capability ceiling. Stop before redesigning domain semantics, third-party plugin management, presentation, or unrelated framework infrastructure.