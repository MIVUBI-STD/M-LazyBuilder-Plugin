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

The post-C4 builder review is documented in `docs/04-system/build-utility-overlap-audit.md`. Its final decision is conservative: do not create a generic Builder Utilities tool layer and do not duplicate Vanilla, Axiom, WorldEdit/WorldEditCUI, MetaBrushes, or other specialist editing workflows.

The completed non-tool reviews are:

- `docs/04-system/session-project-context-audit.md` — reuse existing authoritative managed-world context; no Session/Project Manager or permanent context HUD;
- `docs/04-system/review-collaboration-audit.md` — `Copy Review Reference` is implemented in Map Manager as an on-demand world/location/dimension handoff with no protocol change or cross-Manager dependency;
- `docs/04-system/reliability-recovery-audit.md` — no Recovery Manager, duplicate autosave/backup/snapshot/rollback, retry daemon, or resumable-transfer subsystem; keep failure context actionable and operation-local.

Final source/static readiness is documented in `docs/04-system/final-client-pre-handoff-audit.md`. That audit closes further client scope for the current phase, fixes the remaining obvious screenshot/reload/background-FPS static issues, and records the exact Minecraft 1.21.4 compile/runtime checks that still belong to the later local/Codex proof stage.

Any future builder-facing feature must begin from a concrete workflow problem and pass a fresh ownership/overlap review. The existing three Managers remain the complete LazyBuilder client architecture.
