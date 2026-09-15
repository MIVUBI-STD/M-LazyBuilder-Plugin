# Paper Runtime Verification

This document defines the remote Paper runtime proof for LazyBuilder. It complements `mvn verify` with a real disposable Paper 1.21.4 process and is intentionally narrower than full Minecraft-client validation.

## Remote authority

The canonical remote runtime gate is:

```text
.github/workflows/paper-runtime-proof.yml
```

It runs on Windows with Java 21, builds the Paper modules from source, downloads a stable Paper 1.21.4 runtime from PaperMC, executes the lifecycle proof, then executes restart/persistence proof against the same disposable server directory.

The workflow is path-scoped to Paper/plugin/protocol/runtime-proof changes so unrelated Launcher churn does not continuously cancel or rerun server proof.

## Lifecycle proof

`scripts/verify-paper-runtime.ps1` creates an isolated server under:

```text
.runtime-proof/paper-smoke/
```

It verifies:

```text
Paper 1.21.4 boot
→ World-Manager enable
→ Utilities-Manager enable
→ unauthenticated local-control rejection
→ authenticated protocol/status contract
→ managed-world listing
→ Create World
→ settings update/readback
→ Archive
→ Restore
→ Duplicate
→ Backup
→ native Java 1.21.4 Export
→ authenticated artifact upload
→ Import
→ managed-world publication
→ permanent Delete
→ cleanup confirmation
→ clean server shutdown
```

The native export test intentionally sends an artifact **base name**. The backend owns the `.zip`/`.mcworld` extension according to the selected export type.

## Restart and persistence proof

`scripts/verify-paper-restart.ps1` reuses the disposable runtime produced by the lifecycle test and verifies:

```text
boot existing runtime
→ create persistent probe world
→ confirm registry visibility
→ clean shutdown
→ boot the same runtime again
→ reload registry
→ resolve the same WorldId/display name
→ read settings after restart
→ delete the persisted probe
→ confirm deletion
→ clean shutdown
```

This is real Paper runtime evidence for normal clean restart and registry/filesystem persistence. It is not synthetic unit-test evidence.

## Failure policy

Do not weaken the runtime harness merely to make CI green. A failure must first be classified as either:

- a real plugin/runtime defect;
- a test-harness contract error;
- an external runtime/download failure.

Fix the smallest wrong owner. Do not introduce a second bootstrap, compatibility manager, dependency injection framework, alternate filesystem authority, or another world-operation path simply to bypass a runtime incompatibility.

## What remote GitHub now proves

For the Paper-side path, a green `Paper Runtime Proof` establishes real runtime evidence for:

- Paper plugin loading/linkage on Java 21;
- local loopback authentication and protocol status;
- World-Manager lifecycle and task execution;
- filesystem publication for duplicate/export/import/delete;
- native Java export and HTTP upload/import path;
- registry persistence across a clean restart;
- Utilities-Manager startup and command-binding health signal;
- clean plugin/server shutdown.

## Remaining proof boundary

The remote runner intentionally does **not** claim proof for capabilities that require a real Minecraft client, target workstation, or representative production-scale fixture:

```text
actual player teleport/gameplay interaction
Fabric screen/input behavior inside Minecraft
Utilities movement behavior with a real player
installed Tauri/Windows UI interaction
Modrinth profile/filesystem behavior on a target PC
real large-world throughput at production scale
cross-edition Chunker conversion quality on representative worlds
network disconnect/reconnect with a real Fabric client
```

Those remain later client/target-environment validation items. Their absence must not be replaced by unnecessary architecture or mock systems.
