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
- chunk rebuild, mesh, render-region, upload, visibility, color-provider, and buffer efficiency as renderer ownership grows;
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

Current migration compatibility is deliberately narrow:

- only `minecraft:` entity and block-entity types are eligible for first-party occlusion culling;
- modded/custom entity or block-entity types always render through their owning renderer;
- renderers declaring `rendersOutsideBoundingBox` always bypass culling;
- glowing, named, camera-focused, player, near-camera, stale, or uncertain targets always render;
- when the external `entityculling` mod is present, LazyBuilder culling remains inactive;
- when ImmediatelyFast is installed, first-party text/GPU redirect hooks and chunk-upload batching are not applied;
- when Sodium is installed, first-party chunk rebuild, ring-storage, chunk-upload, section-visibility, buffer-pool, block-color, and block-side-visibility mixins are not applied;
- when FerriteCore is installed, first-party baked-quad dedup mixins are not applied;
- WorldEditCUI overlay rendering remains outside the entity/block-entity culling path and is not intercepted.

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
    │   ├── ChunkRebuildPolicy
    │   ├── ChunkPipelineMetrics
    │   ├── ChunkUploadTask
    │   ├── SectionRingUpdatePolicy
    │   ├── IdentityProviderCache
    │   ├── BlockSideVisibilityCache
    │   └── GpuBufferGrowthPolicy
    ├── memory/
    │   └── MemoryDeduplicator
    ├── compatibility/
    └── diagnostics/
        └── PerformanceSnapshotReader
```

Package boundaries are introduced only when a capability becomes real. Do not create empty managers, workers, registries, caches, or configuration knobs merely to match this diagram.

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
- consecutive identical text render-layer buffer lookups inside vanilla text drawing reuse the last consumer;
- writable non-static GPU vertex buffers keep capacity, grow with bounded aligned headroom, and avoid shrink/reallocate churn;
- duplicate vanilla chunk rebuild requests are coalesced when they cannot strengthen pending rebuild state;
- block-color provider lookup uses a copy-on-write identity cache;
- pure `Block.shouldDrawSide(state, otherState, direction)` visibility decisions are cached with exact identity-based keys;
- block-side visibility cache is bounded and vanilla remains authoritative on cache miss;
- `BuiltChunkStorage` camera movement remaps only toroidal X/Z strips whose section ownership changes when that is cheaper than vanilla's full storage scan;
- chunk vertex/index uploads preserve queue order but consecutive uploads targeting the same `VertexBuffer` share one bind/unbind pair;
- `ChunkData.isVisibleThrough(from,to)` caches the 36 directional section-visibility pairs after vanilla computes each pair once, using two `long` bitmasks rather than per-section cache objects;
- queued LazyBuilder upload tasks remain individually future-backed and can still execute standalone if the optimization setting is disabled before upload;
- chunk-buffer pool starvation is observed without changing vanilla pool behavior;
- chunk-pipeline diagnostics expose task backlog, upload backlog, free buffers, buffer-acquire misses, coalesced rebuilds, avoided upload binds, remapped storage sections, and section-visibility cache hits on demand;
- identical vanilla `BakedQuad` vertex arrays are canonicalized during `BasicBakedModel.Builder` assembly;
- baked-quad canonicalization cache is concurrent and cleared on every client resource reload;
- no dedicated performance worker thread or polling service is added.

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

The production rendering subset deliberately targets redundant work before broad renderer replacement:

- text rendering reuses consecutive identical render-layer consumers;
- writable GPU buffers reuse capacity and grow with bounded aligned headroom;
- vanilla chunk rebuild scheduling drops only requests that cannot strengthen pending rebuild state;
- block color providers use rare copy-on-write registration and lock-free hot-path identity reads;
- block-side visibility caches the exact pure-input vanilla tuple and falls through on miss;
- `BuiltChunkStorage` uses toroidal-strip remapping for camera movement when fewer columns change than a full storage scan;
- chunk upload batching groups only consecutive tasks with the identical `VertexBuffer`, preserving global queue order and future completion semantics;
- section traversal reuses cached directional visibility results only after the original `ChunkOcclusionData` path has produced that exact pair once;
- unknown/non-LazyBuilder upload queue entries still run directly rather than being reordered or absorbed;
- ImmediatelyFast remains owner for overlapping text/GPU/upload behavior while installed;
- Sodium remains owner for the chunk pipeline while installed.

Full chunk mesh replacement, render-region GPU arenas, terrain draw batching, translucent sorting, and Fabric Renderer API implementation are not yet claimed equivalent. They require separate compatibility and runtime proof.

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
Chunk tasks waiting to batch
Chunks waiting to upload
Free chunk buffers
Coalesced chunk rebuild requests
Chunk buffer acquire misses
Avoided chunk upload buffer binds
Remapped chunk-storage sections
Section visibility cache hits
Minecraft chunk debug string
Minecraft entity render debug string
Minecraft particle debug string
```

Memory, option, entity, chunk, particle, and chunk-pipeline diagnostics are not sampled continuously.

## Migration capability map

External performance mods are migration references, not the architecture.

| Reference capability | First-party target | Policy |
| --- | --- | --- |
| Dynamic FPS background throttling | `background/` | retain existing LazyBuilder policy and prove parity before removing the external mod |
| EntityCulling entity/block-entity occlusion | `culling/` | first-party conservative implementation exists; keep external-owner bypass until runtime proof is complete |
| MoreCulling face/model/item-frame culling | `culling/` | adopt only visually safe, measurable cases |
| ImmediatelyFast immediate rendering efficiency | `rendering/` | text lookup, GPU growth, and upload-batching foundations exist; keep external-owner gate until runtime proof is complete |
| FerriteCore memory reductions | `memory/` | first baked-quad vertex dedup subset exists; keep FerriteCore-owner gate until runtime proof is complete |
| Sodium chunk/render pipeline | `rendering/` | rebuild coalescing, diagnostics, ring-storage remap, upload batching, section-visibility caching, block-color lookup, side-visibility caching, and buffer lifecycle foundations exist; Sodium remains owner until mesh/region/draw/FRAPI parity is implemented and proven |
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
6. chunk rebuild/mesh/render-region/upload/GPU-buffer pipeline sufficient to retire Sodium-class renderer dependency;
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

Future settings should remain high-level. Buffer growth, upload grouping, toroidal storage mapping, provider caches, visibility-cache internals, allocator details, dedup tables, and similar implementation mechanics are not normal user settings.

## External-source policy

Reference mods may be studied for documented behavior, problem decomposition, public APIs, compatibility requirements, benchmarks, and implementation ideas. Source reuse must follow the source project's license and repository policy. Where direct reuse is not appropriate, implement the behavior independently rather than porting or renaming external classes/mixins.

The finished LazyBuilder artifact must remain maintainable as a first-party implementation rather than a bundle of copied third-party internals.
