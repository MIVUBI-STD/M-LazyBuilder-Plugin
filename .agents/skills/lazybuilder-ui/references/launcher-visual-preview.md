# Launcher Visual Preview Proof

Use when a Launcher Desktop change affects layout, hierarchy, spacing, styling, page composition, responsive behavior, loading/empty/error presentation, or any visual claim that source/typecheck alone cannot falsify.

This is a proof layer, not a second Launcher implementation. ChatGPT and Codex consume the same artifacts and proof boundary; use whichever exact-run/artifact/image tools are available in the current session.

## Purpose and architecture

```text
production Launcher source
→ deterministic preview runtime fixture only
→ same Svelte components + same CSS
→ Playwright canonical captures
→ exact-commit CI artifact
→ visual inspection
```

Production continues to use Tauri runtime. Preview changes only the runtime data source.

Canonical files:

```text
apps/launcher/src/app/bridge/runtimePreviewProduct.ts
apps/launcher/src/app/bridge/runtimeProductFacade.ts
apps/launcher/ui-preview/capture.spec.mjs
.github/workflows/ui-preview.yml
```

Do not create a parallel HTML mock, duplicate component tree, Storybook-only product copy, or committed screenshot baseline merely for preview.

## Canonical artifact

Workflow:

```text
Launcher UI Preview
```

Artifact:

```text
LazyBuilder-UI-Preview-<commit-sha>
```

Representative captures currently include Server Library, Overview, Worlds, Plugins, Settings, Client Setup, setup-required state, plus `manifest.json` identifying branch/commit/viewport/proof boundary.

## Review procedure

```text
1. Resolve the exact source revision under review.
2. Find the matching successful Launcher UI Preview run, or a descendant whose relevant Launcher source is unchanged.
3. Retrieve the exact artifact.
4. Inspect manifest.json.
5. Open only the screenshots relevant to the changed claim.
6. Audit hierarchy, spacing, states, overflow, density, consistency, and visible regression.
7. Report VISUAL_RENDERED evidence separately from remaining native/runtime residue.
```

If preview fails, diagnose the first wrong boundary. Do not delete assertions merely to make the workflow green.

## Proof boundary

Preview can prove:

```text
real Svelte/CSS composition
relative spacing/density
text wrapping/truncation
canonical state presentation
page hierarchy
browser-level responsive/layout behavior
whether representative async state reaches rendered DOM
```

It does not prove:

```text
Windows chrome/native sizing
native dialogs
Tauri IPC/runtime semantics
filesystem/process behavior
OS DPI/activation edge cases
installed packaging
real external-process/network timing
```

Those require the matching semantic proof or `NATIVE_ACCEPTANCE`.

A compile-only result is insufficient for a visual claim. A preview screenshot is not native/runtime acceptance.

## Fixture discipline

Fixtures may select coherent representative state and feed production components. They must not implement product semantics again, invent capabilities, become runtime authority, or make production depend on preview-only parameters.

Prefer a few representative states over a combinatorial fixture framework.

## Anti-overdevelopment

Do not add pixel-diff approval databases, committed golden-image repositories, a second UI framework, visual-regression SaaS, or exhaustive state/resolution matrices without repeated evidence they are needed.

The goal is fast exact-revision visual inspection, not a second product or proof platform.