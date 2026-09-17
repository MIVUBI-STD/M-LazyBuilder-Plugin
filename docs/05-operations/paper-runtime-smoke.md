# Paper Runtime Verification

This document defines the remote Paper runtime proof for LazyBuilder. It complements `mvn verify` with real disposable Paper 1.21.4 processes and is intentionally narrower than full Launcher or Minecraft-client validation.

## Naming boundary

Use these names consistently:

```text
Paper multi-instance runtime proof
→ remote CI proof that three disposable Paper processes can coexist safely with distinct Paper ports and distinct authenticated World Manager control endpoints

Launcher multi-server runtime
→ Launcher-owned workspace-keyed runtime registry, lifecycle ownership, capacity ceiling, targeted controls, recovery, and UI behavior

3-server local acceptance
→ later target-PC end-to-end proof that the installed Launcher can operate three server workspaces concurrently
```

Do not call the Paper CI proof a `Launcher multi-server proof`. The Paper proof does not instantiate the Launcher runtime registry and therefore does not prove the Launcher three-server capacity ceiling or workspace-targeting behavior.

## Remote authority

The focused remote runtime workflow is:

```text
.github/workflows/paper-runtime-proof.yml
```

The canonical integrated `Verify` workflow also runs the same Paper runtime proof scripts as part of its Paper runtime gate.

Both run on Windows with Java 21, build or consume the tested Paper modules from the same revision, download stable Paper 1.21.4, and execute these proof layers:

```text
Paper managed-world lifecycle proof
→ Paper multi-instance runtime proof (1 → 2 → 3)
→ Paper restart and persistence proof
```

## Paper managed-world lifecycle proof

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

## Paper multi-instance runtime proof

`scripts/verify-paper-multi-instance-runtime.ps1` creates three independent disposable Paper directories under:

```text
.runtime-proof/paper-multi-instance/
├── paper-instance-1/
├── paper-instance-2/
└── paper-instance-3/
```

The proof starts them sequentially from one to three and verifies:

```text
Paper instance 1 ready
→ Paper instance 2 ready while instance 1 remains alive
→ Paper instance 3 ready while instances 1 and 2 remain alive
→ all three use distinct Paper listen ports
→ all three use distinct World Manager control ports
→ every control endpoint accepts only its own token
→ cross-instance tokens are rejected
→ stopping the middle instance leaves the other two alive and ready
→ clean disposable shutdown
```

This proves Paper/plugin **multi-instance coexistence and endpoint isolation** on the remote runner. It does not prove Launcher runtime ownership, the Launcher maximum of three active servers, Launcher workspace switching, targeted console/log routing, or installed-application behavior.

## Paper restart and persistence proof

`scripts/verify-paper-restart.ps1` verifies clean restart and persistence behavior for the disposable runtime:

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

## What remote GitHub proves

For the Paper-side path, a green Paper runtime gate establishes real runtime evidence for:

- Paper plugin loading/linkage on Java 21;
- local loopback authentication and protocol status;
- World-Manager lifecycle and task execution;
- filesystem publication for duplicate/export/import/delete;
- native Java export and HTTP upload/import path;
- registry persistence across a clean restart;
- Utilities-Manager startup and command-binding health signal;
- three concurrent disposable Paper processes on distinct Paper ports;
- three distinct authenticated World Manager control endpoints;
- rejection of cross-instance control tokens;
- isolated shutdown of one Paper instance while the other two remain alive;
- clean plugin/server shutdown.

## Remaining proof boundary

The remote runner intentionally does **not** claim proof for capabilities owned by the installed Launcher, a real Minecraft client, target workstation, or representative production-scale fixture:

```text
Launcher three-server capacity ceiling
Launcher workspace-keyed runtime ownership
Launcher workspace switching while three servers stay online
Launcher targeted console/log/stop behavior across three workspaces
Launcher detached-process UI/recovery on the target PC
actual player teleport/gameplay interaction
Fabric screen/input behavior inside Minecraft
Utilities movement behavior with a real player
installed Tauri/Windows UI interaction
Modrinth profile/filesystem behavior on a target PC
real large-world throughput at production scale
cross-edition Chunker conversion quality on representative worlds
network disconnect/reconnect with a real Fabric client
```

Those remain later Launcher/local/client acceptance items. Their absence must not be replaced by unnecessary architecture or mock systems.
