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
- chunk rebuild, mesh, render-region, upload, visibility, terrain submission, translucent sorting, color-provider, allocator, and buffer efficiency as renderer ownership grows;
- compatibility policy for builder-critical render consumers.

Performance Manager does not own shader loading or shader-pack UX, building/editing behavior, map/world management, screenshot/chat/window convenience, automatic graphics-quality reduction, or speculative background schedulers.

## Current renderer ownership

The first-party renderer path now includes conservative culling, chunk rebuild coalescing, block-color lookup caching, block-side visibility caching, section directional visibility caching, terrain-layer membership/buffer lookup caching, block-layer allocator lookup caching, thread-local section-builder layer lookup caching, toroidal built-chunk storage remapping, per-layer terrain submission indexing, chunk upload batching/pacing, terrain GPU residency accounting, writable GPU-buffer growth/reuse, buffer-pool pressure diagnostics, and pre-scheduler translucent-sort coalescing.

The terrain submission index preserves vanilla draw order inside each render layer. It is rebuilt only when the visible built-chunk set or published chunk data changes, and it is enabled only for sufficiently large sparse layer populations where indexed traversal is estimated to visit fewer sections than five vanilla full scans.

Block-layer allocator lookup caching keeps the original `BlockBufferAllocatorStorage` instances and lifecycle intact; it only reuses the resolved allocator reference for the five fixed vanilla block render-layer identities.

`SectionBuilder.beginBufferBuilding()` is called repeatedly while one section is meshed. LazyBuilder keeps five thread-local BufferBuilder slots scoped to the current build map, so repeated solid/cutout/translucent/tripwire lookups do not repeatedly scan the five-entry map. The thread-local is removed when the build returns and automatically resets when the build map identity changes, preventing cross-worker sharing and long-lived build retention.

Chunk upload batching preserves queue order and shares one bind/unbind for consecutive uploads to the same `VertexBuffer`. Normal render passes process at most 48 queued upload tasks before yielding to the next frame so a large rebuild burst cannot monopolize one render-thread pass. Shutdown/stop paths ignore that cap and drain the queue fully so upload data and futures are not stranded.

Terrain GPU residency is now tracked at the existing `VertexBuffer` ownership boundary. Each terrain buffer is associated with its current section and one of the five fixed terrain layers; successful vertex/index uploads sample the actual `GpuBuffer.size` capacities after upload, section remaps update ownership, and buffer deletion/ChunkBuilder stop releases bookkeeping. Accounting regions are fixed 8x4x8 section groups and are diagnostics/planning units only: no render order, mesh format, shader state, or physical GPU allocation policy is changed by the ledger.

This residency foundation exposes current/peak terrain resident capacity, active resident buffers, active accounting regions, largest region footprint, and cross-region buffer relocations. Those measurements are the input for the later physical render-region arena allocator rather than a claim that the arena already exists.

Translucent sort coalescing mirrors vanilla cancellation semantics: sections without a translucent layer do not enqueue a sort task, and an unchanged normalized camera-relative position is skipped only when vanilla would also cancel it. Camera-axis cases still sort.

Full mesh replacement, physical GPU render-region arenas, terrain multi-draw submission, Fabric Renderer API ownership, and Iris/shader compatibility are not yet claimed equivalent.

A sparse/lazy `VertexBuffer` recreation path is deliberately not implemented yet. Minecraft's `VertexBuffer` constructor asserts the render thread, while meshing can run on worker threads; recreating closed GPU buffers from the mesh worker would violate the render-thread boundary. Physical arena allocation therefore remains render-thread work and will consume the residency ownership/size data introduced here.

## FRAPI and shader compatibility boundary

Fabric Renderer API 5.x lets renderer replacements declare ownership with the metadata key:

```text
fabric-renderer-api-v1:contains_renderer
```

LazyBuilder uses the same marker that Fabric Indigo uses to decide whether Indigo should stand down. This means first-party chunk/meshing mixins no longer special-case only Sodium: any installed mod declaring FRAPI renderer ownership disables LazyBuilder chunk rebuild, meshing lookup, visibility, storage, allocator, upload, translucent-sort, and related chunk-pipeline hooks.

Terrain submission has an additional Iris boundary. It is enabled only when no custom FRAPI renderer owns the pipeline and Iris is absent. Compatibility detection uncertainty also disables first-party chunk ownership rather than guessing.

For the current builder stack, this resolves to `iris+sodium`: Sodium declares FRAPI renderer ownership and Iris layers shader behavior on top of that renderer. Axiom and WorldEditCUI remain builder consumers/overlays rather than renderer owners and therefore do not globally disable the safe first-party paths by themselves.

## Diagnostics

`PerformanceManagerClient.currentSnapshot()` remains on-demand and includes chunk backlog, upload backlog, free chunk buffers, coalesced rebuild requests, buffer acquire misses, avoided upload binds, upload-budget stops, remapped storage sections, section visibility cache hits, avoided translucent sort tasks, avoided terrain-section visits, section-builder BufferBuilder cache hits, terrain GPU residency/region metrics, and the detected renderer pipeline owner.

The renderer diagnostic uses deterministic labels such as:

```text
fabric-indigo
sodium
iris+sodium
compatibility-uncertain
```

## Migration rule

External performance mods remain migration references until the matching first-party behavior is implemented and proven in representative builder workloads. Custom FRAPI renderer owners keep control of the chunk pipeline while installed, Iris keeps control of shader-sensitive terrain submission, ImmediatelyFast keeps overlapping render/upload hooks while installed, and FerriteCore keeps baked-quad deduplication while installed.

## Configuration

Current high-level configuration remains:

```properties
background.enabled=true
background.unfocused_fps=30
background.minimized_fps=10
culling.entities=true
culling.block_entities=true
rendering.optimizations=true
memory.optimizations=true
```

Implementation details such as compatibility markers, visibility masks, layer submission indexing, section-builder lookup caches, allocator lookup caching, upload grouping/pacing, terrain residency accounting, sort coalescing, provider caches, buffer growth, storage-ring mapping, and allocator behavior are not user-facing knobs.
