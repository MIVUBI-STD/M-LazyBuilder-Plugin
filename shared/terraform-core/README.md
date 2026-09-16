# Terraform Core

`terraform-core` is the platform-neutral deterministic geometry kernel shared by the future Paper terraforming executor and Fabric preview/editor.

## Phase 1 scope

Only **Cliff** geometry exists in this phase. Ridge, Mountain, coloring/materials, vegetation, world placement, networking, UI, undo, blending with existing terrain, and performance batching are intentionally out of scope.

The purpose of this phase is to prove one rule before runtime integration:

> The same input parameters and seed must produce the same terrain shape for both preview and server execution.

## Shape contract

A cliff is defined by:

- origin;
- horizontal direction;
- length;
- height;
- width/depth;
- deterministic seed.

The field is intentionally asymmetric: it has a steep front face and a receding back profile. Controlled low-frequency deformation breaks up the silhouette without allowing noise to become the primary shape generator.

```text
intentional cliff profile
+ bounded seeded deformation
= final geometry field
```

`ShapeField.sample(x, y, z) <= 0` means the point belongs to the generated solid shape. The field is continuous enough for preview sampling but makes no claim to be an exact Euclidean signed-distance field.

## Ownership

This module has no Paper, Fabric, Minecraft, rendering, protocol, or material dependency. Platform adapters must consume this kernel rather than copy its formulas.

## Next gate

Do not add Ridge or Mountain until Cliff can be rendered/inspected as a preview and its silhouette is accepted. The next implementation step is a Fabric-side diagnostic preview adapter that samples this exact field without modifying the world.
