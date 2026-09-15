# Launcher Visual Preview Proof

Use this reference when a Launcher Desktop change affects layout, hierarchy, spacing, styling, page composition, responsive behavior, loading/empty/error presentation, or any issue that cannot be proven from source/typecheck alone.

## Purpose

Reduce the Local-PC feedback loop by rendering the **real Svelte Launcher UI** in a deterministic browser proof mode and publishing screenshots that ChatGPT can retrieve from GitHub Actions.

This is a proof layer, not a second Launcher implementation.

```text
production Launcher source
→ visual-preview runtime fixture only
→ same Svelte components + same CSS
→ Playwright canonical captures
→ GitHub Actions artifact
→ ChatGPT downloads and visually audits PNGs
```

Production continues to use the Tauri runtime. `visual-preview` mode changes only the runtime data source.

## Canonical files

```text
apps/launcher/src/app/bridge/runtimePreviewProduct.ts
apps/launcher/src/app/bridge/runtimeProductFacade.ts
apps/launcher/ui-preview/capture.spec.mjs
.github/workflows/ui-preview.yml
```

Do not create a parallel HTML mock, duplicate component tree, Storybook-only copy of the Launcher, or committed screenshot baseline merely to support preview.

## Canonical artifact

Workflow:

```text
Launcher UI Preview
```

Artifact:

```text
LazyBuilder-UI-Preview-<commit-sha>
```

Canonical screenshots currently include:

```text
01-server-library.png
02-overview-ready.png
03-worlds.png
04-plugins.png
05-settings.png
06-client-setup.png
07-server-setup-required.png
manifest.json
```

`manifest.json` identifies the exact branch, commit, viewport, and proof boundary.

## ChatGPT review procedure

For a Launcher visual review:

```text
1. Refresh Local HEAD.
2. Find the latest successful Launcher UI Preview run for that commit or a descendant whose Launcher source is unchanged.
3. Fetch its artifacts.
4. Download LazyBuilder-UI-Preview-<sha>.
5. Inspect manifest.json.
6. Open the relevant PNGs in ChatGPT/tooling.
7. Audit hierarchy, spacing, states, overflow, density, consistency, and obvious visual regressions.
8. Report what is visually proven separately from what still requires native Local-PC proof.
```

If the latest preview workflow fails, inspect the diagnostics artifact and fix the first wrong boundary. Do not simply remove assertions to make the workflow green.

## Proof levels

### Source proof

Can prove:

```text
types
routing
component ownership
intended state flow
static contracts
```

### Launcher UI Preview proof

Can additionally prove:

```text
actual Svelte/CSS composition
relative spacing and density
text wrapping/truncation
canonical loading/ready/problem/setup states
page hierarchy
most browser-level responsive/layout defects
whether async state actually reaches rendered DOM
```

### Local PC / native Tauri proof

Still required for:

```text
Windows window chrome and native sizing behavior
native file/folder dialogs
Tauri IPC/runtime integration
filesystem/process behavior
OS scaling/DPI differences
focus/window activation edge cases
installed packaging behavior
real runtime timing and external-process states
```

Never claim native acceptance from preview screenshots alone.

## Quality gate

For a Launcher task whose accepted result is visual/layout/state-presentation:

```text
source/typecheck green
+ relevant Launcher UI Preview screenshot inspected
+ no unresolved visual issue in canonical state
```

is the minimum pre-Local-PC proof.

A compile-only result is insufficient for a visual issue.

## Fixture discipline

The preview runtime is deterministic test data, not product authority.

- Keep fixture states internally possible and semantically coherent.
- Exercise important ready/problem/setup/empty states without inventing product capabilities.
- When a fixture exposes a real UI lifecycle bug, fix the real UI boundary rather than bypassing the assertion.
- Do not let production code depend on fixture-only data or preview query parameters.
- Prefer a few representative states over a combinatorial fixture framework.

## Anti-overdevelopment

Do not add pixel-diff baselines, screenshot approval databases, a second UI framework, or a visual-regression service until repeated evidence proves they are needed.

The goal is fast human/ChatGPT visual inspection before native testing, not a second product or test platform.
