# Client Implementation-Proof Handoff

## Purpose

This is the V1 execution handoff for the required LazyBuilder Fabric client components after architecture/scope simplification. The next phase is proof and bounded defect fixing, not feature expansion.

`mods/performance-manager/` is a required V1 client component. It is built with the Fabric suite, bundled by the Launcher, installed/repaired by Client Setup, and participates in client readiness.

## Target

Always prove the current `Local` HEAD.

Pinned toolchain:

```text
Minecraft     1.21.4
Yarn          1.21.4+build.8
Fabric Loader 0.16.10
Fabric API    0.119.4+1.21.4
Java          21
Gradle        8.12
```

## 1. Environment gate

From repository root:

```powershell
java -version
gradle --version
python scripts/verify_versions.py
```

Acceptance: Java 21, Gradle 8.12, repository consistency green, and no source edit made merely to work around PATH/toolchain issues.

## 2. Required V1 Fabric builds

Run:

```powershell
gradle -p mods/map-manager --no-daemon build
gradle -p mods/utility-manager --no-daemon build
```

Expected artifacts:

```text
mods/map-manager/build/libs/lazybuilder-map-manager-0.1.0-SNAPSHOT.jar
mods/utility-manager/build/libs/lazybuilder-utility-manager-0.1.0-SNAPSHOT.jar
```

Performance Manager is part of the V1 package/runtime gate. Performance-specific runtime effectiveness still requires representative workload evidence beyond source/build proof.

## 3. Compile defect policy

For every reproducible failure record:

```text
Manager:
Command:
Expected:
Actual:
Exact error:
First wrong owner:
Smallest proposed fix:
```

Fix the smallest owning file/package, rerun the failing Manager first, then rerun both required V1 Managers.

## 4. Map Manager runtime matrix

```text
[ ] starts without mixin/init failure
[ ] World Map / Worlds navigation works
[ ] current managed-world state is server-authoritative
[ ] CurrentWorldCleared removes stale unmanaged-world state
[ ] Copy Review Reference contains exact world name/id/location/dimension
[ ] Teleport Here works
[ ] Export Area selection works and clears on world change
[ ] World Control V5 / Map Action V5 interoperate with Paper
[ ] transfer shutdown/reconnect behavior is clean
```

## 5. Utility Manager runtime matrix

```text
[ ] loads independently
[ ] chat history extension works
[ ] session-only draft restores/clears correctly
[ ] reconnect / Copy Details behave correctly
[ ] DisconnectedScreen mixin binds on 1.21.4
[ ] borderless startup works and vanilla F11 stays authoritative
[ ] resource reload emits one toast
[ ] screenshot naming remains opt-in
[ ] normal F2 remains vanilla-owned
[ ] rapid contextual screenshots do not collide
[ ] explicit screenshot filenames remain untouched
```

## 6. Client Setup / Modrinth matrix

```text
[ ] known Modrinth profile paths are detected
[ ] custom profile can be selected manually
[ ] exact selected profile path persists
[ ] Minecraft 1.21.4 + Fabric is verified from last launch
[ ] <profile>\mods is derived automatically
[ ] only Map Manager + Utility Manager are required
[ ] third-party mods are untouched
[ ] duplicate LazyBuilder-owned V1 JARs are repaired
[ ] failed two-component publish restores the previous LazyBuilder set
[ ] Sync is blocked while Minecraft uses the selected profile
```

## 7. Combined V1 smoke test

Install the two required JARs together and verify:

```text
[ ] no duplicate mod id / metadata issue
[ ] no cross-Manager class linkage
[ ] Map Manager does not depend on Utility implementation
[ ] Utility behavior is independent of managed-world state
[ ] Vanilla/Axiom/WorldEdit workflows remain familiar and unaffected
```

Keep Performance Manager in the combined V1 client test. Treat renderer/performance effectiveness as a separate acceptance dimension rather than removing the required component from the suite.

## Failure priority

```text
P0 startup / crash / data-loss risk
P1 protocol / world / transfer correctness
P2 Manager isolation / state restoration
P3 interaction/layout blocking normal use
P4 cosmetic polish
```

## Stop conditions

Stop and report instead of expanding scope when a proposed fix would require:

- a new persistent client state authority;
- one Manager importing another Manager's implementation;
- reimplementing a specialist performance/build tool;
- duplicate Vanilla/Axiom/WorldEdit behavior;
- fixing a failure that cannot be reproduced.

## Completion criteria

```text
[ ] repository consistency passes
[ ] Map Manager + Utility Manager build independently
[ ] Client Setup installs/repairs only those two V1 components
[ ] each required runtime matrix passes
[ ] combined V1 smoke test passes
[ ] defects are fixed at the smallest owner
[ ] no scope expansion introduced
[ ] exact tested commit SHA recorded
```

Do not call the V1 client runtime-validated before these checks are actually executed.
