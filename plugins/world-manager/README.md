# LazyBuilder World-Manager

Paper-side authority for managed Minecraft Java 1.21.4 worlds.

World-Manager owns world lifecycle and storage operations used by the LazyBuilder desktop application and Fabric client. It is intentionally not a generic server-management framework.

## Scope

World-Manager owns:

- managed-world discovery and registry persistence;
- create, load/unload, teleport, archive and restore;
- duplicate, backup and permanent delete;
- import/export staging and bounded file transfer;
- Java/Bedrock conversion through the verified on-demand conversion runtime;
- desktop loopback control and Fabric transport adapters;
- bounded heavy-operation/task coordination;
- optional Chunky-backed pregeneration and build-world performance controls.

It does not own general plugin management, player utilities, launcher process management, permissions, economy, or unrelated server features.

## Runtime boundaries

```text
Desktop Tauri/Svelte
  -> authenticated loopback HTTP/JSON
  -> PaperLocalControlServer

Fabric client
  -> plugin messaging
  -> Paper World/Map/Transfer adapters

Both
  -> World-Manager application services
  -> registry / filesystem / conversion ownership
```

Transport adapters must not become alternate world-management implementations.

## Storage and safety

World mutation uses owned filesystem roots and explicit operation coordination. Destructive operations use reversible staging before the registry commit where applicable.

Import handling includes path-traversal protection, bounded archive extraction, file-count limits, identity cleanup, and controlled publication. Default limits are deliberately conservative for local builder machines:

```text
max import files       100,000
max uncompressed data  16 GiB
max uploaded artifact   8 GiB
```

These are safety defaults, not hard product limits. Raise them only for known large-world workflows and only when sufficient disk capacity is available.

Permanent delete requires exact display-name confirmation and server-side protection for the active fallback/default world.

## Conversion runtime

Conversion is request-scoped and never runs as a daemon. The runtime is checksum-verified and compatibility-probed before promotion.

The default update policy is `NOTIFY_ONLY`: a newer stable runtime may be detected, but it is not silently promoted merely because upstream published a new release. This keeps production behavior tied to a known-good runtime until validation is available.

## Configuration

Canonical runtime configuration is in:

```text
src/main/resources/config.yml
```

Important sections:

```text
world-manager.fallback-world
world-manager.idle-unload
world-manager.build-performance
world-manager.import
world-manager.conversion
world-manager.transfer
```

## Build and verification

From the repository root:

```bash
mvn verify
```

Repository CI also builds the Fabric client modules and Tauri/Svelte desktop application before packaging integration artifacts.

A green source/CI build proves compilation and automated tests only. Before a stable release, validate the real runtime path on a disposable Paper 1.21.4 server:

```text
boot
-> create world
-> teleport
-> update settings
-> unload/load
-> duplicate
-> backup
-> export
-> import
-> delete
-> restart
-> verify recovery and registry/filesystem consistency
```

Also validate at least one real large-world transfer and one real conversion workflow before describing the release as production-proven.

## Architecture rules

The canonical design and constraints live in:

- `docs/04-system/world-manager-architecture-lock.md`
- `docs/02-world-management/README.md`
- `docs/02-world-management/conversion.md`
- `docs/04-system/world-task-contract.md`

Prefer extending an existing service over adding another manager, filesystem authority, executor, registry, conversion runtime, or transport-specific world-operation path.

Do not introduce a new abstraction solely to reduce file size or make the architecture look more enterprise-like. Refactor an adapter only when it has acquired multiple concrete reasons to change.
