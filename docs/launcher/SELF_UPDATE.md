# LazyBuilder Launcher Self Update

This document defines the single release/signing authority for Launcher self-update.

## Security model

LazyBuilder uses Tauri v2 updater artifacts and signatures. Update signature verification must never be bypassed.

- The updater public key is safe to embed in the Launcher runtime.
- The updater private key must never be committed, uploaded as an artifact, or copied into user data.
- The private key exists only in the release operator's secure storage and GitHub Actions secret `TAURI_SIGNING_PRIVATE_KEY`.
- The optional key password exists only in `TAURI_SIGNING_PRIVATE_KEY_PASSWORD`.
- Release builds use `src-tauri/tauri.release.conf.json`; normal developer builds do not require signing material.

## One-time signing bootstrap

Generate the updater signing key pair on a trusted machine with the pinned Launcher toolchain. Tauri's supported signer command is:

```powershell
cd apps/launcher
npx tauri signer generate -w "$HOME\.tauri\lazybuilder-updater.key"
```

Keep `lazybuilder-updater.key` outside the repository and back it up securely. Store its private-key content in GitHub Actions secret `TAURI_SIGNING_PRIVATE_KEY` and its password in `TAURI_SIGNING_PRIVATE_KEY_PASSWORD`.

The generated public key will be embedded by the runtime updater implementation. Do not proceed with runtime update installation until that public key has been reviewed and committed as the canonical Launcher updater public key.

## Release contract

`Launcher Release` is manual and only runs from `main`.

The workflow:

1. checks that `package.json`, `Cargo.toml`, and `tauri.conf.json` have the same version;
2. verifies the release overlay and repository key-safety rules;
3. runs the full Launcher source verification;
4. builds the NSIS installer with `createUpdaterArtifacts=true`;
5. requires Tauri to emit the matching `.sig` file;
6. creates `latest.json` from the installer URL and generated signature;
7. publishes an immutable release tag `launcher-vMAJOR.MINOR.PATCH`;
8. publishes the stable channel manifest to branch `launcher-update-channel`, path `stable/latest.json`.

Stable updater endpoint:

```text
https://raw.githubusercontent.com/halokaryamedia-source/LazyBuilder-Plugin/launcher-update-channel/stable/latest.json
```

The channel branch contains distribution metadata only; it is not a source-development authority.

## Version policy

A release request must exactly match the Launcher source version. Never publish a different updater version by changing only the workflow input.

Release tags are immutable. If a release is bad, publish a new version. Do not replace a signed installer under an existing version tag.

## Runtime authority

The future runtime updater must:

- use the embedded canonical updater public key;
- use the stable channel endpoint above;
- check update availability without mutating server workspaces;
- register download/install work in `OperationRegistry` as `launcher-update`;
- expose real download progress when available;
- never advertise cancellation after the install/commit boundary;
- refuse installation while unsafe Launcher/server operations are active;
- install through Tauri updater and relaunch only after a successful verified install;
- keep Paper/runtime update logic separate from Launcher self-update.

`autoCheckUpdates` and `updateChannel` should remain hidden from normal UI until this runtime authority is active. A stored preference must not imply functionality that does not yet exist.
