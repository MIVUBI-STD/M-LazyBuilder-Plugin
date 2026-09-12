# World Manager — Import, Export, Conversion, and Runtime Updates

This document is the canonical owner for world transfer/conversion behavior inside LazyBuilder World Manager. System-level network ownership is defined in [`../04-system/networking.md`](../04-system/networking.md).

## Product boundary

Import, export, file management, version conversion, Java↔Bedrock conversion, and conversion-runtime updates are one World Manager capability. `Chunker` is an internal implementation dependency only; it is not a separate user-facing feature or product surface.

```text
LazyBuilder
└── World Manager
    ├── Import World
    ├── Export World
    ├── World Files
    ├── Export Area
    └── internal conversion runtime
```

User-facing naming must stay under World Manager. Do not expose separate menus named Chunker, Converter, Conversion Worker, or similar implementation terminology.

## Conversion engine ownership

World Manager owns the use cases and validation. The external conversion engine is isolated behind one adapter boundary.

```text
World Manager
    ↓
ConversionService
    ↓
ConverterAdapter
    ↓
on-demand conversion worker
```

The adapter owns CLI/process details. No other World Manager source should construct converter CLI commands or depend on converter-specific file layout.

## Runtime model

The conversion worker is not a daemon and must not remain resident while idle.

```text
conversion requested
→ prepare validated input/snapshot
→ start worker process
→ run exactly one active conversion job
→ collect result
→ terminate worker
```

Idle requirements:

- no converter process;
- no periodic conversion polling;
- no filesystem watcher solely for conversion;
- no background preview generation;
- no repeated directory-size scans;
- no map processing while World Manager UI is closed.

World/file state is request/event driven. Heavy filesystem work is asynchronous; Bukkit/Paper state transitions remain on API-safe threads.

## Import behavior

Import is request-bound and publishes no world until archive validation and any required conversion succeed.

```text
prepare
→ reserve destination identity
→ reject duplicate destination
→ acquire one import slot

worker phase
→ resolve one artifact from world/imports
→ bounded ZIP/.mcworld extraction
→ zip-slip / file-count / uncompressed-size validation
→ normalize one optional top-level world folder
→ require level.dat
→ detect Java vs Bedrock from staged world layout
→ remove session.lock / uid.dat
→ validate Java DataVersion when considering native fast path
→ native publish OR verified conversion to JAVA_1_21_4
→ publish world folder
→ register fresh WorldId
→ persist registry
→ initialize UNLOADED

finish
→ release import slot
```

Accepted V1 upload artifacts are `.zip` and `.mcworld`. Arbitrary host paths are never accepted by the import service. Import extraction is bounded by configurable file-count and uncompressed-size limits and always happens inside an owned request workspace.

The Java 1.21.4 native fast path is authorized by the actual `level.dat` `DataVersion` (`4189`), not by a user-editable marker file. Legacy `.lazybuilder-transfer.properties` metadata may still be removed during sanitization, but it is not trusted as proof of edition/version. Java archives whose DataVersion is absent, malformed, or not 1.21.4 are normalized through the verified converter runtime. Bedrock input always targets Java 1.21.4 through the converter.

Any workspace allocated for conversion is tracked before the converter starts and is cleaned on both success and failure. Partial converter output therefore does not become durable world state or accumulate silently under `world/work` after a failed request.

Existing worlds are never overwritten in V1. Imported worlds always receive fresh LazyBuilder identity and default to:

```text
runtime   = UNLOADED
autoLoad  = OFF
lifecycle = ACTIVE
kind      = IMPORTED
```

Imported gameplay/settings are preserved. `BUILD_READY` is applied only by Create World or by an explicit Reset to Build Ready action.

## Export behavior

Export separates the short live-world snapshot window from potentially long conversion/packaging work.

```text
prepare (Paper thread)
→ acquire EXPORT lease
→ unload/quiesce source when needed

snapshot phase (worker)
→ SNAPSHOT copy while source is quiescent

resume phase (Paper thread)
→ restore source world immediately after snapshot

processing phase (worker)
→ native package OR verified conversion runtime
→ package completed artifact
→ cleanup request workspaces

finish (Paper thread)
→ release EXPORT lease
```

The EXPORT lease stays active through processing so destructive/conflicting World Manager operations remain excluded, but player/world downtime is limited to the quiescent snapshot window. Conversion and ZIP packaging operate only on the owned snapshot after the live source has been restored.

Native Java 1.21.4 export does not invoke the converter and is packaged directly as `.zip`. Import does not rely on export provenance metadata for this fast path; it validates the world DataVersion itself.

For a different Java version or Bedrock target, Export lazily checks for a stable conversion-runtime update, then uses the verified `current` runtime. If the update check itself fails but a verified current runtime already exists, Export continues with that current runtime instead of disabling the feature. The requested target format must exist in the runtime-discovered supported-format catalog before conversion starts.

Converted Java output is packaged as `.zip`. Converted Bedrock output is packaged as `.mcworld`. Completed export artifacts are bounded to `plugins/LazyBuilder/world/exports` and never overwrite an existing artifact name.

If plugin/server shutdown begins while an Export Area request is active, the adapter stops accepting new work and releases tracked operation leases without attempting to reload worlds during Paper teardown.

