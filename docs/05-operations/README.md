# Current Operations

This directory owns current continuation/proof only. Durable product and architecture rules live in their domain docs.

## Current State

- plugin-stack audit remains open for the broader legacy stack;
- Multiverse is the first confirmed rebuild target;
- native LazyBuilder World Manager source implementation has started;
- World Manager runtime bootstrap is intentionally idle when unused;
- World Manager world identity/registry foundation is source-implemented;
- registry metadata has one bounded YAML persistence path with atomic publication where supported;
- canonical `BUILD_READY` initialization policy is source-implemented and deliberately not a background enforcement loop;
- Flat World and Void World creation share one application path and one Paper runtime adapter;
- runtime `LOADED/UNLOADED/LOADING/UNLOADING` state is a separate ephemeral owner;
- Auto Load consumes persisted metadata once at startup rather than polling;
- Teleport, World Settings, and Spawning share the same Paper authority;
- file operations have one request-bound operation coordinator plus one path-safe local repository boundary;
- Archive/Restore, Clone, and Delete are source-implemented with rollback-oriented ownership;
- conversion runtime now has a bounded current/previous/candidate store under `world/runtime/converter`;
- conversion updates are lazy and policy-gated: at most once per interval, stable-source contract, SHA-256 verification, adapter compatibility probe, discovered supported-format catalog, candidate promotion, and previous-runtime rollback support;
- V1 conversion ownership is globally single-job through `ConversionJobCoordinator`;
- generic child-process execution is request-bound with timeout/interruption cleanup; no converter process exists while idle;
- the actual Chunker release-source/asset-selection adapter and CLI command mapping remain intentionally unimplemented until their live artifact/CLI contract is verified rather than guessed;
- Xaero World Map remains limited to Map Preview, location interaction, and the approved Export Area presentation boundary.

## Next Action

Verify the current Chunker stable-release artifact and CLI contract, then implement the concrete internal adapter/release source on top of the new runtime store/update/process boundaries. After that, connect Import / Export to the existing file-operation and conversion owners.

Do not expose Chunker as a separate product surface. Do not introduce an idle daemon, periodic update timer, filesystem watcher, or persistent conversion worker.

## Proof State

GitHub Actions `mvn verify` is green through Clone/Delete source and targeted unit tests. The internal conversion-runtime foundation has source and tests in the current change and requires its own green CI run before compile/test success is claimed. Real runtime download, Chunker CLI probing, converter execution, Java↔Bedrock conversion quality, client file transfer, and live Paper behavior remain LOCAL_CODE/LIVE_SERVER proof.
