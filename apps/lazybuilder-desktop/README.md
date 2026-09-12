# LazyBuilder Desktop

`LazyBuilder.exe` is the Windows desktop entry point for the LazyBuilder suite.

## Technology

The desktop app targets .NET 8 WPF. The server workspace is Windows-first, so WPF gives a small, mature native desktop surface without bundling a browser/Electron runtime. Visual styling may follow a clean Modrinth-like navigation pattern, but the app keeps LazyBuilder's own branding and interaction model.

## Module boundaries

```text
LazyBuilder Desktop
├── Shell
├── Server-Manager
└── Plugin-Manager
```

World lifecycle is **not** implemented here. The Worlds page is a client surface for the Paper `World-Manager` authority.

### Server-Manager

Owns only:
- Paper process start/stop/restart;
- safe stop through Paper stdin;
- process/crash state;
- CPU/RAM health summary;
- basic server settings and paths required to launch Paper.

### Plugin-Manager

Owns only:
- plugin discovery and categorization;
- add/update validation;
- duplicate prevention;
- dependency/compatibility warnings;
- restart-safe enable/disable;
- safe removal while preserving plugin data by default.

## UI scope

Primary navigation stays intentionally small:

```text
Dashboard
Worlds
Plugins
Settings
```

Dashboard shows only server state, health, CPU, RAM, and start/stop/restart actions. Logs, JVM details, raw Paper configuration, and diagnostics belong under contextual problem views or Settings > Advanced.

## Maintenance rule

Server-Manager and Plugin-Manager are feature modules inside one desktop executable. They must not import each other's implementation packages. Shared desktop contracts live under a narrow `Core`/contracts layer only when genuinely needed by both modules.
