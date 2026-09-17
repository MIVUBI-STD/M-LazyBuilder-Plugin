# LazyBuilder Performance Manager

LazyBuilder Performance Manager is the first-party Fabric client performance layer for Minecraft Java 1.21.4.

## Product contract

Performance Manager is a production product owner, not an experimental diagnostics helper. Its job is to keep builder-heavy Minecraft sessions responsive while preserving the user's intended scene and visual quality.

One Performance Manager = one Fabric mod = one mod id = one output JAR.

The target is to replace the external performance stack gradually with independently owned LazyBuilder implementations. External mods remain reference and migration baselines until the matching first-party capability is implemented and proven. They must not be shaded, nested, unpacked, copied, or silently treated as runtime dependencies.

Performance Manager owns only client performance behavior:

- frame timing and frame-pressure diagnostics;
- background/unfocused resource policy;
- entity and block-entity visibility culling;
- conservative model/face culling where visual correctness is provable;
- immediate-mode/HUD/screen rendering efficiency;
- targeted memory reductions and deduplication;
- chunk rebuild, mesh, render-region, and buffer efficiency as renderer ownership grows;
- compatibility policy for builder-critical render consumers.

Performance Manager does not own shader loading or shader-pack UX, building/editing behavior, map/world management, screenshot/chat/window convenience, automatic graphics-quality reduction, or speculative background schedulers.

## Builder performance rule

Optimization success is not defined by peak FPS in an empty vanilla world. Representative workload includes large builds, high render distance, many entities/block entities, rapid creative flight, frequent chunk updates, large resource packs, Axiom, WorldEditCUI, and other builder-facing overlays.

Relevant proof targets include average frame time/FPS, 1% low behavior, worst recent frame time, camera-motion stutter, chunk rebuild/upload latency, entity-heavy render cost, retained memory, and GC pressure.

Performance Manager must preserve visual intent:

```text
same configured scene + less unnecessary work
```

It must not silently lower render distance, particle quality, graphics mode, entity distance, shader quality, or another user-selected visual setting in response to load.

## Correctness policy

Rendering optimizations are conservative and fail open.

```text
visibility uncertain -> render
compatibility uncertain -> use vanilla/Fabric path
unsupported renderer state -> bypass optimization
```

A missed optimization is acceptable. Incorrectly hiding a builder-visible entity, block entity, preview, guide, selection, overlay, or model is not.

Builder-critical compatibility takes priority over marginal frame savings. Axiom, WorldEditCUI, Iris, Fabric Renderer API consumers, resource packs, and custom model/render paths are explicit compatibility surfaces when the affected capability is implemented.

Current culling compatibility policy is deliberately narrow:

- only `minecraft:` entity and block-entity types are eligible for first-party occlusion culling;
- modded/custom entity or block-entity types always render through their owning renderer;
- renderers declaring `rendersOutsideBoundingBox` always bypass culling;
- glowing, named, camera-focused, player, near-camera, stale, or uncertain targets always render;
- when the external `entityculling` mod is present, LazyBuilder culling remains inactive to avoid competing render owners during migration;
- WorldEditCUI overlay rendering remains outside the entity/block-entity culling path and is not intercepted.

Rendering-efficiency mixins are migration-aware. When the external `immediatelyfast` mod is installed, the first-party text-buffer and GPU-buffer mixins are not applied at all, avoiding redirect conflicts while migration is incomplete.

Chunk rebuild coalescing is also migration-aware. When Sodium is installed, the first-party `ChunkBuilder.BuiltChunk` rebuild mixin is not applied so Sodium remains the sole chunk-render owner during migration.

Memory-dedup mixins are also migration-aware. When `ferritecore` is present, the first-party baked-quad accessor and builder mixin are not applied so only one memory owner canonicalizes baked quad storage.

## Runtime model

The existing runtime remains the single authority:

```text
PerformanceManagerClient
└── PerformanceRuntime
    ├── frame/
    │   ├── FrameMonitor
    │   └── FramePressure
    ├── background/
    │   └── BackgroundResourcePolicy
    ├── culling/
    │   ├── CullingRuntime
    │   └── VisibilityDecision
    ├── rendering/
    │   └── ChunkRebuildPolicy
    ├── memory/
    │   └── MemoryDeduplicator
    ├── compatibility/    # explicit bypass/integration policy
    └── diagnostics/
        └── PerformanceSnapshotReader
```

