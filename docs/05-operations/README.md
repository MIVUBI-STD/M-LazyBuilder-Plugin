# Current Operations

This directory owns current continuation/proof only. Durable product and architecture rules live in their domain docs.

## Current State

- `Local` is the active development/source authority; `main` remains stable/release.
- Remote architecture/source simplification is closed unless local/live evidence reveals a real defect.
- World-Manager and Utilities-Manager source architecture remain structurally locked.
- LazyBuilder execution routing uses five specialist Skills only:

```text
lazybuilder-desktop-runtime
lazybuilder-plugin-management
lazybuilder-world-management
lazybuilder-ui
lazybuilder-protocol
```

- Minimum-flow development discipline is canonical in `docs/04-system/development-discipline.md`.
- Specialist ownership/routing is canonical in `docs/04-system/skill-routing.md`.
- Source/CI proof remains distinct from actual Windows/Paper/Fabric runtime proof.

## Current Source Checkpoint

The source checkpoint immediately before this operations update is:

```text
branch: Local
head:   ffc57a3bd8cf67782fc95995a1de74b1b61d56c2
```

Important simplifications already present at that checkpoint:

```text
7 specialist Skills -> 5
single Paper process owner / recovery path
single process marker with PID + process start time
process_identity sidecar/module removed
single typed server_config owner for server-manager.json
bundled World/Utilities core sync made internal before start/restart
manual public Core Sync action removed
Paper runtime update remains the user-facing update decision
Plugin Manager category override registry removed
Plugin Manager remove-data/quarantine path removed
plugin data always preserved on JAR removal
historical plugin backup retention removed
backup_maintenance module removed
Plugin Manager rollback reduced to one previous-valid JAR snapshot
manual CPU allocation removed from product/config
CPU scheduling remains JVM/OS managed
resource UI reduced to Performance / Boost / Custom RAM
frontend runtime bridge consolidated into one grouped typed runtimeApi
World Manager re-audited with no material simplification required
```

Do not reopen these decisions merely to reduce file/class count. Reopen only when local/live evidence shows a concrete defect or unsupported requirement.

## Last Known Remote Proof

The earlier complete remote source/CI gate was:

```text
head: fcae5870192252bcecc63c5f0c458bed446a64a8
run:  Verify #509

Paper modules/tests  SUCCESS
Fabric client build  SUCCESS
Tauri desktop        SUCCESS
Overall              SUCCESS
```

That gate predates the later simplification work listed above. Therefore it is historical proof, not proof that current `ffc57a3...` compiles locally. The current source must now be validated under `LOCAL_CODE`.

Canonical older completion record:

```text
docs/05-operations/remote-github-complete.md
```

## Canonical Runtime Layout

```text
Work Server - 1.21.4/
├── LazyBuilder.exe
├── server/
│   ├── paper.jar
│   └── plugins/
├── world-system/
│   ├── worlds/
│   ├── imports/
│   ├── exports/
│   ├── backups/
│   └── work/
└── tools/lazybuilder/
    ├── config/
    ├── cache/
    ├── logs/
    ├── disabled-plugins/
    └── plugin-backups/
```

Archive/Restore is lifecycle metadata plus load-state handling; it does not own a second physical archive directory. Legacy inputs may be read only where compatibility is still intentionally supported; they must not become parallel active authorities.

## Desktop Runtime Ownership After Simplification

```text
Desktop Runtime
├── server_config
│   └── one server-manager.json reader/writer authority
├── ServerManagerState
│   ├── Child ownership
│   ├── one process marker
│   ├── PID + real process start-time validation
│   ├── start / stop / restart
│   └── detached recovery
├── Java provider
├── Paper provider
├── bundled Core provider
└── resource settings
```

Runtime rules:

- bundled core compatibility is internal maintenance;
- Paper update remains explicit/user-facing;
- CPU is JVM/OS managed; no manual `ActiveProcessorCount` product path;
- RAM remains bounded by hardware-aware Performance / Boost / Custom settings;
- no network lookup may turn a committed local mutation into a false failure.

## Plugin Manager Ownership After Simplification

Third-party Paper plugin lifecycle remains:

```text
list / metadata / dependency validation
install
update
enable / disable
safe JAR removal
resolve duplicates
minimum rollback
```

Current invariants:

- filesystem + plugin metadata are primary truth;
- category is derived presentation metadata, not a persisted authority;
- removal preserves plugin data;
- no user-facing remove-data/quarantine branch;
- no timestamped backup history or retention subsystem;
- update/removal keeps only one previous-valid rollback JAR per plugin where needed;
- duplicate resolution keeps temporary rollback state only for the active transaction;
- no hot reload; restart-required behavior remains explicit.

## World Manager Audit Result

World Manager was re-audited after the minimum-flow discipline was introduced. No material refactor is currently justified.

Keep the following because they have distinct runtime responsibilities:

```text
bounded WorldTaskRunner
single conversion-job lease/coordinator
registry/persistence owners
Paper runtime adapter
filesystem/import/export owners
transfer session owner
conversion adapter/runtime
```

The task runner is bounded and prevents heavy file/conversion work from leaking onto Paper's main thread. The conversion coordinator is an in-memory single-job lease, not an idle daemon. Do not collapse these owners without runtime evidence.

