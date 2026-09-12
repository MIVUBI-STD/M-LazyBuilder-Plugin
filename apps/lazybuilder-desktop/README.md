# LazyBuilder Desktop

`LazyBuilder.exe` is the Windows desktop entry point for the LazyBuilder server workspace.

## Ownership

The desktop app owns only desktop concerns:

```text
Server-Manager
  start / stop / restart Paper
  Java 21 discovery and validation
  process crash state
  CPU / RAM health sampling
  manager configuration

Plugin-Manager
  scan plugin JARs
  categorize plugins
  install / update
  enable / disable through restart-safe file placement
  dependency and compatibility checks
  duplicate detection and safe resolution
  preserve plugin data by default on removal
```

World lifecycle is **not** implemented here. The future `Worlds` page must call the Paper `World-Manager` through a structured local control bridge so the desktop app never becomes a second world authority.

## Navigation

The normal UI stays deliberately small:

```text
Dashboard
Worlds
Plugins
Settings
```

Dashboard exposes only server state, health, CPU, RAM, and Start / Stop / Restart.

Plugins exposes normal plugin-management tasks. Technical metadata belongs behind detail/advanced surfaces rather than the primary list.

## Workspace assumption

The packaged executable is intended to live at the LazyBuilder workspace root next to `server/`, `world-system/`, and `tools/`.

For development, set:

```text
LAZYBUILDER_WORKSPACE_ROOT=<workspace path>
```

to explicitly point the desktop app at a test workspace.

## Runtime files

Desktop-only state is stored under:

```text
tools/lazybuilder/
├── server-manager.json
├── plugin-registry.json
└── plugin-backups/
```

Paper configuration remains owned by Paper/server files. World storage remains owned by World-Manager and `world-system/`.

## Maintenance rules

- no hot plugin unload/reload hacks;
- no automatic dependency downloads in the first version;
- no background polling while Paper is offline;
- one owner for Java validation, plugin inventory, and world lifecycle;
- file mutations use bounded/atomic operations where practical;
- corrupt plugin JARs are surfaced as `Problem` instead of silently ignored;
- duplicate plugin JARs must be resolved explicitly and removed copies are backed up first;
- category overrides are persistent but never rewrite plugin metadata;
- CI compile proof is separate from live Paper/runtime proof.
