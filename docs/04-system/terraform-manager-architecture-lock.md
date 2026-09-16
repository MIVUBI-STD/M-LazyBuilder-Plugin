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

## Geometry pipeline

```text
user intent
-> cleaned path / footprint
-> stable local frames
-> macro form
-> meso landform events
-> bounded micro deformation
-> existing-world union by additive occupancy
-> voxel occupancy
-> bounded block queue
```

Noise is deformation/detail, never the primary macro generator.

## Tool grammar

### Cliff

- steep exposed front
- back mass/slope
- endpoint taper
- base flare
- sparse ledges, shoulders and recesses
- continuous path with no visible segment seams

### Ridge

- continuous crest following a smoothed path
- crest wander at meso scale
- broad shoulders and two slopes
- sparse erosion-like cuts
- tapered endpoints

### Mountain

- dominant coherent mass
- offset main crest
- secondary ridge branches
- controlled valley cuts
- macro asymmetry with bounded surface breakup

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

## Deferred until after geometry milestone

- terrain material/color grammar
- vegetation
- biome painting
- learned/diffusion runtime generation
- optional advanced expert controls

Acceptance testing remains intentionally deferred until the complete geometry/editor milestone is assembled.
