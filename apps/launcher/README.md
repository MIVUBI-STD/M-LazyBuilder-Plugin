# LazyBuilder Launcher

Canonical desktop application source for LazyBuilder.

## Location

```text
apps/launcher/
```

## Stack

```text
Tauri 2
Svelte 5
Vite
TypeScript
Tailwind CSS 4
Rust
```

## Ownership

```text
src/                    presentation + application state
src/app/bridge/         typed Tauri command boundary
src-tauri/src/commands/ thin Tauri command adapters
src-tauri/src/engine/   desktop-native runtime/domain logic
```

Minecraft world authority remains in `plugins/world-manager/`. The Launcher may call its authenticated loopback bridge, but it must not duplicate world lifecycle, transfer, conversion, or filesystem ownership.

## Client Setup / Modrinth

`Client` is a global Launcher surface; it does not belong to one server workspace. LazyBuilder does not replace Modrinth App and does not launch Minecraft itself. The desktop `client_integration` owner only maintains LazyBuilder-owned Fabric components in one explicitly selected Modrinth profile.

```text
Modrinth owns
- Minecraft installation/profile
- Fabric loader
- general mods/modpacks
- launching Minecraft

LazyBuilder owns
- lazybuilder-map-manager-*.jar
- lazybuilder-utility-manager-*.jar
- lazybuilder-performance-manager-*.jar
- compatibility/status check for Minecraft 1.21.4 + Fabric
- install/update/duplicate cleanup for those three prefixes only
```

Client Setup scans known Modrinth profile locations when the global Client surface is opened/refreshed. Custom locations are selected by choosing the exact `.../profiles/<profile>` folder; LazyBuilder derives `<profile>/mods` itself. There is no background watcher.

The canonical Client Setup config is stored under `%LOCALAPPDATA%\LazyBuilder\config\client-integration.json` (with `%APPDATA%` only as an environment fallback). Existing legacy `%APPDATA%\LazyBuilder\client-integration.json` data is migrated automatically when found.

`Sync Client` is transactional across all three LazyBuilder components: new JARs are staged and verified first, current LazyBuilder-owned JARs are backed up, and a failed publish restores the previous set. Third-party mod files are never part of the transaction. Sync is blocked while a running Java/Minecraft process is using the selected profile.

## Server runtime safety

Server library entries are durable even when their storage path is temporarily unavailable; opening the unavailable entry reports the path problem instead of deleting it from the library. A server may be stopped while still in `Starting` state. Paper startup stdout/stderr is captured in the workspace LazyBuilder logs until Paper reaches `Done`, after which the normal Paper `latest.log` is the primary UI log.

Adopting a plain Paper server performs a best-effort live Java process check before any migration so files are not moved while that server is running.

## Local Windows build

Repository-root entrypoints:

```text
BUILD-LAUNCHER.cmd
UPDATE-LAUNCHER.cmd
```

Normal runtime-ready build:

```text
BUILD-LAUNCHER.cmd
→ mvn verify
→ Gradle 8.12 build for all three Fabric Managers
→ stage matching Paper core + LazyBuilder client JARs
→ npm ci
→ Svelte typecheck/build
→ cargo check/test
→ Tauri + NSIS package
```

Output:

```text
dist/LazyBuilder/
├── LazyBuilder-Setup.exe
├── LazyBuilder.exe
└── README.txt
```

For repeated installed-app testing, close LazyBuilder and use `UPDATE-LAUNCHER.cmd`. Server workspaces, selected Modrinth profile, and normal LazyBuilder user data are preserved.

Required local tools:

```text
Windows 10/11
Java 21
Apache Maven
Gradle 8.12
Node.js 24+
Rust stable toolchain
Microsoft C++ Build Tools
WebView2 runtime
```

## Bundled runtime resources

Runtime-ready builds require tested artifacts from the same source revision:

```text
src-tauri/resources/core/
├── World-Manager-0.1.0-SNAPSHOT.jar
└── Utilities-Manager-0.1.0-SNAPSHOT.jar

src-tauri/resources/client-mods/
├── lazybuilder-map-manager-0.1.0-SNAPSHOT.jar
├── lazybuilder-utility-manager-0.1.0-SNAPSHOT.jar
└── lazybuilder-performance-manager-0.1.0-SNAPSHOT.jar
```

Generated JARs are staged by local build/CI and are not committed.

For explicit Launcher compile/typecheck work only:

```powershell
cd apps\launcher
.\build-local.ps1 -AllowMissingCore
```

Compile-only mode is not suitable for fresh-server or Client Setup runtime validation.

## Protocol boundary

The Launcher expects desktop loopback protocol version `2`. This contract is separate from the Minecraft World Control V5 and Map Action V2 protocols.
