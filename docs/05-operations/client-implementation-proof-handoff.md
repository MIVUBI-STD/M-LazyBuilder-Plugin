# Client Implementation-Proof Handoff

## Purpose

This file is the execution handoff for the LazyBuilder Fabric client after the client architecture, overlap review, post-C4 review, and final pre-handoff audit were closed.

The goal of the next phase is **proof and bounded defect fixing**, not new feature development.

Canonical architecture source:

```text
docs/04-system/final-client-pre-handoff-audit.md
```

Current client ownership remains:

```text
LazyBuilder Client Suite
├── Map Manager
├── Utility Manager
└── Performance Manager
```

Do not create another Manager, shared client implementation module, build-tool layer, recovery subsystem, or new user-facing feature during this phase unless a reproducible failure proves the current architecture cannot satisfy an existing requirement.

## Proof target

The proof target is the current `Local` HEAD at execution time.

Never treat the SHA recorded in this handoff as permanent authority. Before starting, fetch/pull `Local`, record the exact HEAD, and prove that exact revision.

The pinned Fabric toolchain is:

```text
Minecraft     1.21.4
Yarn          1.21.4+build.8
Fabric Loader 0.16.10
Fabric API    0.119.4+1.21.4
Java          21
Gradle        8.12
```

## Phase 1 — environment gate

From the repository root, verify the same shell can resolve:

```powershell
java -version
gradle --version
```

Acceptance:

- Java 21 is active;
- Gradle 8.12 is active;
- repository is on branch `Local`;
- working tree state is understood before any edits;
- no source change is made merely to work around a PATH/toolchain problem.

## Phase 2 — repository consistency

Run first:

```powershell
python scripts/verify_versions.py
```

Acceptance:

- version consistency passes;
- all three Manager identities/artifact names remain correct;
- cross-Manager isolation guards pass;
- Utility/Performance remain detached from Map Manager/shared World-Manager protocol.

If this fails, fix only the violated repository contract before attempting runtime proof.

## Phase 3 — independent Fabric builds

Use the same commands as repository CI:

```powershell
gradle -p client/map-manager --no-daemon build
gradle -p client/utility-manager --no-daemon build
gradle -p client/performance-manager --no-daemon build
```

Run them independently and in that order so the first failing Manager is obvious.

Expected artifacts:

```text
client/map-manager/build/libs/lazybuilder-map-manager-0.1.0-SNAPSHOT.jar
client/utility-manager/build/libs/lazybuilder-utility-manager-0.1.0-SNAPSHOT.jar
client/performance-manager/build/libs/lazybuilder-performance-manager-0.1.0-SNAPSHOT.jar
```

A Manager build failure does not justify editing another Manager.

## Phase 4 — compile defect policy

For every reproducible build failure record:

```text
Manager:
Command:
Expected:
Actual:
Exact compiler/mixin/config error:
First wrong owner:
Smallest proposed fix:
```

Fix rules:

1. change the smallest owning file/package;
2. do not rename/restructure Managers during proof work;
3. do not add compatibility abstractions unless the pinned runtime actually requires them;
4. rerun only the failing Manager first;
5. after the targeted build passes, rerun all three Fabric builds;
6. update canonical docs only if durable behavior actually changes.

## Phase 5 — Map Manager runtime matrix

Install the freshly built Map Manager JAR into the Minecraft 1.21.4 test profile together with the required server-side LazyBuilder World Manager environment.

Verify:

```text
[ ] client starts without mixin/init failure
[ ] Map Manager screen opens correctly
[ ] current managed-world state appears when entering a managed world
[ ] CurrentWorldCleared removes stale state in unmanaged worlds
[ ] Worlds ↔ Map navigation remains correct
[ ] right-click Map Actions menu renders all six actions
[ ] Copy Coordinates remains unchanged
[ ] Copy Review Reference is enabled only with managed world + player + client world
[ ] Review Reference clipboard text contains exact world display name
[ ] Review Reference contains stable World ID
[ ] Review Reference contains current block X Y Z
[ ] Review Reference contains current dimension id
[ ] Copy Review Reference causes no teleport or map mutation
[ ] Teleport Here still works
[ ] Export Area selection still works
[ ] selection clears if current world changes
[ ] World Control V5 / Map Action V2 interoperate with Paper
```

Review Reference expected shape:

```text
World: <display name>
World ID: <world id>
Location: <x> <y> <z>
Dimension: <dimension id>
```

No protocol change should be required for this feature.

## Phase 6 — Utility Manager runtime matrix

Verify the current locked Utility scope only:

```text
[ ] Utility Manager loads independently from Map Manager
[ ] chat history extension works
[ ] unsent chat draft restores during the same session
[ ] sending chat clears the saved draft
[ ] reconnect button appears only when valid reconnect state exists
[ ] Copy Details includes available server/reason context
[ ] DisconnectedScreenMixin injects without crash
[ ] borderless startup works when enabled
[ ] vanilla F11 remains authoritative
[ ] resource reload completion produces one toast, not duplicate dispatch
[ ] contextual screenshot naming remains opt-in
[ ] normal F2 capture remains vanilla-owned
[ ] rapid contextual screenshots receive distinct filenames
[ ] explicit screenshot filenames remain untouched
```

