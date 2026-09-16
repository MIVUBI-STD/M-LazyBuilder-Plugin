# Terraform Core

`terraform-core` is the platform-neutral deterministic geometry kernel for LazyBuilder Terraform.

## Product boundary

LazyBuilder Terraform is standalone. Axiom, ezEdits, WorldEdit/FAWE and other build tools are research references only and are not runtime dependencies.

The public terrain vocabulary is intentionally small:

```text
Cliff
Ridge
Mountain
```

Coloring, materials and vegetation remain outside the geometry milestone.

## Geometry model

The kernel follows a hierarchical terrain model:

```text
user intent
→ cleaned path / footprint
→ stable local frame
→ macro landform
→ sparse meso structure
→ bounded micro deformation
→ shape field
→ voxelization / world adapter
```

Noise deforms an intentional form; noise does not define the macro terrain.

### Cliff

Path-driven asymmetric terrain mass with an exposed front wall, crest, back mass, endpoint taper and controlled wall breakup. The path implementation is continuous and does not place repeated prefab segments.

### Ridge

Path-driven symmetric crest with two coherent slopes. It shares path cleanup and stable frames with Cliff.

### Mountain

Footprint-driven mass with deliberate asymmetry and a secondary ridge bias. It is not a cone distorted by unrestricted noise.

## Public controls

The editor should expose only:

```text
Tool: Cliff | Ridge | Mountain
Size
Height
Variation: Soft | Natural | Dramatic
```

Orientation, spline frames, macro sections, falloff and deformation are engine responsibilities.

## Runtime ownership

```text
shared/terraform-core   deterministic geometry only
mods/terraform-manager  Fabric editor/input/preview adapter
shared/protocol         neutral Paper↔Fabric operation contract
plugins/terraform-manager Paper validation, batching, history and world apply
```

Do not place Terraform implementation back into Utility Manager. UI behavior may be familiar to Axiom users, while visual design must follow the existing LazyBuilder UI language.
