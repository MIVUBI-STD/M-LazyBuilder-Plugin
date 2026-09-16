# Terraform Manager — Paper

Server authority for LazyBuilder terrain operations. This is a standalone LazyBuilder plugin and must not depend on Axiom, ezEdits or WorldEdit/FAWE.

Responsibilities planned for the complete geometry milestone:

```text
validate operation + permission
→ reconstruct deterministic terraform-core shape
→ compute bounded voxel delta
→ queue chunk-aware batches
→ record one undo transaction per user gesture
→ apply to Paper world
```

Coloring/material policies are intentionally out of scope until geometry is accepted.
