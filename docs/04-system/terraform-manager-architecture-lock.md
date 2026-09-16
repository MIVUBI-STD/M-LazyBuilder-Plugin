# Terraform Manager Architecture Lock

LazyBuilder Terraform is a standalone Fabric + Paper editing system. Axiom, ezEdits, WorldEdit, Terrain Diffusion, Unreal voxel tools, and related projects are research references only and are not runtime dependencies.

## V1 public tool surface

- Cliff
- Ridge
- Mountain

Material/coloring is intentionally outside this milestone.

## Interaction contract

- Axiom-familiar direct world manipulation.
- LazyBuilder World/Map Manager visual language.
- LMB drag draws/extends the active terrain form.
- Mouse wheel adjusts size.
- Shift + wheel adjusts height.
- RMB flips the exposed/front side where relevant.
- Esc cancels the active stroke.
- Ctrl+Z requests operation undo.
- Preview uses the same platform-neutral shape reconstruction path as the Paper executor.
- Hover preview must invalidate when the cursor target changes; it may not remain pinned to a stale block.

## Geometry pipeline

```text
user intent
-> bounded local terrain context
-> cleaned path / footprint
-> stable local frames
-> macro form
-> meso landform events
-> bounded micro deformation
-> rooted terrain transition
-> existing-world union by additive occupancy
-> voxel occupancy
-> bounded block queue
```

Noise is deformation/detail, never the primary macro generator.

## Terrain-context / blending lock

- Context sampling is local and bounded around the cursor; no whole-world terrain scan is permitted.
- Horizontal block-face normals are valid direct orientation hints.
- For top-facing hits on slopes, the client estimates a smoothed downhill horizontal direction from nearby surface heights.
- Flat/ambiguous terrain falls back to projected player view direction.
- Orientation is locked when a stroke begins so the preview cannot flip during one gesture.
- Cliff, Ridge, and Mountain fields extend a bounded root below the sampled surface so generated mass embeds into existing ground instead of sitting on top as a detached shell.
- Root cross-section narrows with depth, preserving the visible landform while reducing seams beneath the existing surface.
- Server composition remains additive for this geometry milestone: existing non-air blocks are preserved and generated occupancy fills only air.
- Client preview shows only prospective air-to-solid additions. Existing occupied blocks inside the field are treated as already-unioned terrain and are not outlined as new work.

## Tool grammar

### Cliff

- steep exposed front
- back mass/slope
- endpoint taper
- base flare
- sparse ledges, shoulders and recesses
- continuous path with no visible segment seams
- bounded rooted transition below the sampled terrain surface

### Ridge

- continuous crest following a smoothed path
- crest wander at meso scale
- broad shoulders and two slopes
- sparse erosion-like cuts
- tapered endpoints
- rooted shoulder transition into existing terrain

### Mountain

- dominant coherent mass
- offset main crest
- secondary ridge branches
- controlled valley cuts
- macro asymmetry with bounded surface breakup
- narrowed subsurface root for footprint attachment

## Ownership

```text
mods/terraform-manager/       Fabric editor, input, UI, preview, client transport
plugins/terraform-manager/    Paper validation, queue, world mutation, history/undo
shared/terraform-core/        deterministic geometry only
shared/protocol/terraform/    bounded wire contract only
```

No Terraform implementation belongs in Utility Manager or World Manager. Shared Terraform Core must not import Minecraft, Fabric, Paper, rendering, materials, transport, or world mutation classes.

## Safety/performance

- server-authoritative request validation
- bounded candidate volume
- tick-budgeted mutation queue
- per-player bounded history
- undo stores original BlockData rather than assuming generated material
- no whole-world scans
- no unbounded per-frame shape generation
- context sampling remains bounded to a small neighborhood
- large preview fields use reduced sampling density

## Deferred until after geometry milestone

- terrain material/color grammar
- vegetation
- biome painting
- learned/diffusion runtime generation
- optional advanced expert controls

Acceptance testing remains intentionally deferred until the complete geometry/editor milestone is assembled.
