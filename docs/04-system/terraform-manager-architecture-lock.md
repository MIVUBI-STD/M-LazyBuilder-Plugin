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
- Exactly one Terraform keyboard shortcut is registered: **Right Shift** toggles the Terraform editor panel, matching Axiom's default Toggle Editor UI key.
- No dedicated keyboard shortcuts exist for tool selection, variation, undo, size, height, or face direction.
- Tool/variation selection and Undo belong to the Terraform UI surface.
- LMB drag draws/extends the active terrain form.
- Mouse wheel adjusts size.
- Shift + wheel adjusts height.
- RMB flips the exposed/front side where relevant.
- Opening the Terraform panel cancels an unfinished live stroke rather than leaving a hidden half-stroke active.
- Preview uses the same platform-neutral shape reconstruction path as the Paper executor.
- Hover preview must invalidate when the cursor target changes; it may not remain pinned to a stale block.
- Size/height/front/tool/variation changes surface immediate HUD feedback without requiring shortcut keys.
- Tool or variation changes cancel an in-progress stroke instead of silently changing semantics mid-gesture.
- Accepted/finished operations use concise overlay feedback; errors remain visible chat messages.

### UI style lock

- Behavior follows Axiom's editor model: open a compact editor panel, configure the tool, close the panel, then perform the actual terrain gesture directly in the world.
- Visual styling follows LazyBuilder Map Manager rather than Axiom: the same dark surfaces, borders, accent blue, text hierarchy, elevated-panel treatment, hover states, and disabled-state language are mirrored locally inside Terraform Manager.
- The panel is configuration-first rather than menu-first. It exposes only Tool, Size, Height, Variation, operation status, Undo, and concise mouse help.
- Tool and Variation use compact segmented controls; Size and Height use Map-Manager-like bordered rows with small steppers.
- Status is visually separated from settings and uses a narrow state accent: green for ready, blue while applying.
- The panel must remain visually compact so the world stays the primary work surface.

## Operation lifecycle lock

```text
stroke release
-> request encoded/sent
-> client pending state
-> server validation + queue admission
-> Accepted
-> bounded world mutation
-> Finished or Error
-> client ready state
```

- While an operation is pending, direct drawing and hover preview are suspended. This avoids stacking a new gesture onto terrain that has not finished mutating.
- The panel remains available during pending work and shows `Applying terrain...`.
- Undo is a panel action, not a keyboard shortcut.
- Undo is enabled only when the latest completed operation is eligible and there is no pending operation.
- Pending/undo state is cleared on disconnect and world/dimension identity change.
- Server undo history is cleared on player world change/quit.
- A completed server job is added to undo history only if the player is still online in the same authoritative world in which that job executed.

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

### Automatic path continuation

- Cliff and Ridge do not expose an `Attach`, `Continue`, or `Spline` mode.
- After a successful path stroke, the client keeps only a bounded continuation tail: the previous point, endpoint, front orientation, seed, and public shape settings.
- A new Cliff/Ridge stroke that starts near that endpoint and still uses compatible tool, variation, size, and height automatically overlaps the prior tail.
- The overlap moves the new stroke's start taper behind the visible join so two operations read as one terrain formation instead of two tapered pieces touching end-to-end.
- Continuation reuses the prior front orientation and operation seed to reduce visible orientation/detail discontinuity.
- Hover preview uses the same continuation tail, so the builder sees the joined result before committing.
- Continuation is cleared by world/dimension changes, disconnect/reset, undo, face reversal, Mountain selection, or materially different shape settings.
- This is a local bounded continuity aid only; it does not create a second persistent terrain registry or scan prior operations globally.

## Tool grammar

### Cliff

- steep exposed front
- back mass/slope
- endpoint taper
- base flare
- sparse ledges, shoulders and recesses
- continuous path with no visible segment seams
- bounded rooted transition below the sampled terrain surface
- nearby compatible strokes automatically overlap their tails for visual continuation

### Ridge

- continuous crest following a smoothed path
- crest wander at meso scale
- broad shoulders and two slopes
- sparse erosion-like cuts
- tapered endpoints
- rooted shoulder transition into existing terrain
- nearby compatible strokes automatically overlap their tails for visual continuation

### Mountain

- dominant coherent mass
- offset main/secondary summit structure
- hierarchical secondary ridge branches
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
- bounded global queue and bounded per-player queued operations
- operation acknowledgement occurs only after successful queue admission
- undo is rejected while that builder still has queued work, avoiding history races
- per-player bounded history
- offline players do not retain completed-operation undo history
- client editor/stroke state resets on disconnect
- client stroke/pending/undo/continuation context also resets when the active Minecraft world or dimension identity changes
- undo stores original BlockData rather than assuming generated material
- no whole-world scans
- no unbounded per-frame shape generation
- context sampling remains bounded to a small neighborhood
- preview sampling density scales with affected volume and surface output has a hard cap

## Deferred until after geometry milestone

- terrain material/color grammar
- vegetation
- biome painting
- learned/diffusion runtime generation
- optional advanced expert controls

Acceptance testing remains intentionally deferred until the complete geometry/editor milestone is assembled.
