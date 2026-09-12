# Current Operations

This directory owns current continuation/proof only. Durable product and architecture rules live in their domain docs.

## Current State

- `Local` remains the working authority; `main` is untouched unless explicitly promoted;
- World Manager uses one durable registry owner plus separate ephemeral runtime state;
- Create, load/unload, teleport, settings/spawning, Archive/Restore, Clone, Delete, Import, Export, and Export Area are source-implemented;
- file operations remain bounded to managed world/import/export/work roots with traversal/symlink protections where applicable;
- conversion uses one verified current/previous/candidate runtime store and one globally single conversion job;
- conversion workers and release checks are request-bound; there is no idle converter process, update poller, filesystem watcher, or transfer daemon;
- fresh-install converter bootstrap retries on the next explicit conversion request after transient release/network failure instead of being throttled for 24 hours without a usable runtime;
- Java native-import eligibility now comes from bounded `level.dat` `DataVersion` parsing (`4189` for Java 1.21.4); transfer-marker metadata is not trusted as version authority;
- failed conversion/import paths track and clean allocated workspaces before returning failure;
- `BUILD_READY` reset persists its durable entry-mode preference before mutating Paper runtime and attempts metadata rollback if runtime reset fails;
- native whole-world Java 1.21.4 export keeps the direct ZIP fast path; cross-version/cross-edition and Export Area use the verified converter path;
- Export Area reuses `WorldExportService`, request-local pruning, existing export artifacts, and the existing transfer channel rather than creating parallel systems;
- map Export tracks active tasks; shutdown stops accepting new map work and releases active Export leases without starting world loads during Paper teardown;
- `TransferSessionService` retains ordered chunks, per-owner limits, SHA-256 validation, atomic upload publication, and owner isolation while using per-session synchronization rather than one global I/O lock;
- protocol failures that identify an active transfer session clean that stale server session;
- the official Fabric client allows one active file transfer total at a time, simplifying error recovery while the server remains bounded for multiple clients;
- `PaperTransferPayloadAdapter` and `PaperMapActionPayloadAdapter` remain thin adapters over the canonical transfer/map application owners;
- Fabric client networking mirrors `lazybuilder:transfer` and `lazybuilder:map`; no second protocol exists;
- optional Xaero integration remains presentation/input only and has one version-pinned fullscreen-map accessor;
- Xaero `Teleport Here` and `Export Area` controls plus P/O fallbacks compile against the pinned 1.21.4 client target.

## Audit Hardening Completed

The post-implementation full audit produced concrete recovery/integrity findings and the remote-fixable items have been addressed:

```text
converter bootstrap retry lockout     fixed
forgeable native-import marker trust  fixed via level.dat DataVersion
failed conversion workspace leak      fixed
BUILD_READY persistence ordering      hardened
client transfer ambiguity             reduced to one active client transfer
failed server transfer session state  fail-closed cleanup
Export Area shutdown lease handling   hardened
multi-client transfer global lock     removed in favor of per-session locking
```

No new background subsystem was introduced to fix these paths.

## Next Action

Do not add another backend subsystem before runtime evidence exists. The meaningful remaining boundary is `LOCAL_CODE` / `LIVE_SERVER` validation with Paper 1.21.4 + Fabric client + pinned Xaero.

The first live pass should verify plugin enable/disable, real world creation/load/unload, fallback/player evacuation behavior, settings persistence, safe-surface teleport, Xaero fullscreen control placement and coordinate transform, two-corner Export Area, native file dialogs, large upload/download, disconnect/error recovery, converter bootstrap/update, Java↔Bedrock conversion, and `.mcworld` opening.

Only runtime evidence should drive further Xaero positioning, Paper compatibility, or converter-behavior changes. Do not create a second renderer, map cache, export service, teleport authority, transfer registry, compatibility layer, or background watcher without evidence.

## Proof State

`REMOTE_GITHUB` is green through the audit-hardening and transfer-contention slices: Paper Maven verification and Fabric Gradle compilation both pass on the current source lineage.

This does **not** prove actual running-server behavior, Xaero mixin/runtime transforms, native OS dialogs, multi-gigabyte filesystem behavior, live Chunker conversion quality, or Java↔Bedrock fidelity. Those remain `LOCAL_CODE` / `LIVE_SERVER` proof.
