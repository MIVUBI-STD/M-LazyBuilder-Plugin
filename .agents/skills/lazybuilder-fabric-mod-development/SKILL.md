---
name: lazybuilder-fabric-mod-development
description: Implement and evolve LazyBuilder-owned Fabric mods: module/bootstrap structure, entrypoints/environment boundaries, Fabric/Minecraft API integration, keybinds/screens/rendering adapters, networking adapters, mixin boundaries, resources/descriptors, Loom/Gradle packaging, and client runtime integration. Use for coding Fabric mods, not shared protocol semantics, UI ownership, Paper/world domain rules, or Launcher runtime.
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
mixin implementation when supported API/event paths are insufficient
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
physical-environment / entrypoint mechanics
Fabric/Minecraft API adaptation
keybind/listener/client lifecycle mechanics
Screen/controller/renderer implementation mechanics after UI contract freeze
Fabric-side protocol adapter implementation after Protocol contract freeze
resource/assets/fabric.mod.json wiring
mixin target/injection mechanics when justified
Gradle/Loom/toolchain/package mechanics
client runtime registration and disconnect/world-change/reset mechanics
proof fixture/harness code only when it exercises production paths without duplicating semantics
```

It does not own UI/product/wire/domain semantics merely because they execute inside a Fabric mod.

## Context loading

### Default load

```text
this SKILL.md
→ exact mod module under mods/**
→ exact build.gradle / gradle.properties / settings.gradle when material
→ exact fabric.mod.json + src/main/java/resources surface
→ exact semantic contract/handoff consumed by the implementation
```

### Required if

```text
Minecraft/Fabric API behavior is uncertain
→ exact target-version API/mapping/source evidence

entrypoint/environment/mixin behavior is uncertain
→ exact fabric.mod.json + target-version Loader/Minecraft evidence

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
- do not browse generic Fabric examples when current LazyBuilder source already establishes the local pattern;
- do not copy API names from newer Fabric docs into the 1.21.4 codebase without checking current mappings/source;
- do not add mixins, libraries, UI frameworks, event buses, caches, or compatibility layers without a specific unsupported platform/API requirement.

### Escalate when

Escalate only when current mod source plus its consumed semantic contract cannot distinguish implementation, semantic ownership, build/toolchain, renderer, mixin/version coupling, or live-client behavior. Name the next separating evidence before loading more context.

## Temporal status

The ownership/lifecycle/safety rules in this Skill are `STABLE_RULE`.

Current Minecraft version, Yarn mappings, Fabric Loader/API versions, Loom version, Gradle requirement, Java release, run configs, source-set wiring, shared-protocol inclusion pattern, and mod descriptors are `IMPLEMENTATION_SNAPSHOT`. Verify them from the exact module/build files before relying on specific values.

Current LazyBuilder Map Manager is a client-environment mod using a `client` entrypoint in `fabric.mod.json`. Treat that as the current module pattern, not a universal rule for every future mod.

Official Fabric documentation is `EXTERNAL_REFERENCE`. It may document a newer Minecraft/Fabric line than LazyBuilder's current target, so use it for conceptual platform rules and then verify exact classes/methods/mappings against the repository's current 1.21.4 sources before coding.

## Platform anchors

When external Fabric behavior must be checked, prefer official Fabric documentation for the exact topic:

```text
Loader / mod structure
→ https://docs.fabricmc.net/develop/loader/

fabric.mod.json / entrypoints / dependencies / mixins
→ https://docs.fabricmc.net/develop/loader/fabric-mod-json

Minecraft custom Screen lifecycle
→ https://docs.fabricmc.net/develop/rendering/gui/custom-screens

networking / payload registration / receivers
→ https://docs.fabricmc.net/develop/networking
```

Stable conceptual rules:

- a Fabric mod JAR is described by root `fabric.mod.json` metadata;
- mod id, environment, entrypoints, dependencies, and mixin declarations are part of the load contract;
- `main`, `client`, and `server` entrypoints have different environment scope; client-only code must not leak into a common/server path;
- Screen dimensions/widgets are lifecycle-owned; create/rebuild widgets in the Screen initialization lifecycle rather than assuming constructor-time width/height is authoritative;
- mixins are a class-transformation mechanism, not the default architecture for behavior already available through supported APIs/events;
- network payload/type/receiver mechanics are version-sensitive; exact 1.21.4 registration APIs must come from current LazyBuilder/Fabric source, not copied blindly from newer docs.

## Failure taxonomy

```text
MODULE_STRUCTURE      module/source/resource/descriptor layout wrong
BOOTSTRAP             initializer/registration/environment lifecycle wrong
API_ADAPTER           Fabric/Minecraft translation of a proven contract wrong
INPUT_EVENT           keybind/input/event dispatch wiring wrong
RENDER_CONTROLLER     Screen/renderer/controller mechanics wrong after UI semantics are proven
NETWORK_ADAPTER       shared protocol is correct but Fabric sender/receiver adapter is stale/wrong
LIFECYCLE_STATE       disconnect/reload/world/dimension/server transition cleanup wrong
RESOURCE_DESCRIPTOR   fabric.mod.json/assets/resources/version wiring wrong
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
→ load exact mod module + descriptor/build/resources
→ verify target/environment/entrypoint/toolchain snapshot
→ capture current failing/missing implementation evidence
→ classify local subtype
→ verify target-version API/mapping/lifecycle only where material
→ reuse existing initializer/controller/adapter/source-set pattern
→ choose supported API/event before mixin
→ smallest complete implementation
→ focused build/source/package proof available in current capability
→ VISUAL_RENDERED when appearance is part of acceptance and real Minecraft renderer is exercised
→ LIVE_RUNTIME only when the exact changed Fabric/client-server path is exercised
→ typed handoff if ownership changes
→ STOP
```

## Entrypoint / environment contract

Treat `fabric.mod.json` as part of runtime architecture, not only metadata.

```text
common/main entrypoint
→ code safe for every environment in which the mod can load

client entrypoint
→ client-only initialization, rendering, keybinds, Screens, client receivers

server entrypoint
→ dedicated/server-only initialization when the mod actually supports it
```

Rules:

- do not reference client-only Minecraft classes from a common path that can load on a dedicated server;
- registration should happen deterministically once at the appropriate entrypoint/lifecycle;
- entrypoints register adapters/controllers; they should not become large domain-service implementations;
- avoid static initialization that assumes an active world/player/network connection before the client lifecycle provides one;
- a client-only mod should state that environment explicitly when that is the real product/runtime constraint;
- adding a common/server entrypoint is a product/runtime expansion and must be justified rather than added for symmetry.

## Screen / input / renderer implementation contract

UI semantics remain owned by `lazybuilder-ui`; this Skill implements the production Minecraft path.

For Screens:

```text
constructor
→ capture immutable inputs / parent reference only

init / re-init
→ create position-dependent widgets from current width/height
→ register controls exactly once for that initialization

render / tick
→ consume current presentation/controller state
→ avoid domain/filesystem/network mutation as render side effects

close / removed / disconnect
→ release or detach screen-local/transient resources without implying unsupported semantic cancellation
```

Rules:

- resize/re-init must not duplicate handlers/widgets or lose durable controller state accidentally;
- one physical key/click enters one logical dispatch path;
- global keybind + Screen handler must not both execute the same action;
- render/tick paths must not perform expensive sorting/filesystem/network work each frame when event/revision-driven state can be reused;
- cache invalidation follows actual content/world/dimension scope, not hover/sidebar/menu/favorite state;
- keep late async/network results correlated to the request/entity/screen context that originated them.

## Networking adapter contract

Protocol meaning belongs to `lazybuilder-protocol`. Fabric code owns only client-side registration/codec/send/receive/adaptation mechanics after that contract is frozen.

```text
canonical shared payload/identifier
→ Fabric registration/codec/channel adapter
→ send request with canonical ids/bounds
→ receive canonical result/error
→ correlate to originating request/entity/context
→ update client/controller presentation state
```

Rules:

- register required payload/receiver types before first use according to the exact target-version Fabric API;
- do not create a second payload model inside the mod when `shared/protocol` already defines the wire meaning;
- bounds/default/version semantics are not recomputed in the client adapter;
- server/domain authorization remains authoritative even if the client disables impossible actions for UX;
- treat server/client input as untrusted at the receiving authority; client-side validation improves feedback but is not security;
- network callbacks must transition onto the correct client/game context before touching Minecraft client state when the target API requires it;
- disconnect/server switch/world transition invalidates or reconciles outstanding transient requests so late results cannot mutate a new context;
- if the current LazyBuilder adapter uses a different 1.21.4 networking abstraction than current upstream docs, preserve the current proven contract unless an intentional migration is in scope.

## Client lifecycle contract

Transient client state must have an explicit validity scope.

At minimum distinguish when material:

```text
client process lifetime
server/session connection
managed world identity
dimension
active Screen/request
renderer/cache scope
```

Rules:

- disconnect/server change clears session-scoped capabilities, pending requests, selected remote ids, and stale operation results as appropriate;
- unmanaged/changed world or dimension must not reuse managed-world presentation/cache state unless the semantic owner says it remains valid;
- re-opened Screens query/reuse canonical current state instead of replaying old requests automatically;
- global callbacks/keybinds are registered once for the client process, not once per Screen/world join;
- client reset/cleanup must not invent a server-side state transition.

## Mixin boundary

Use this escalation order:

```text
existing LazyBuilder adapter/controller
→ supported Fabric API/event/callback
→ target Minecraft API/mapped hook
→ mixin only when the required hook is otherwise unavailable
```

If a mixin is necessary:

- target the smallest stable behavior point that satisfies the requirement;
- verify the exact target class/method/descriptor against current 1.21.4 mappings/source;
- keep business/domain rules outside the mixin; inject/delegate to the normal owner;
- avoid broad overwrite-style replacement when a narrow injection/access is sufficient;
- make failure/version coupling visible instead of silently falling back to different behavior;
- do not add compatibility mixins for hypothetical versions not supported by the repo.

## Resource / descriptor contract

`fabric.mod.json` and assets/resources must agree with the built implementation:

```text
mod id / version / name
environment
entrypoints
depends/breaks/conflicts only when real
mixins only when present
asset/resource namespaces
```

Rules:

- mod id is canonical package/resource identity and should not drift casually;
- descriptor dependencies describe real load requirements, not a workaround for internal ordering;
- current LazyBuilder resource processing expands build version into the descriptor; preserve one version authority;
- resource namespaces should align with the canonical mod id unless the existing module intentionally defines otherwise;
- do not duplicate an internal shared protocol contract into JSON/resources for convenience.

## Implementation invariants

- prefer stable Fabric/Minecraft APIs available for the current target before mixins or internal implementation hooks;
- client-only state never becomes server/domain authorization authority;
- shared Paper↔Fabric wire types remain defined once in the canonical shared protocol source; Fabric adapters consume them rather than fork them;
- UI semantics come from `lazybuilder-ui`; this Skill implements screens/controllers/render paths without inventing business state;
- one physical input dispatches one logical action;
- late network responses remain bound to the originating request/entity/context;
- disconnect/world/server transitions clear or reconcile transient client state deterministically;
- renderer/cache invalidation follows real content scope;
- proof fixtures may feed/open production screens/controllers but must not become a second product implementation.

## Module creation checklist

For a new LazyBuilder Fabric module, reuse the repository's current module pattern unless source intentionally changes it:

```text
module directory under mods/**
build.gradle / settings.gradle / gradle.properties as required
src/main/java
src/main/resources/fabric.mod.json
only required assets/resources
entrypoint classes matching environment
shared protocol consumption only when needed
focused source tests / existing proof source sets when justified
```

Before creating a module, answer:

```text
Does a new Fabric mod lifecycle/artifact actually need to exist?
Can an existing mod own the implementation without broadening unrelated context?
Is the mod client-only or genuinely common/server-capable?
Which semantic Skill owns behavior and presentation?
Does it consume an existing protocol contract or require Protocol work first?
What exact Minecraft/Fabric runtime behavior still requires LIVE_RUNTIME proof?
```

Do not split code into separate mods merely for package organization.

## Build / packaging discipline

Use the repository's current Fabric toolchain instead of creating a parallel Gradle/Loom setup.

Current Map Manager snapshot uses Minecraft 1.21.4, Yarn 1.21.4+build.8, Fabric Loader 0.16.10, Fabric API 0.119.4+1.21.4, Loom 1.9.2, Gradle 8.12, Java 21, version expansion in `fabric.mod.json`, selected shared-protocol source inclusion, and a client gametest/proof source set. Verify exact current values before relying on them; do not copy module-specific proof/source-set choices into every mod automatically.

Rules:

- version/mapping/Loader/API/Loom changes must be deliberate and checked against every directly affected module;
- do not upgrade Minecraft/Fabric/Loom/Gradle incidentally while implementing an unrelated feature;
- resource processing must keep descriptor/version metadata consistent with the produced artifact;
- shared protocol sources/dependencies are consumed through the current repository pattern, not copied into a second contract definition;
- remap/package success proves artifact/build contracts only, not screen/input/network/runtime behavior;
- do not introduce a second wrapper/toolchain/version authority for one mod unless the repository explicitly supports it;
- module-specific gametest/proof wiring is evidence infrastructure, not production architecture.

## Proof matrix

```text
pure Java/controller/validation behavior          → EXECUTED_SOURCE
Gradle compile/remap/API/entrypoint wiring         → EXECUTED_SOURCE
resource/fabric.mod.json/package identity          → EXECUTED_SOURCE or PACKAGE_SMOKE
mixin target compile/mapping contract              → EXECUTED_SOURCE
client controller/network deterministic fixture    → INTEGRATION_FIXTURE
real Minecraft Screen/layout/renderer scenario     → VISUAL_RENDERED
keybind/Screen/client lifecycle changed path        → LIVE_RUNTIME
Fabric network send/receive/interoperability path   → LIVE_RUNTIME after Protocol contract proof
semantic presentation decision                     → lazybuilder-ui
```

An HTML/mock recreation is not canonical Minecraft visual proof. A remapped JAR is not proof that the client loads the mod or that the changed keybind/network/Screen path works. A screenshot proves rendered presentation only; it does not prove request ordering, server authorization, or disconnect cleanup.

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

Finish when the Fabric module consumes one proven semantic contract, environment/entrypoint/input/network/render lifecycle is singular, mixins exist only where justified, transient state has explicit validity/cleanup, descriptor/package behavior matches current repository tooling, and matching proof covers the changed implementation at the available capability ceiling. Stop before redefining UI/domain/protocol semantics, Launcher runtime, or unrelated framework/toolchain infrastructure.