## Local Environment Checkpoint

Local repository:

```text
D:\Work\AI Stuff\LazyBuilder
branch: Local
```

Confirmed during setup:

```text
Java   Temurin 21.0.12.1 LTS   installed / verified
Maven  3.9.16                  installed / verified in configured session
Node   24.14.1                 verified
npm    11.16.0                 verified
Cargo  1.96                    verified in configured session
rustc  1.96                    verified in configured session
```

Environment caveat:

- a newly opened Administrator PowerShell did not automatically inherit the user PATH entries for Maven, Cargo/Rust, and Gradle;
- this is currently an environment/PATH issue, not evidence that those installations are missing;
- refresh Machine/User PATH or use the configured tool paths before build validation.

Pending toolchain check:

```text
Gradle 8.12 -> installation/path verification still pending
```

No full local build has been completed after the current source simplification.
No LIVE_SERVER validation has started.

## STOP Point / Exact Resume Point

Work intentionally stopped during `LOCAL_CODE` environment preparation.

Resume here, in this exact order:

```text
1. open PowerShell at D:\Work\AI Stuff\LazyBuilder
2. refresh user/machine PATH for the session if needed
3. verify Gradle 8.12 specifically
4. verify Java 21 / Maven / Node / npm / Cargo / rustc / Gradle in the same shell
5. run current local builds without editing source first
6. record the first reproducible build failure, if any
7. fix only the smallest owning boundary
8. repeat the failing targeted build
9. after LOCAL_CODE is green, launch LazyBuilder desktop
10. then begin LIVE_SERVER validation
```

Do **not** start another architecture cleanup before step 5. The first objective is now proof, not more source reduction.

## LOCAL_CODE Build Order

Recommended order:

```text
1. Maven Paper/shared modules
2. Fabric client with pinned Gradle 8.12
3. frontend dependency install from committed lockfile
4. frontend/Svelte build
5. Rust/Tauri locked check/build
6. inspect produced artifacts
```

Use repository-pinned/reproducible dependency state where available:

```text
EngineData/Frontend/RustApp/package-lock.json
EngineData/Frontend/RustApp/src-tauri/Cargo.lock

npm ci
cargo check --locked
```

Do not introduce a new build framework merely to prove the current source.

## LIVE_SERVER Sequence After Local Build Passes

```text
1. launch LazyBuilder desktop
2. create/open/adopt workspace
3. Prepare Server
4. accept EULA explicitly
5. start Paper 1.21.4
6. verify start / stop / restart
7. verify detached/crash recovery
8. verify canonical runtime directories
9. verify World-Manager enable + control bridges
10. verify Fabric World Manager lifecycle UI
11. verify Import/Export + native dialogs + transfer integrity
12. verify Xaero Teleport Here / Export Area
13. verify Utilities families and state restoration
14. verify Plugin Manager install/update/enable-disable/remove/duplicates
15. verify app/server restart persistence and cleanup
```

High-value runtime checks:

- desktop finds/uses managed Java 21 correctly;
- bundled core auto-sync occurs as internal start/restart maintenance;
- no manual Core Sync product action reappears;
- Paper update remains explicit and recoverable;
- one `server-process.json` marker is the process identity authority;
- stale/reused PID cannot cause unrelated-process termination;
- `server-manager.json` has one active reader/writer authority;
- JVM launch does not force manual CPU processor count;
- `world-system/worlds` is the actual Paper universe;
- no duplicate runtime storage authorities appear;
- plugin removal preserves plugin data;
- plugin rollback does not accumulate unbounded timestamp history;
- create/load/unload/teleport/clone/archive/restore/delete correctness;
- fallback/default world delete protection;
- Import/Export on real archives and filesystem permissions;
- transfer checksum/integrity and realistic file-size behavior;
- native Windows dialogs;
- Fabric screen input/layout;
- Xaero mixin/runtime compatibility;
- Fly/Noclip/Night Vision state restoration;
- Build Helpers behavior in Creative builder workflows;
- World Safety protections without unintended ownership;
- restart/shutdown persistence and cleanup.

## Defect Handling

Only reproducible local/live failures reopen source work. Record:

```text
expected
actual
exact reproduction steps
relevant log/error
owning component
smallest failing boundary
```

Then route through the smallest relevant specialist:

```text
desktop/runtime/process/config     -> lazybuilder-desktop-runtime
third-party plugin lifecycle       -> lazybuilder-plugin-management
world/Paper behavior/files         -> lazybuilder-world-management
Desktop or Fabric presentation     -> lazybuilder-ui
shared Paper/Fabric wire contract  -> lazybuilder-protocol
```

Fix defects directly in `Local`. Do not create side branches unless explicitly requested. Do not reopen a locked architecture when a bounded owner-level fix is sufficient.

## Proof Ceiling

```text
REMOTE_GITHUB -> source/static/CI claims
LOCAL_CODE    -> local compile/test/build/artifact claims
LIVE_SERVER   -> actual Paper/Fabric/desktop/gameplay/runtime claims
```

Current state at this checkpoint:

```text
REMOTE_GITHUB  historical proof exists
LOCAL_CODE     environment setup in progress; current source not yet fully built locally
LIVE_SERVER    not started
```