Package boundaries may be introduced incrementally as a capability becomes real. Do not create empty managers, workers, registries, caches, or configuration knobs merely to match this diagram.

## Implemented behavior

The currently implemented first-party behavior is production-owned:

- allocation-free rolling frame timing over a bounded 60-frame window;
- target-aware frame-pressure states: `NORMAL`, `ELEVATED`, and `HEAVY`;
- render-gap protection so world loading, disconnects, unfocused windows, and minimized windows are not counted as frame spikes;
- first-party unfocused/minimized FPS policy;
- conservative entity and block-entity occlusion culling with bounded end-of-tick evaluation;
- culling render hooks never raycast directly; stale/unknown state renders and is queued for later evaluation;
- opaque full cubes are the only definite ray occluders; partial/transparent collision shapes are stepped through and uncertainty fails open;
- modded/custom render types bypass culling;
- consecutive identical text render-layer buffer lookups inside vanilla text drawing reuse the last consumer instead of repeatedly querying the provider;
- writable non-static GPU vertex buffers are not shrunk and reallocated when the current allocation already fits the upload; static-write buffers retain vanilla resize behavior;
- duplicate vanilla chunk rebuild requests are coalesced when they cannot strengthen the already-pending rebuild state; an incoming important rebuild still upgrades a pending normal rebuild;
- chunk rebuild coalescing is disabled while Sodium is present so the migration-source renderer keeps sole ownership;
- rendering-efficiency mixins are disabled while ImmediatelyFast is present to keep one active owner during migration;
- identical vanilla `BakedQuad` vertex arrays are canonicalized during `BasicBakedModel.Builder` assembly so duplicate quads can share backing storage;
- baked-quad canonicalization cache is concurrent and cleared on every client resource reload so old model arrays are not retained across reload generations;
- memory-dedup mixins are disabled while FerriteCore is present to keep one active owner during migration;
- on-demand performance snapshots containing Minecraft/client state without a metrics-history database;
- no dedicated performance worker thread or polling service.

One world-render callback records focused world frame timing. One end-client-tick hook updates the background FPS policy and bounded culling work.

## Background resource policy

Current defaults:

```properties
background.enabled=true
background.unfocused_fps=30
background.minimized_fps=10
```

The policy changes only Minecraft's temporary inactivity FPS limiter. It does not rewrite the user's configured foreground video-option FPS limit. When focus returns, the current user limit remains authoritative.

## Culling

Current defaults:

```properties
culling.entities=true
culling.block_entities=true
```

The culling path is intentionally conservative. It samples multiple points on vanilla entity/block-entity bounds, caches fresh results briefly, invalidates on camera/target movement or world changes, and limits evaluation work per client tick. If evaluation cannot establish safe occlusion, the target renders.

External EntityCulling remains the active owner when its mod id is present. Remove that migration dependency only after representative Minecraft runtime proof shows the first-party path is correct for the intended builder workload.

## Rendering efficiency

Current default:

```properties
rendering.optimizations=true
```

The production rendering subset deliberately targets low-risk redundant work before broad renderer replacement:

- text rendering caches the immediately previous render-layer consumer inside a vanilla `TextRenderer.Drawer`, avoiding repeated `VertexConsumerProvider#getBuffer` calls for consecutive glyph work on the same layer;
- vertex uploads keep an existing writable non-static GPU allocation when it is already large enough instead of shrinking/reallocating it for each smaller upload;
- static-write GPU buffers preserve vanilla resizing semantics;
- vanilla chunk rebuild scheduling drops only requests that cannot strengthen an already-pending rebuild state;
- a pending normal rebuild still accepts an incoming important request so rebuild priority semantics are preserved;
- when ImmediatelyFast is installed, the text/GPU redirect mixins are rejected by the mixin plugin;
- when Sodium is installed, the chunk rebuild mixin is rejected by the mixin plugin.

HUD batching, screen batching, sign atlas buffering, map atlas generation, GL error-check changes, full chunk mesh replacement, render-region management, translucent sorting, and Fabric Renderer API implementation are not assumed equivalent merely because the reference mods contain them. They require separate correctness/compatibility proof before adoption.

