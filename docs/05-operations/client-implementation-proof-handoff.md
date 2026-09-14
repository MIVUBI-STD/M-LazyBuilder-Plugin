# Client Implementation-Proof Handoff

## Purpose

This is the execution handoff for the three LazyBuilder Fabric mods after architecture/scope lock. The next phase is proof and bounded defect fixing, not feature expansion.

Canonical architecture: `docs/04-system/final-client-pre-handoff-audit.md`.

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

## 2. Independent Fabric builds

Run exactly:

```powershell
gradle -p mods/map-manager --no-daemon build
gradle -p mods/utility-manager --no-daemon build
gradle -p mods/performance-manager --no-daemon build
```

Expected artifacts:

```text
mods/map-manager/build/libs/lazybuilder-map-manager-0.1.0-SNAPSHOT.jar
mods/utility-manager/build/libs/lazybuilder-utility-manager-0.1.0-SNAPSHOT.jar
mods/performance-manager/build/libs/lazybuilder-performance-manager-0.1.0-SNAPSHOT.jar
```

A Manager build failure does not justify editing another Manager.

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

Fix the smallest owning file/package, rerun the failing Manager first, then rerun all three.

## 4. Map Manager runtime matrix

```text
[ ] starts without mixin/init failure
[ ] World Map / Worlds navigation works
[ ] current managed-world state is server-authoritative
[ ] CurrentWorldCleared removes stale unmanaged-world state
[ ] Copy Review Reference contains exact world name/id/location/dimension
[ ] Teleport Here works
[ ] Export Area selection works and clears on world change
[ ] World Control V5 / Map Action V2 interoperate with Paper
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

## 6. Performance Manager runtime matrix

Without Dynamic FPS:

```text
[ ] focused gameplay preserves user FPS limit
[ ] unfocused/minimized caps apply as configured
[ ] focus restoration returns to user FPS limit
[ ] no renderer/Sodium/Iris behavior is modified
```

With Dynamic FPS:

```text
[ ] provider is detected
[ ] LazyBuilder background controller performs no competing write
[ ] external provider owns background FPS behavior
```

Capability detection remains informational only.

## 7. Combined smoke test

Install all three JARs together and verify:

```text
[ ] no duplicate mod id / metadata issue
[ ] no cross-Manager class linkage
[ ] Map Manager does not depend on Utility/Performance internals
[ ] Utility behavior is independent of managed-world state
[ ] Performance behavior is independent of Map/Utility implementation
[ ] Vanilla/Axiom/WorldEdit workflows remain familiar and unaffected
```

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
- persistent issue/review tracking;
- duplicate Vanilla/Axiom/WorldEdit behavior;
- fixing a failure that cannot be reproduced.

## Completion criteria

```text
[ ] repository consistency passes
[ ] all three mods build independently
[ ] each focused runtime matrix passes
[ ] combined smoke test passes
[ ] defects are fixed at the smallest owner
[ ] no scope expansion introduced
[ ] exact tested commit SHA recorded
```

Do not call the client runtime-validated before these checks are actually executed.
