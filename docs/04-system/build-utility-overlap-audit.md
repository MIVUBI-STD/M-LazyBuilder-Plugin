# Build Utility Overlap Audit

## Purpose

This audit starts the post-C4 builder-facing review. The goal is not to add tools by default. The goal is to identify only the gaps that remain after accounting for Vanilla Minecraft, Axiom, WorldEdit/WorldEditCUI, MetaBrushes, and the current LazyBuilder Managers.

A builder-facing feature must be rejected when an existing familiar workflow already solves the same job well enough.

## Audit rule

Every candidate must answer four questions before implementation:

1. Is the task already handled by Vanilla, Axiom, WorldEdit/WorldEditCUI, MetaBrushes, or another established builder mod?
2. Would LazyBuilder improve the workflow without forcing builders to learn a replacement interaction?
3. Does the feature fit an existing Manager ownership boundary, or would it require inventing a new tool subsystem?
4. Is the gain large enough to justify maintenance and compatibility cost?

If the answer is weak or overlapping, the feature stays external.

## Existing ownership baseline

```text
Vanilla Minecraft
├── Creative inventory / block search (`E`)
├── Pick Block / middle click
├── hotbar / scroll interaction
├── F2 screenshots
├── F3 debug information
└── standard Creative movement

Axiom / WorldEdit ecosystem
├── selection / editing
├── measurement and spatial build inspection
├── brushes / terrain / shape editing
├── palettes and block manipulation
├── placement / transformation workflows
├── symmetry / guides where supported
├── build camera / inspection workflows
└── professional world-editing operations

MetaBrushes / specialist builder mods
└── advanced brush workflows and specialist editing behavior

LazyBuilder
├── Map Manager        -> world / map / transfer workflow
├── Utility Manager    -> passive non-build client convenience
└── Performance Manager -> performance status / resource policy
```

## Rejected overlap

The following should **not** be implemented in LazyBuilder under the current architecture.

| Candidate | Decision | Reason |
| --- | --- | --- |
| Material/block search | Reject | Creative inventory already provides the familiar `E` workflow. |
| Inventory replacement | Reject | Replaces a core Vanilla interaction without a proven gap. |
| Pick Block enhancement | Reject for now | Vanilla middle-click already owns the basic interaction. |
| Measurement/ruler | Reject | Axiom already covers the builder measurement/inspection need. |
| Selection/editing | Reject | Axiom/WorldEdit ownership. |
| Palette system | Reject | Strong overlap with existing builder tools. |
| Brush/terrain tools | Reject | Axiom/WorldEdit/MetaBrushes ownership. |
| Symmetry tools | Reject | Existing editing ecosystem should remain authoritative. |
| Placement helpers | Reject | Strong overlap with Axiom-style editing. |
| Precision build flight | Reject | Build-tool behavior; not a client utility gap. |
| Freecam/build camera | Reject | Existing builder tools already cover this category. |
| Zoom | Reject | Generic convenience with common external solutions; not core LazyBuilder value. |
| Hotbar build profiles | Reject for now | Risks creating a second inventory/build workflow. |
| NBT/block inspector | Reject | Build/editing ecosystem ownership. |
| Command macros | Reject | Creates a new command/tool workflow and shortcut burden. |
| Saved build cameras | Reject | Build camera ownership belongs to specialist tools. |
| Blueprint manipulation | Reject | Direct build/edit ownership. |
| Build HUD | Reject | Adds permanent UI and duplicates information/tools elsewhere. |
| Radial build menu | Reject | Introduces a new interaction system builders must learn. |

## Important principle: non-tool builder utilities only

If LazyBuilder adds anything builder-facing after this audit, it should first be a **non-tool utility**.

That means the feature should assist the work session without performing the build operation itself.

Examples of acceptable categories to investigate later:

```text
Session / project context
├── clear current-world/project context
├── safe status / warnings
└── workflow handoff information

Review / collaboration
├── lightweight review context
├── issue/location handoff
└── shareable non-editing references

Reliability / recovery
├── warnings around risky workflow states
├── clear failure context
└── recovery-oriented information
```

These are categories for review, not approved features.

## Interaction standard

Any surviving feature must follow the builder's existing mental model:

- prefer contextual actions over new keybinds;
- prefer Vanilla screens/interactions where possible;
- do not replace `E`, Pick Block, hotbar, F2, F3, or normal Creative movement;
- do not introduce radial menus;
- do not create a second block/material browser;
- do not make the user learn a LazyBuilder-specific editing language;
- do not duplicate Axiom or WorldEdit under a different name.

## Ownership routing

A candidate that survives overlap review still needs a valid owner:

```text
world / project / map context      -> Map Manager
passive client convenience         -> Utility Manager
performance / resource policy      -> Performance Manager
building / editing                 -> external Axiom/WorldEdit ecosystem
```

If a proposed feature does not fit one of these boundaries cleanly, that is evidence that it should remain external rather than creating another Manager.

## First-pass decision

The first-pass audit finds no justification for creating a new generic "Builder Utilities" tool layer.

The existing three Managers remain the complete LazyBuilder client architecture. Builder-specific work should proceed only through narrowly defined gaps that are non-overlapping and can be assigned to an existing Manager.

## Next review

The next review should focus only on **non-tool builder workflow gaps**. Do not revisit build/editing categories unless a concrete missing workflow is demonstrated that Vanilla/Axiom/WorldEdit cannot reasonably cover.
