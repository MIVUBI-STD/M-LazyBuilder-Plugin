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
- conversion runtime has a bounded current/previous/candidate store under `world/runtime/converter`;
- conversion updates are lazy and policy-gated: at most once per interval, SHA-256 verification, CLI compatibility probe, supported-format discovery, candidate promotion, and previous-runtime rollback support;
- official stable release metadata is now consumed through a dedicated GitHub release source that selects only `chunker-cli-<tag>.jar` and requires the release `sha256:` digest;
- `ChunkerCliAdapter` now owns verified CLI syntax, version probing, required-option compatibility checks, supported-format parsing, heap-bounded JVM invocation, conversion command construction, and optional Export Area pruning input;
- V1 conversion ownership remains globally single-job through `ConversionJobCoordinator`;
- child-process execution is request-bound, timeout/interruption-safe, writes output to a temporary request log, and retains only a bounded output tail; no converter process exists while idle;
- Gson is shaded/relocated inside LazyBuilder so GitHub release parsing does not create a shared server classpath dependency;
- Xaero World Map remains limited to Map Preview, location interaction, and the approved Export Area presentation boundary.

## Next Action

Connect **Import / Export** to the existing world-file and conversion owners. The integration must lazily call `ConversionUpdateService.checkIfDue()` when transfer functionality is actually used, then choose native transfer for same-format/same-version requests or `ChunkerCliAdapter` for version/edition conversion.

Implement Export first around a safe world snapshot, then Import around validated temporary input and publish/register only after success. `Export Area` should create pruning settings only when a selection exists; whole-world export must omit `-p` completely.

Do not expose Chunker as a separate product surface. Do not introduce an idle daemon, periodic update timer, filesystem watcher, or persistent conversion worker.

## Proof State

GitHub Actions `mvn verify` was green for the conversion-runtime foundation. The concrete GitHub release source / Chunker CLI adapter / shaded dependency changes have targeted unit tests in the current source and require the latest CI run to be green before compile/test proof is promoted. Real runtime download, live CLI probing, converter execution, Java↔Bedrock conversion quality, client file transfer, large-world behavior, and live Paper behavior remain LOCAL_CODE/LIVE_SERVER proof.
