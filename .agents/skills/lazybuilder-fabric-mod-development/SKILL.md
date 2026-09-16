---
name: lazybuilder-fabric-mod-development
description: Implement and evolve LazyBuilder-owned Fabric mods: module/bootstrap structure, client/server initializers, Fabric/Minecraft API integration, keybinds/screens/rendering adapters, networking adapters, resources, Loom/Gradle packaging, and client runtime integration. Use for coding Fabric mods, not shared protocol semantics, UI ownership, Paper/world domain rules, or Launcher runtime.
---

# LazyBuilder Fabric Mod Development

Own implementation mechanics of LazyBuilder-owned Fabric mods under `mods/**`. Global diagnosis/proof rules come from `docs/04-system/development-discipline.md`; semantic owner selection and cross-owner handoff come from `docs/04-system/skill-routing.md`.

This Skill is consumer-neutral: ChatGPT and Codex use the same Fabric implementation procedure. Adapt only execution mechanics to available capabilities; unavailable build, renderer, or client-runtime proof remains explicit residue.

## Entry gate

Use this Skill when the requested change is primarily about implementing or changing a LazyBuilder Fabric mod:

```text
new Fabric mod/module
Fabric client/common initializer wiring
keybind/event/client lifecycle integration
Minecraft Screen/controller/render adapter implementation
Fabric networking sender/receiver adapter after contract freeze
resource/assets/fabric.mod.json wiring
Gradle/Loom module configuration and packaging
Minecraft/Fabric API integration
client gametest/proof harness implementation when required by an existing proof contract
focused Fabric/Minecraft runtime integration
```

Do not redefine semantics owned elsewhere:

```text
presentation/interaction contract       → lazybuilder-ui
neutral Paper↔Fabric wire meaning       → lazybuilder-protocol
world lifecycle/import/export semantics → lazybuilder-world-management
Launcher/process/provisioning           → lazybuilder-desktop-runtime
third-party Paper plugin lifecycle      → lazybuilder-plugin-management
```

If another Skill owns the semantic decision, freeze/prove that contract first, emit the typed handoff, then implement it here.

## Owns

```text
Fabric mod module structure
initializer/bootstrap wiring
Fabric/Minecraft API adaptation
keybind/listener/client lifecycle mechanics
Screen/controller/renderer implementation mechanics after UI contract freeze
Fabric-side protocol adapter implementation after Protocol contract freeze
resource/assets/fabric.mod.json wiring
Gradle/Loom/toolchain/package mechanics
client runtime registration and shutdown/reset mechanics
proof fixture/harness code only when it exercises production paths without duplicating semantics
```

It does not own UI/product/wire/domain semantics merely because they execute inside a Fabric mod.

## Context loading

### Default load

```text
this SKILL.md
→ exact mod module under mods/**
→ exact build.gradle / gradle.properties / settings.gradle when material
→ exact src/main/java/resources surface
→ exact semantic contract/handoff consumed by the implementation
```

### Required if

```text
Minecraft/Fabric API behavior is uncertain
→ exact version-matched API/mapping/source evidence

Screen/input/render behavior is the semantic question
→ STOP and hand to lazybuilder-ui

shared payload/version/default meaning changes
→ STOP and hand to lazybuilder-protocol

world-domain meaning changes
→ STOP and hand to lazybuilder-world-management

build/package/toolchain is the first wrong owner
→ exact module Gradle/Loom configuration + current repository wrapper/toolchain evidence
```

### Do not load if

- do not load every mod when one module is affected;
- do not load UI/Protocol/World/Desktop Skills merely because the mod consumes their contracts;
- do not browse generic Fabric examples when current source already establishes the local pattern;
- do not add mixins, libraries, UI frameworks, event buses, caches, or compatibility layers without a specific unsupported platform/API requirement.

### Escalate when

Escalate only when current mod source plus its consumed semantic contract cannot distinguish implementation, semantic ownership, build/toolchain, renderer, or live-client behavior. Name the next separating evidence before loading more context.

## Temporal status

The ownership/lifecycle/safety rules in this Skill are `STABLE_RULE`.

Current Minecraft version, mappings, Fabric Loader/API versions, Loom version, Gradle requirement, Java release, run configs, source-set wiring, and mod descriptors are `IMPLEMENTATION_SNAPSHOT`. Verify them from the exact module/build files before relying on specific values.

## Failure taxonomy

