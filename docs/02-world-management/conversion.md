# World Manager — Import / Export and Conversion

Canonical owner for Import, Export, version conversion, Java↔Bedrock conversion, transfer, and conversion-runtime behavior.

## Product boundary

Import and Export are one user-facing workspace inside World Manager:

```text
World Manager
└── Import / Export
    ├── Export
    └── Import
```

`Chunker` is an internal implementation dependency only. Never expose product surfaces named Chunker, Converter, Conversion Worker, Runtime Manifest, Pruning, Artifact, or Conversion Slot.

The user should think only in familiar Minecraft terms:

```text
Java Edition
Bedrock Edition
Version
Entire World
Selected Area
Import World
Export World
```

## Backend ownership

The product capability is unified, but implementation responsibilities stay separated:

```text
WorldExportService
WorldImportService
ConverterAdapter
ConversionRuntimeStore
ConversionJobCoordinator
TransferSessionService
```

Do not collapse these into one monolithic service. One product capability does not mean one implementation class.

## Daily default settings

Normal interaction uses one user-scoped, server-scoped set of daily defaults. These are universal defaults for the builder's repeated workflow, not per-world presets.

Export defaults may contain only persistent choices such as:

```text
edition
version
dimensions / supported content options
compatibility choices supported by the active runtime
```

Do not persist transient values such as:

```text
selected map rectangle
literal file name
current progress
runtime worker state
```

Advanced changes may be used once or explicitly saved as the new default.

Saved defaults must be validated against the current backend capability catalog whenever Import / Export opens. Unsupported saved values fall back to the nearest recommended supported value with a concise notice.

## Capability-driven UI

The frontend must not hardcode format/version choices that the backend cannot produce.

The verified conversion runtime is authoritative for:

```text
supported target editions
supported target versions
supported optional conversion controls
```

Advanced UI renders only real capabilities. Do not add decorative checkboxes for unsupported converter features.

Internal machine identifiers such as `JAVA_1_21_4` may remain stable backend ids, while the UI renders familiar labels such as `Java Edition • 1.21.4`.

## Canonical server format

Managed server worlds use one canonical runtime format:

```text
Java Edition 1.21.4
```

Import always normalizes supported external input into this canonical server format. This keeps server runtime, world management, and validation single-path.

## Import behavior

Import is file-first and always creates a new managed world. Existing worlds are never silently overwritten.

```text
Choose .zip / .mcworld
→ lightweight client-side inspection when safe
→ server-authoritative validation
→ detect source edition/version
→ resolve safe display-name collision before heavy processing
→ bounded upload
→ bounded extraction
→ native publish if verified Java 1.21.4
   OR verified conversion to Java 1.21.4
→ publish world folder
→ register fresh WorldId
→ persist registry
→ ACTIVE
```

Accepted upload artifacts are `.zip` and `.mcworld`. Arbitrary host paths are never accepted by the import service.

Java 1.21.4 native fast path is authorized by actual `level.dat` DataVersion (`4189`), not user-editable provenance metadata. Bedrock or incompatible Java input is normalized through the verified converter runtime.

Imported world runtime loaded/unloaded condition is not durable world state. Runtime loading remains automatic when the builder later teleports.

Imported gameplay/settings are preserved where supported. Builder defaults are applied only by Create World or an explicit reset action.

## Export behavior

Normal Export should be one quick action using the current daily defaults:

```text
Export
→ show default summary
→ Export World
```

Advanced Export is inline on the same screen and may override edition/version/options for one operation or save them as new defaults.

Backend flow:

```text
prepare on Paper thread
→ acquire EXPORT operation ownership
→ quiesce/unload only when required for a consistent snapshot

snapshot worker
→ create owned filesystem snapshot

resume Paper thread
→ restore source immediately if it was in use

processing worker
→ native package OR verified conversion
→ package final file
→ cleanup request workspace

finish
→ release operation ownership
```

Native Java 1.21.4 export bypasses conversion and packages directly as `.zip`.

Different Java versions or Bedrock targets use the verified conversion runtime. Java output uses `.zip`; Bedrock output uses `.mcworld`. The user never chooses the extension separately.

## Export Area

Export Area is not a second export system.

```text
Map selection
→ Export Area
→ same Import / Export workspace, Export tab
→ selected rectangle supplied as transient request context
```

Without selection, Export means Entire World. A selected area is never stored as the universal daily default.

Internal region/pruning documents remain request-owned implementation details and are never exposed in UI wording.

## Conversion presentation

For users:

```text
Java Edition 1.21.4
Bedrock Edition
Will be converted to Java Edition 1.21.4
Will be converted to Bedrock Edition
```

Do not expose:

```text
Chunker
converter CLI
runtime version ids
worker process
pruning
format-id syntax
```

Cross-edition conversion may not be perfectly lossless. Show concise contextual warnings only when relevant.

## Runtime model

The converter is on-demand, not a daemon:

```text
conversion requested
→ ensure verified runtime
→ start one request-scoped worker
→ convert
→ collect result
→ terminate worker
```

Idle requirements:

- no converter process;
- no periodic conversion polling;
- no conversion-only filesystem watcher;
- no background preview generation;
- no repeated directory-size scan.

## Operation locking

Conflicting operations on the same managed world are rejected through the existing World Manager operation owner.

Examples:

```text
delete during export
archive during conversion
duplicate during destructive operation
simultaneous conversion jobs competing for the same output
```

Import has no source managed world, so it uses a request-bound import slot. Conversion remains separately guarded by `ConversionJobCoordinator`.

Only one active conversion job is allowed in V1 to keep CPU, memory, disk I/O, and progress semantics bounded.

## Client/server transfer

The client may open native file picker/save dialogs. Files stream over LazyBuilder's bounded transfer protocol on the existing Minecraft connection.

Do not create a separate HTTP/WebSocket/cloud transfer product path.

The official client permits one active file-transfer operation at a time. Transfer progress may show real percentage values; non-measurable conversion stages must use activity labels rather than fake percentages.

## Cancellation and navigation

Leaving the Import / Export screen does not imply cancellation.

Expose `Cancel` only where backend cancellation is explicitly safe. Otherwise allow the operation to continue and present clear ongoing/background status when the user returns.

Disconnect/reconnect or failed transfer must not leave permanent world-operation ownership or a stuck busy state.

## Storage and failure handling

Import extraction remains bounded by configured file count and uncompressed size. Conversion and export workspaces are request-owned and cleaned on success and failure.

User-facing errors should be actionable where possible, including:

```text
unsupported edition/version
invalid world archive
world name conflict
not enough server storage
not enough client save storage
conversion support unavailable
transfer interrupted
```

## Automatic conversion-runtime updates

Converter updates remain internal and fail-safe.

Default policy:

```text
channel          = stable only
idle polling     = none
normal check     = at most once per 24h when a verified runtime exists
bootstrap retry  = next explicit conversion request until one verified runtime exists
checksum         = required
compatibility    = required
rollback         = enabled
retained runtime = current + previous
server restart   = not required
```

Same-version Java import/export bypasses updater/network work when conversion is not required.

A failed update must not disable Import / Export when a verified current runtime is already available.

### Runtime store

```text
world/runtime/converter/
├── current/
├── previous/
└── candidate/
```

Only current and previous remain after successful stabilization.

## Proof boundary

Source review may prove ownership, capability gating, DataVersion checks, bounded extraction, workspace cleanup, packaging rules, transfer contracts, and runtime isolation. Real conversion quality, throughput, Paper lifecycle behavior, native dialogs, `.mcworld` results, disconnect recovery, and large-file behavior require local/live validation.