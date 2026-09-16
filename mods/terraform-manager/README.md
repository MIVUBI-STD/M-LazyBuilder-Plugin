# Terraform Manager — Fabric

Standalone LazyBuilder terrain editor client for Minecraft 1.21.4.

## UX contract

Interaction is intentionally familiar to Axiom users while remaining independent from Axiom code/runtime:

```text
Editor Mode
→ Terraform
→ Cliff | Ridge | Mountain
→ aim / preview
→ LMB drag
→ release to commit
```

Primary controls:

```text
Wheel          Size
Shift + Wheel  Height
RMB            Flip face where meaningful
Esc            Cancel active stroke
```

The viewport is primary. Tool palette and contextual options remain compact and use the same LazyBuilder visual language as World/Map Manager: dark layered surfaces, blue accent, restrained borders and compact controls.

This module will consume `shared/terraform-core`; it must not import Map Manager or Utility Manager implementation packages.