```text
MODULE_STRUCTURE      module/source/resource/descriptor layout wrong
BOOTSTRAP             initializer/registration lifecycle wrong
API_ADAPTER           Fabric/Minecraft translation of a proven contract wrong
INPUT_EVENT           keybind/input/event dispatch wiring wrong
RENDER_CONTROLLER     Screen/renderer/controller mechanics wrong after UI semantics are proven
NETWORK_ADAPTER       shared protocol is correct but Fabric sender/receiver adapter is stale/wrong
LIFECYCLE_STATE       disconnect/reload/world-change/client-state cleanup wrong
RESOURCE_DESCRIPTOR   fabric.mod.json/assets/resources wiring wrong
MIXIN_BOUNDARY        mixin target/injection/version coupling wrong or unnecessary
DEPENDENCY_PACKAGE    Gradle/Loom/dependency/remap/package artifact wrong
FABRIC_RUNTIME        source/build contract correct but behavior remains client-runtime-only
OWNERSHIP             another semantic Skill is the first wrong owner
UNKNOWN               next separating evidence required
```

`OWNERSHIP` is not patched here. Reclassify through `skill-routing.md`, hand off, and stop this Skill until the semantic contract returns frozen.

## Canonical procedure

```text
name exact Fabric-side behavior to implement
→ identify semantic contract/owner supplying that behavior
→ load exact mod module + build/resources
→ capture current failing/missing implementation evidence
→ classify local subtype
→ verify target-version API/mapping/lifecycle only where material
→ reuse existing initializer/controller/adapter/source-set pattern
→ smallest complete implementation
→ focused build/source proof available in current capability
→ VISUAL_RENDERED when appearance is part of acceptance and production renderer is exercised
→ LIVE_RUNTIME only when the exact changed Fabric/client-server path is exercised
→ typed handoff if ownership changes
→ STOP
```

## Implementation invariants

- prefer stable Fabric/Minecraft APIs available for the current target before mixins or internal implementation hooks;
- use mixins only when the required behavior cannot be achieved cleanly through supported APIs/events and the version coupling is justified;
- client-only state never becomes server/domain authorization authority;
- shared Paper↔Fabric wire types remain defined once in the canonical shared protocol source; Fabric adapters consume them rather than fork them;
- UI semantics come from `lazybuilder-ui`; this Skill implements screens/controllers/render paths without inventing business state;
- one physical input should dispatch one logical action; avoid competing keybind/screen/global handlers;
- late network responses must remain bound to the originating request/entity/context;
- disconnect/world/server transitions clear or reconcile transient client state deterministically;
- renderer/cache invalidation follows real content scope, not unrelated hover/menu/presentation state;
- proof fixtures may feed production screens/controllers but must not become a second product implementation.

## Build / packaging discipline

Use the repository's current Fabric toolchain instead of creating a parallel Gradle/Loom setup.

Current modules use Gradle/Loom with Java 21 and module-local version properties; some modules include selected shared protocol sources and client-gametest/proof source sets. These details are source-authoritative snapshots, not permanent requirements.

Rules:

- version/mapping/Loader/API/Loom changes must be deliberate and checked against all directly affected modules;
- resource processing must keep descriptor/version metadata consistent with the produced artifact;
- shared protocol sources/dependencies are consumed through the current repository pattern, not copied into a second contract definition;
- remap/package success proves artifact/build contracts only, not screen/input/network/runtime behavior;
- do not introduce a second wrapper/toolchain/version authority for one mod unless the repository explicitly supports it.

## Proof matrix

```text
pure Java/controller/validation behavior         → EXECUTED_SOURCE
Gradle compile/remap/API wiring                  → EXECUTED_SOURCE
resource/descriptor/package identity             → EXECUTED_SOURCE or PACKAGE_SMOKE
deterministic multi-component client fixture     → INTEGRATION_FIXTURE
real Svelte-equivalent? no: Minecraft appearance → VISUAL_RENDERED via real Minecraft renderer
keybind/screen/client lifecycle/runtime path      → LIVE_RUNTIME when exact path is exercised
Paper↔Fabric interoperability                    → LIVE_RUNTIME after Protocol contract proof
semantic presentation decision                   → lazybuilder-ui
```

An HTML/mock recreation is not canonical Minecraft visual proof. A remapped JAR is not proof that the client loads the mod or that the changed keybind/network/screen path works.

## Handoff / exit

Use canonical typed handoffs from `skill-routing.md`.

Common outputs:

```text
to ui
→ exact production Screen/controller surface + canonical state/result consumed + implementation constraint

to protocol
→ Fabric-side adapter evidence + exact shared contract meaning missing/ambiguous

to world-management
→ exact world-domain semantic question observed through the client adapter

to desktop-runtime
→ exact client provisioning/launch/runtime integration symptom after mod implementation is proven correct
```

Finish when the Fabric module consumes one proven semantic contract, initializer/input/network/render lifecycle is singular, build/package behavior matches current repository tooling, and matching proof covers the changed implementation at the available capability ceiling. Stop before redefining UI/domain/protocol semantics, Launcher runtime, or unrelated framework/toolchain infrastructure.