## Export Area

User-facing name: **Export Area**.

Do not expose converter terminology such as pruning/include/exclude.

Behavior:

```text
no area selection
→ entire world
→ omit pruning configuration completely

area selection exists
→ convert selection to chunk bounds
→ use include-region pruning internally
```

Exclude-region pruning is not part of the V1 user surface.

Export Area reuses the canonical Export service. The internal pruning document exists only for a request that actually contains an area selection and remains inside request-owned workspace state.

Xaero remains the map/selection presentation owner. World Manager converts the selected block-space rectangle into validated chunk bounds for the converter adapter.

## Conversion limitations

Cross-edition conversion is not guaranteed lossless. The UI should provide a concise warning for capabilities the active conversion runtime reports or documents as limited, especially entity and structure-metadata conversion. Warnings must not imply failure when the requested build content is still convertible.

## File Manager

World Files is a bounded World Manager surface, not a general filesystem browser.

```text
World Files
├── Server Worlds
├── Imports
└── Exports
```

Normal user operations may include import, export, save-to-client, rename transfer artifacts, and delete transfer artifacts. Arbitrary host filesystem browsing is outside scope.

## Client/server file transfer

The client mod may open the native file picker/save dialog. Files are streamed through LazyBuilder's own bounded protocol over the existing Minecraft play connection. No separate HTTP upload server, WebSocket service, relay, VPN API, or cloud storage path is part of the World Manager flow.

Do not create separate local-PC and remote-server product flows unless evidence requires different transport behavior.

The official client permits one active file-transfer operation at a time. Each active local/server file uses one seekable `FileChannel`, and the protocol uses a bounded four-chunk credit window instead of reopening/skipping the file or requiring a full round trip for every 24 KiB chunk.

Server processing remains ordered per player while unrelated players can progress independently. Chunk size remains bounded below the Minecraft custom-payload ceiling; throughput improvements come from positional I/O and the bounded pipeline, not oversized packets.

## Operation locking

Conflicting operations on the same managed world must be rejected through the existing World Manager operation owner. Import has no source managed world, so V1 uses one request-bound import slot while conversion remains separately guarded by the global `ConversionJobCoordinator`.

Examples of conflicts:

- delete during export;
- archive during conversion;
- clone while a destructive swap is active;
- simultaneous conversions competing for the same output;
- two imports trying to publish at once.

V1 permits only one active conversion job at a time. This protects CPU, memory, and disk I/O and keeps progress semantics simple.

## Resource bounds

The conversion worker runs in a separate process/JVM with a configured memory ceiling. It must not inherit an unbounded fraction of host memory.

Import archive extraction is also bounded. Current default source limits are 200,000 archive entries and 65,536 MiB uncompressed data, both configurable under `world-manager.import`.

## Automatic runtime updates

Converter updates are owned internally by World Manager and are fail-closed.

Default policy:

```text
mode             = Automatic Stable
channel          = stable releases only
idle polling     = none
normal check     = at most once per 24h when a verified runtime exists
bootstrap retry  = each user-triggered conversion request until one verified runtime exists
checksum         = required
compatibility    = required
rollback         = enabled
retained runtime = current + previous
server restart   = not required
```

Update checks are triggered lazily by conversion-requiring transfer work rather than by a permanent timer. Same-version Java import/export bypasses the conversion updater when the staged world is verified as Java 1.21.4.

A transient network/release-metadata failure on a fresh installation must not create a 24-hour lockout. The normal update interval is enforced only after a verified `current` runtime exists; bootstrap may retry on the next explicit conversion request.

### Update pipeline

```text
check release metadata
→ if no newer stable version: stop
→ download candidate to staging
→ verify release digest/checksum
→ probe expected CLI/runtime capabilities
→ query supported formats
→ if PASS: mark candidate current for the next conversion
→ retain previous runtime for rollback
```

An active conversion always finishes with the runtime version it started with. Runtime replacement never occurs mid-job.

If validation fails, keep the current runtime and discard/reject the candidate. Do not disable Import/Export merely because an update failed while a verified current runtime is available.

The GitHub release request is maintenance/bootstrap egress, not the LazyBuilder client/server data plane. A verified locally installed runtime remains usable without updater network availability. A future zero-egress deployment mode should provide the runtime locally at this adapter boundary instead of creating another network path.

### Runtime store

```text
world/runtime/converter/
├── current/
├── previous/
└── candidate/
```

Only current and previous are retained after successful stabilization; stale candidates are removed.

### Supported format catalog

The World Manager version list is discovered from the verified runtime rather than hardcoded where possible. A converter update that only adds supported Minecraft versions therefore does not require a LazyBuilder release.

If the converter changes its CLI/API contract incompatibly, the compatibility probe rejects the candidate until `ConverterAdapter` is updated in LazyBuilder.

## Proof boundary

Remote/source proof may establish contracts, adapter isolation, update state transitions, checksum behavior, DataVersion gating, deterministic parsing, phased transfer ownership, positional file-I/O structure, bounded pipeline configuration, safe archive extraction, workspace cleanup, and packaging logic. Real throughput, Paper world lifecycle, `.mcworld` opening, client file dialogs, latency/loss behavior, live conversion quality, and real Java↔Bedrock results require local/live runtime proof.
