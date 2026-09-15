# Windows End-User Support Policy

LazyBuilder Local distribution is installer-first. End users and LocalTest machines must not build source code.

## Canonical user entrypoint

```text
LazyBuilder-Setup-Local.exe
```

The raw `lazybuilder.exe` is a diagnostics/developer artifact and is not the supported installation path.

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

## Windows release gates

A Local installer is considered test-ready only if the exact artifact:

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

## Production signing

Local test installers may be unsigned. Public/stable Windows distribution should be Authenticode-signed and timestamped with a trusted code-signing certificate before being described as production-ready. Signing is intentionally not simulated or bypassed in Local.