## Memory efficiency

Current default:

```properties
memory.optimizations=true
```

The first production memory subset is deliberately narrow:

- only vanilla `BasicBakedModel.Builder` quad assembly is targeted;
- equal `int[]` vertex payloads share one canonical array instance;
- array identity is reused without changing tint, face, sprite, shade, light-emission, or model-selection semantics;
- the canonicalization table is cleared during client resource reload;
- when FerriteCore is installed, first-party baked-quad dedup mixins are not applied.

Block-state cache replacement, fast state maps, multipart predicate deduplication, model-side compaction, threading-detector changes, and other FerriteCore features remain out of the implemented subset until measured builder workloads justify them.

## Diagnostics

`PerformanceManagerClient.currentSnapshot()` captures diagnostics on demand:

```text
FPS
Current frame time
Rolling average frame time
Worst recent frame time
JVM used / max memory
Render distance
Simulation distance
Window focused / minimized state
Current frame pressure
Completed chunk count
Minecraft chunk debug string
Minecraft entity render debug string
Minecraft particle debug string
```

Memory, option, entity, chunk, and particle diagnostics are not sampled continuously.

## Migration capability map

External performance mods are migration references, not the architecture.

| Reference capability | First-party target | Policy |
| --- | --- | --- |
| Dynamic FPS background throttling | `background/` | retain existing LazyBuilder policy and prove parity before removing the external mod |
| EntityCulling entity/block-entity occlusion | `culling/` | first-party conservative implementation exists; keep external-owner bypass until runtime proof is complete |
| MoreCulling face/model/item-frame culling | `culling/` | adopt only visually safe, measurable cases |
| ImmediatelyFast immediate rendering efficiency | `rendering/` | first low-risk text lookup and GPU resize subset exists; keep external-owner mixin gate until runtime proof is complete |
| FerriteCore memory reductions | `memory/` | first baked-quad vertex dedup subset exists; keep FerriteCore-owner gate until runtime proof is complete |
| Sodium chunk/render pipeline | `rendering/` | first vanilla chunk-rebuild coalescing foundation exists; Sodium remains the chunk-render owner until mesh/region/buffer/FRAPI parity is implemented and proven |
| Reese's Sodium Options | settings presentation | unnecessary after first-party settings own first-party capabilities |
| Sodium Extra | capability-by-capability | retain only performance behavior that fits this contract; cosmetic convenience is out of scope |
| Chunks Fade In | none | visual effect; not a Performance Manager requirement |

A migration source is retired only after the first-party capability that replaces it has matching representative proof. Removing a JAR is never used as evidence that replacement behavior exists.

## Delivery phases

Implementation order is deliberate:

1. production contract, runtime/config foundation, and diagnostics;
2. entity and block-entity culling;
3. conservative face/item-frame culling where correctness can be proven;
4. proven immediate-mode/HUD/screen/buffer optimizations;
5. targeted memory reductions;
6. chunk rebuild/mesh/render-region/GPU-buffer pipeline sufficient to retire Sodium-class renderer dependency;
7. one familiar LazyBuilder settings surface exposing only meaningful user decisions.

Do not advance a later phase by creating placeholder toggles for behavior that is not implemented.

## Configuration rules

Only implemented user decisions belong in persisted preferences. Internal implementation details remain internal unless a real compatibility or user-choice requirement proves otherwise.

Current configuration:

```properties
background.enabled=true
background.unfocused_fps=30
background.minimized_fps=10
culling.entities=true
culling.block_entities=true
rendering.optimizations=true
memory.optimizations=true
```

Future settings should remain high-level. Buffer strategies, chunk rebuild coalescing, visibility-cache TTLs, mesh allocator details, dedup tables, and similar implementation mechanics are not normal user settings.

## External-source policy

Reference mods may be studied for documented behavior, problem decomposition, public APIs, compatibility requirements, benchmarks, and implementation ideas. Source reuse must follow the source project's license and repository policy. Where direct reuse is not appropriate, implement the behavior independently rather than porting or renaming external classes/mixins.

The finished LazyBuilder artifact must remain maintainable as a first-party implementation rather than a bundle of copied third-party internals.
