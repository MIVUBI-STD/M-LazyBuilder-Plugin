# World Manager — Import, Export, Conversion, and Runtime Updates

This document is the canonical owner for world transfer/conversion behavior inside LazyBuilder World Manager.

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

World Manager detects the incoming world edition/version and chooses the cheapest safe path.

```text
same target format/version
→ native import

different Java version
→ conversion worker

Bedrock input targeting Java server
→ conversion worker
```

Import must validate into temporary storage before publishing the world directory or registry entry. Existing worlds are never overwritten in V1.

Imported worlds default to:

```text
runtime   = UNLOADED
autoLoad  = OFF
lifecycle = ACTIVE
```

Imported gameplay/settings are preserved. `BUILD_READY` is applied only by Create World or by an explicit Reset to Build Ready action.

## Export behavior

World Manager chooses a native package path when no conversion is needed and the conversion worker when the target edition/version differs.

```text
same format/version
→ native export

different version/edition
→ conversion worker
```

Export UI exposes edition, version, and area. Converter-specific advanced settings remain at upstream defaults unless a future confirmed requirement requires an override.

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

The client mod may open the native file picker/save dialog. Files are streamed between client and server through the LazyBuilder protocol so the same flow works whether client and server share a machine or connect remotely.

Do not create separate local-PC and remote-server product flows unless evidence requires different transport behavior.

## Operation locking

Conflicting operations on the same world must be rejected or queued through one World Manager operation owner.

Examples of conflicts:

- delete during export;
- archive during conversion;
- clone while a destructive swap is active;
- simultaneous conversions competing for the same output.

V1 permits only one active conversion job at a time. This protects CPU, memory, and disk I/O and keeps progress semantics simple.

## Resource bounds

The conversion worker runs in a separate process/JVM with a configured memory ceiling. It must not inherit an unbounded fraction of host memory.

Default policy should prefer a conservative bounded heap, configurable by the maintainer. Conversion failure or out-of-memory must not terminate Paper.

## Automatic runtime updates

Converter updates are owned internally by World Manager and are fail-closed.

Default policy:

```text
mode             = Automatic Stable
channel          = stable releases only
idle polling     = none
check frequency  = at most once per 24h
checksum         = required
compatibility    = required
rollback         = enabled
retained runtime = current + previous
server restart   = not required
```

Update checks are triggered lazily (for example, first use of Import/Export after the interval expires) rather than by a permanent timer.

### Update pipeline

```text
check release metadata
→ if no newer stable version: stop
→ download candidate to staging
→ verify release digest/checksum
→ probe expected CLI/runtime capabilities
→ query supported formats
→ run a small deterministic compatibility fixture when practical
→ if PASS: mark candidate current for the next conversion
→ retain previous runtime for rollback
```

An active conversion always finishes with the runtime version it started with. Runtime replacement never occurs mid-job.

If validation fails, keep the current runtime and discard/reject the candidate. Do not disable Import/Export merely because an update failed.

### Runtime store

Conceptually:

```text
world/runtime/converter/
├── current/
├── previous/
└── candidate/
```

Only current and previous are retained after successful stabilization; stale candidates are removed.

### Supported format catalog

The World Manager version list should be discovered from the verified runtime rather than hardcoded where possible. A converter update that only adds supported Minecraft versions therefore does not require a LazyBuilder release.

If the converter changes its CLI/API contract incompatibly, the compatibility probe rejects the candidate until `ConverterAdapter` is updated in LazyBuilder.

## Proof boundary

Remote/source proof may establish contracts, adapter isolation, update state transitions, checksum behavior, and deterministic parsing. Live conversion quality, Paper world lifecycle, client file dialogs, large-file transfer, and real Java↔Bedrock results require local/live runtime proof.
