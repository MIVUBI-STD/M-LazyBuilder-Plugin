# Paper Runtime Smoke Verification

This is the minimum live-server proof for LazyBuilder Paper plugins. It complements `mvn verify`; it does not replace unit tests or the full world-lifecycle validation checklist.

## Goal

Prove that the packaged Paper server can actually boot with the current `World-Manager` and `Utilities-Manager` JARs, that both plugins reach their enabled state, and that the server can be stopped cleanly.

This catches failures that source/unit verification cannot prove, including plugin metadata errors, runtime linkage failures, Paper API incompatibilities, startup lifecycle failures, and packaged-JAR mistakes.

## Prerequisites

- Java 21 available on `PATH`, or pass `-JavaExecutable`.
- A known Paper 1.21.4 server JAR already available locally.
- Current Paper modules built from the repository root with `mvn verify`.

## Run

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify-paper-runtime.ps1 `
  -ServerJar "E:\1.21.4\paper.jar"
```

The harness uses the verified module artifacts by default:

```text
plugins/world-manager/target/World-Manager-0.1.0-SNAPSHOT.jar
plugins/utilities-manager/target/Utilities-Manager-0.1.0-SNAPSHOT.jar
```

It creates an isolated disposable server under `.runtime-proof/paper-smoke/`. The existing live server is not modified.

## Pass conditions

The smoke proof passes only when Paper reaches its normal ready state, both LazyBuilder Paper plugins emit their enabled startup signals, no known fatal plugin startup signal is detected, and the disposable process accepts a normal `stop` command. Forced termination is only a cleanup fallback after timeout.

## Proof boundary

A passing smoke run does not prove world operations. Stable-release validation still requires a disposable live-server scenario covering:

```text
create
-> teleport
-> settings
-> unload/load
-> duplicate
-> backup
-> export
-> import
-> delete
-> restart
-> recovery / registry-filesystem consistency
```

Large-file transfer throughput and at least one real conversion workflow remain separate live-proof items.

## Failure handling

Do not weaken the harness merely to make it green. Fix the actual runtime incompatibility. Do not add a second plugin bootstrap, compatibility manager, dependency injection container, or alternate Paper adapter unless a concrete runtime failure demonstrates a real architectural requirement.