Special attention:

### Screenshot mixin

Prove the pinned Minecraft/Yarn target accepts the `ScreenshotRecorderMixin` injection and that rapid captures do not collide.

### Disconnect mixin

Prove the pinned target exposes the shadowed `DisconnectedScreen` fields and `DisconnectionInfo.reason()` exactly as expected.

If either binding fails, fix the binding only; do not replace the vanilla screen/capture systems.

## Phase 7 — Performance Manager runtime matrix

Test first **without Dynamic FPS**:

```text
[ ] Performance Manager loads independently
[ ] focused gameplay preserves configured user FPS limit
[ ] unfocused visible window caps at configured unfocused value
[ ] minimized window caps at configured minimized value
[ ] focus restoration returns to user FPS limit
[ ] changing the user FPS option remains authoritative when focused
[ ] no renderer/Sodium/Iris behavior is modified
```

Then test **with Dynamic FPS installed**:

```text
[ ] Dynamic FPS is detected
[ ] LazyBuilder background controller returns without writing a window FPS limit
[ ] no competing FPS-governor behavior is observed
[ ] focused/unfocused/minimized behavior is owned entirely by the external provider
```

Also confirm capability detection for the actual chosen optimization stack where installed:

```text
Sodium
Iris
ImmediatelyFast
FerriteCore
EntityCulling
MoreCulling
Sodium Extra
Reese's Sodium Options
Dynamic FPS
```

Detection is informational only; no internal third-party API integration should be introduced during proof.

## Phase 8 — combined client smoke test

After each Manager passes independently, install all three together.

Verify:

```text
[ ] game launches with all three JARs
[ ] no duplicate mod id / metadata issue
[ ] no cross-Manager class linkage
[ ] Map Manager remains functional without relying on Utility/Performance internals
[ ] Utility behavior remains independent of current managed-world state
[ ] Performance behavior remains independent of Map/Utility implementation
[ ] no new permanent HUD or shortcut conflict appears
[ ] Axiom / WorldEdit workflow remains unaffected
[ ] Creative inventory, Pick Block, hotbar, F2, F3, Creative movement remain familiar
```

## Failure priorities

Use this severity order:

```text
P0 startup / crash / data-loss risk
P1 protocol / world / transfer correctness
P2 Manager isolation or state-restoration defect
P3 interaction/layout defect blocking normal use
P4 cosmetic/polish defect
```

Do not spend proof time on P4 while P0–P2 remain open.

## Stop conditions

Stop and report instead of expanding scope if proof reveals any of these:

- a feature requires a new persistent client state authority;
- a fix would require one Manager to import another Manager's implementation;
- Dynamic FPS or another specialist mod would need to be reimplemented;
- Map review handoff would require issue/notes/marker persistence;
- a proposed fix duplicates Vanilla/Axiom/WorldEdit behavior;
- a runtime failure cannot be reproduced reliably.

Those conditions require a new architecture decision, not an implicit implementation expansion.

## Completion criteria

Client implementation-proof is complete only when:

```text
[ ] repository consistency passes
[ ] Map Manager builds
[ ] Utility Manager builds
[ ] Performance Manager builds
[ ] each Manager passes its focused runtime matrix
[ ] all three pass combined smoke test
[ ] defects discovered during proof are fixed at their smallest owner
[ ] no scope expansion was introduced
[ ] exact tested commit SHA is recorded in the proof report
```

Do not describe the client as runtime-validated before these gates are complete.

## Codex execution prompt

Use this prompt from a Codex workspace opened at the repository root:

```text
Read `docs/05-operations/client-implementation-proof-handoff.md` first, then read `docs/04-system/final-client-pre-handoff-audit.md` and only the exact source files needed for the current proof step.

Work on branch `Local`.

This is an implementation-proof phase, not a feature-development phase. Do not add new client features, Managers, shared client modules, build tools, recovery systems, profiles, HUDs, shortcuts, or third-party replacements.

Follow the handoff phases in order:
1. verify Java 21 and Gradle 8.12;
2. run `python scripts/verify_versions.py`;
3. build Map Manager, Utility Manager, and Performance Manager independently with the exact CI Gradle commands;
4. if a build fails, record the exact reproducible failure and fix only the smallest owning boundary;
5. rerun the failing Manager, then all three Managers;
6. prepare and execute the runtime test matrices in the handoff when the local Minecraft test environment is available;
7. keep a concise proof report with exact commands, results, defects, fixes, and tested commit SHA.

Preserve the locked architecture. Do not refactor unrelated source. Do not modify another Manager to solve a local failure unless evidence proves that Manager is the actual owner. Do not claim runtime success for checks that were not executed.
```
