# Windows End-User Support Policy

LazyBuilder Local distribution is installer-first. End users and LocalTest machines must not build source code.

## Canonical user entrypoint

```text
LazyBuilder-Setup-Local.exe
```

The raw `lazybuilder.exe` is a diagnostics/developer artifact and is not the supported installation path.

## Current Local PC validation

The next target-machine proof is defined in:

[`local-pc-validation-plan.md`](local-pc-validation-plan.md)

Windows testing must begin from the installer and continue through Launcher, managed Java/Paper, plugins, Modrinth Client Setup, Fabric mods, real Minecraft interoperability, restart/recovery, and only then large-world/storage/conversion testing.

Do not replace installed-product testing with a local source build.

## Supported baseline

Primary target:

```text
Windows 11 x64
current-user installation
online access for first-time prerequisite/runtime provisioning
```

Compatibility target:

```text
Windows 10 x64 (1803 or newer)
```

Windows 10 compatibility is maintained because Tauri/WebView2 supports this baseline, but Windows 10 itself is outside normal Microsoft consumer support after October 2025. Windows 11 is therefore the preferred production target.

Windows on ARM is not a native target in the current build. Windows 11 ARM64 may run the x64 application through Windows emulation, but that path is not a release gate until a dedicated ARM64 build/test lane exists.

## Installer behavior

The NSIS package is configured for:

- per-user installation (`currentUser`), so normal installation does not require machine-wide app placement;
- downgrade blocking;
- WebView2 Evergreen bootstrap provisioning when missing;
- statically linked Visual C++ runtime for the LazyBuilder binary;
- no Node, npm, Rust, Cargo, Maven, Gradle, Python, Git, or MSVC requirement on the end-user machine.

The normal installer is an online bootstrap-style installer. An offline WebView2 package may be added later as a separate distribution variant if air-gapped deployment becomes an accepted requirement.

## Application runtime behavior

Application state belongs under `%LOCALAPPDATA%\\LazyBuilder` (with `%APPDATA%` only as a fallback where already implemented).

Server workspaces are user-selected and remain outside the installed application directory.

Java 21 is managed privately by LazyBuilder under `%LOCALAPPDATA%\\LazyBuilder\\runtimes\\java-21`. LazyBuilder must not modify global `PATH` or `JAVA_HOME` for an end user.

Managed Java provisioning must remain transactional:

```text
download -> SHA-256 verify -> staging -> Java 21 validation -> atomic publish -> rollback on failure
```

## Windows remote release gates

A Local installer is considered ready for target-PC testing only if the exact artifact:

1. builds from the exact `Local` commit;
2. passes source/contract checks;
3. passes Paper and Fabric build/tests;
4. passes Paper runtime smoke;
5. builds the Tauri/NSIS package;
6. installs silently in CI;
7. registers a Windows uninstaller;
8. exposes an installed LazyBuilder executable;
9. starts successfully with a sanitized PATH containing only Windows system locations;
10. publishes SHA-256 and build provenance with the installer.

The clean-PATH startup gate specifically prevents accidental runtime reliance on developer tools installed on a build machine.

These gates prove **test readiness**, not full end-user readiness.

## Windows Local PC release gates

Before discussing `Local` → `main` promotion, a representative Windows PC must additionally prove:

```text
clean install
→ Launcher first run
→ managed Java provisioning
→ Paper start/stop/restart
→ Plugin Manager representative flow
→ World Manager + Utilities Manager
→ real Modrinth profile sync
→ real Map Manager + Utility Manager inside Minecraft
→ Paper/Fabric reconnect + restart behavior
→ installer update over existing installation
→ representative large-world/storage/conversion behavior
```

The detailed acceptance criteria and defect-recording format are owned only by `local-pc-validation-plan.md`.

## Production signing

Local test installers may be unsigned. Public/stable Windows distribution should be Authenticode-signed and timestamped with a trusted code-signing certificate before being described as production-ready. Signing is intentionally not simulated or bypassed in Local.
