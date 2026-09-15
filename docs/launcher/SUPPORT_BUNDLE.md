# LazyBuilder Support Bundle

This document defines the diagnostic export boundary for the Launcher.

## Purpose

Support Bundle creates a local ZIP that can be inspected or shared manually when troubleshooting the Launcher. Creation never uploads data and never opens a network connection by itself.

The export is registered in the canonical `OperationRegistry` as:

```text
kind: export-support-bundle
resource: launcher:support-bundle
```

It is intentionally non-cancellable because the current bounded export is short and has no cooperative cancellation boundary.

## Included data

The archive may contain only:

```text
README.txt
diagnostics.json
operations.json
startup.json
logs/launcher.log
logs/launcher.1.log ... launcher.4.log when present
```

Launcher logs are already bounded by the Diagnostics authority and the exporter refuses unexpectedly oversized log files.

## Explicit exclusions

The exporter must not copy or enumerate:

- Minecraft worlds or region files;
- Paper/server configuration files;
- plugin JAR contents;
- plugin configuration or plugin data;
- client profile files or Minecraft account data;
- authentication tokens, cookies, credentials, or API keys;
- updater/private signing keys;
- arbitrary user documents;
- server logs.

If troubleshooting later requires any excluded file, it must be requested as a separate, explicit user action rather than silently expanding Support Bundle scope.

## Path redaction

Before writing JSON/log text, the exporter redacts known sensitive location roots:

```text
LOCALAPPDATA
APPDATA
USERPROFILE
HOME
all currently registered LazyBuilder workspace paths
```

Known workspace paths become `<WORKSPACE>` and user-data roots become their symbolic markers, for example `<LOCALAPPDATA>`.

This is a best-effort path privacy boundary, not a general secret scanner. Logs must therefore continue to avoid writing secrets in the first place.

## Filesystem publication

Export uses a staging file next to the selected destination:

```text
<target>.zip.incoming
```

The archive is finalized before publication. If the destination already exists, the existing file is temporarily moved aside and restored if publication fails.

No support bundle state participates in server/workspace recovery because the export is diagnostic-only and does not mutate server state.

## UI contract

Global Launcher Settings exposes **Export support bundle** and must explain that:

- the ZIP is created locally;
- nothing is uploaded automatically;
- server/world/plugin data is excluded;
- known paths are redacted.

The file picker appears before an Activity operation is created. Cancelling the picker therefore creates no activity-history noise.

## Future changes

Any proposal to add more data must answer all of the following before implementation:

```text
Is this data required to diagnose a concrete Launcher failure?
Can a smaller metadata representation replace the raw file?
Can it contain credentials, private content, or player data?
Can it be bounded deterministically?
Can it be redacted reliably?
Does the UI clearly disclose that it is included?
```

Default to exclusion when any answer is unclear.
