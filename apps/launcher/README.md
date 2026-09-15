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

`Client` is a global Launcher surface; it does not belong to one server workspace. LazyBuilder does not replace Modrinth App and does not launch Minecraft itself. The desktop `client_integration` owner only maintains the LazyBuilder Fabric components required by V1 in one explicitly selected Modrinth profile.

```text
Modrinth owns
- Minecraft installation/profile
- Fabric loader
- general mods/modpacks
- launching Minecraft

LazyBuilder V1 owns
- lazybuilder-map-manager-*.jar
- lazybuilder-utility-manager-*.jar
- compatibility/status check for Minecraft 1.21.4 + Fabric
- install/update/duplicate cleanup for those two prefixes only
```

`mods/performance-manager/` remains deferred source for measured future work. It is not a required V1 client component, is not installed by Client Setup, and is not bundled into the Launcher package.

Client Setup scans known Modrinth profile locations when the global Client surface is opened/refreshed. Custom locations are selected by choosing the exact `.../profiles/<profile>` folder; LazyBuilder derives `<profile>/mods` itself. There is no background watcher.

The canonical Client Setup config is stored under `%LOCALAPPDATA%\LazyBuilder\config\client-integration.json` (with `%APPDATA%` only as an environment fallback). Existing legacy `%APPDATA%\LazyBuilder\client-integration.json` data is migrated automatically when found.

`Sync Client` is transactional across the required LazyBuilder components: new JARs are staged and verified first, current LazyBuilder-owned JARs are backed up, and a failed publish restores the previous set. Third-party mod files are never part of the transaction. Sync is blocked while a running Java/Minecraft process is using the selected profile.

## Server runtime safety

Server library entries are durable even when their storage path is temporarily unavailable; opening the unavailable entry reports the path problem instead of deleting it from the library. A server may be stopped while still in `Starting` state. Paper startup stdout/stderr is captured in the workspace LazyBuilder logs until Paper reaches `Done`, after which the normal Paper `latest.log` is the primary UI log.

Adopting a plain Paper server performs a best-effort live Java process check before any migration so files are not moved while that server is running.

Server start does not rewrite Paper gameplay/performance configuration and the Launcher does not run a background CPU-priority governor. Performance tuning must be justified by local/runtime evidence before becoming product behavior.

## Local Windows build

Repository-root entrypoints:

```text
SETUP-DEV.cmd
CHECK-DEV.cmd
BUILD-LAUNCHER.cmd
UPDATE-LAUNCHER.cmd
```

`toolchain.json` is the canonical build-tool policy. Do not duplicate or float tool versions in local scripts or CI.

Normal runtime-ready build:

```text
BUILD-LAUNCHER.cmd
→ toolchain preflight
→ mvnw.cmd verify (repo-managed Maven 3.9.16)
→ gradlew.bat build (repo-managed Gradle 8.12)
→ stage and verify matching Paper/client artifacts
→ npm ci
→ Svelte typecheck/build
→ cargo check/test --locked
→ Tauri + NSIS package
```

The repository wrappers download Maven/Gradle only when their pinned version is not already cached under `%LOCALAPPDATA%\LazyBuilder\build-tools`. Downloaded distributions are checked against their official SHA-512/SHA-256 checksum before extraction.

Output:

```text
dist/LazyBuilder/
├── LazyBuilder-Setup.exe
├── LazyBuilder.exe
└── README.txt
```

For repeated installed-app testing, close LazyBuilder and use `UPDATE-LAUNCHER.cmd`. Server workspaces, selected Modrinth profile, and normal LazyBuilder user data are preserved.

Required developer tools:

```text
Windows 10/11 x64
Eclipse Temurin / OpenJDK 21 (runtime-ready server builds)
Node.js 24.x LTS + npm
Rust toolchain selected by repository rust-toolchain.toml
Microsoft Visual Studio 2022 Build Tools (Desktop development with C++)
WebView2 runtime
Git for Windows (source workflow)
```

Not required as global installs:

```text
Apache Maven   -> repo wrapper
Gradle         -> repo wrapper
Python         -> no longer part of Launcher build verification
Tauri CLI      -> npm development dependency
```

## Bundled runtime resources

Runtime-ready builds require tested artifacts from the same source revision:

```text
src-tauri/resources/core/
├── World-Manager-0.1.0-SNAPSHOT.jar
└── Utilities-Manager-0.1.0-SNAPSHOT.jar

src-tauri/resources/client-mods/
├── lazybuilder-map-manager-0.1.0-SNAPSHOT.jar
└── lazybuilder-utility-manager-0.1.0-SNAPSHOT.jar
```

Generated JARs are staged by local build/CI and are not committed.

For explicit Launcher compile/typecheck work only:

```powershell
cd apps\launcher
.\build-local.ps1 -AllowMissingRuntime
```

Compile-only mode is not suitable for fresh-server or Client Setup runtime validation.

## Protocol boundary

The Launcher expects desktop loopback protocol version `2`. This contract is separate from the Minecraft World Control V5 and Map Action V2 protocols.
