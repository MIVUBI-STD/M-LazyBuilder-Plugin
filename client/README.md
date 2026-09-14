# LazyBuilder client modules

LazyBuilder client-side functionality is organized as three Manager-owned Fabric mods.

```text
LazyBuilder Client Suite
├── Map Manager
├── Utility Manager
└── Performance Manager
```

Map Manager owns world/map workflow, navigation, transfer UI, world settings UI, and Fabric-to-Paper World-Manager transport. Utility Manager owns passive non-building client convenience. Performance Manager owns performance status and the narrow background-FPS fallback that automatically stands down when Dynamic FPS is present.

Each Manager remains one Fabric mod and one JAR. No Manager imports another Manager's implementation packages, and subfeatures must not become separate runtime components.

Current source boundaries:

```text
client/
├── map-manager/          implemented / scope locked
├── utility-manager/      implemented / scope locked
└── performance-manager/  implemented baseline / scope locked
```

Cross-manager architecture verification is documented in `docs/04-system/client-cross-manager-audit-lock.md`.

The post-C4 builder review is documented in `docs/04-system/build-utility-overlap-audit.md`. Its first-pass decision is intentionally conservative: do not create a generic Builder Utilities tool layer and do not duplicate Vanilla, Axiom, WorldEdit/WorldEditCUI, MetaBrushes, or other specialist editing workflows. Any future builder-facing feature must first demonstrate a narrow non-tool workflow gap and fit one of the existing Manager ownership boundaries.

Session/project-context review is documented in `docs/04-system/session-project-context-audit.md`. Map Manager already has server-authoritative current managed-world context through the existing map protocol/controller path, so no additional Session Manager, Project Context service, persistent project identity, or permanent world HUD should be introduced. Future review/collaboration features must reuse that existing world context only when a concrete handoff flow requires it.
