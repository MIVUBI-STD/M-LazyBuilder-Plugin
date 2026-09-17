# LazyBuilder Performance Manager

LazyBuilder Performance Manager is the first-party Fabric client performance layer for Minecraft Java 1.21.4.

## Product contract

Performance Manager is a production product owner, not an experimental diagnostics helper. Its job is to keep builder-heavy Minecraft sessions responsive while preserving the user's intended scene and visual quality.

One Performance Manager = one Fabric mod = one mod id = one output JAR.

The target is to replace the external performance stack gradually with independently owned LazyBuilder implementations. External mods remain reference and migration baselines until the matching first-party capability is implemented and proven. They must not be shaded, nested, unpacked, copied, or silently treated as runtime dependencies.

Performance Manager owns client performance behavior only: frame timing/pressure, background FPS policy, conservative culling, immediate rendering efficiency, targeted memory reduction, chunk rebuild/mesh/render-region/upload/visibility/submission/buffer efficiency, and builder-render compatibility policy.

## Current renderer ownership

The first-party renderer path includes conservative culling, rebuild coalescing, block-color and block-side caches, section visibility caching, terrain-layer membership/buffer lookup caches, block-layer allocator lookup caching, thread-local SectionBuilder lookup caching, toroidal BuiltChunk storage remapping, per-layer terrain submission indexing, upload batching/pacing, terrain GPU residency accounting, oversized stale-buffer reclamation, writable GPU-buffer growth/reuse, buffer-pool pressure diagnostics, and translucent-sort coalescing.

Chunk upload batching preserves queue order and shares one bind/unbind for consecutive uploads to the same `VertexBuffer`. Normal render passes process at most 48 queued upload tasks; shutdown drains fully.

## Terrain GPU residency and reclamation

Terrain GPU residency is tracked at the existing `VertexBuffer` ownership boundary. Each buffer is associated with its section and one of the five fixed terrain layers. Successful uploads sample actual `GpuBuffer.size` capacities while upload tasks also record mesh payload bytes. Accounting regions are fixed 8x4x8 section groups.

Diagnostics therefore distinguish:

```text
resident GPU capacity
uploaded mesh payload
capacity headroom / fragmentation
peak residency
resident buffers and regions
largest region footprint/headroom
cross-region relocations
reclaimed bytes and reclaimed buffers
```

When a toroidal `BuiltChunk` slot moves into a different accounting region, LazyBuilder may reclaim stale terrain capacity. Reclamation is deliberately conservative:

```text
capacity < 2 MiB       -> keep buffer
same 8x4x8 region      -> keep buffer
not on render thread   -> keep buffer
large + cross-region   -> close old VertexBuffer and replace it with a fresh STATIC_WRITE buffer
```

Replacement happens only from `setSectionPos(long)` after vanilla has moved the section ownership. The map entry remains a normal vanilla `VertexBuffer`, so `BuiltChunk#getBuffer`, upload, draw order, mesh format, shaders, and FRAPI semantics stay unchanged. Pending uploads targeting a reclaimed closed buffer fail open through the existing upload task discard path.

This is real retained-capacity reclamation, but it is still not a physical shared GPU arena. Full render-region suballocation/multi-draw remains a later ownership step.

## FRAPI and shader compatibility boundary

Fabric Renderer API 5.x lets renderer replacements declare ownership with:

```text
fabric-renderer-api-v1:contains_renderer
```

LazyBuilder uses the same ownership marker used by Fabric Indigo. Any custom FRAPI renderer owner disables first-party chunk/meshing mixins. Terrain submission has an additional Iris gate. Compatibility uncertainty also disables first-party chunk ownership.

For the current builder stack this resolves to `iris+sodium`; Axiom and WorldEditCUI remain consumers/overlays rather than global renderer owners.

## Diagnostics

`PerformanceManagerClient.currentSnapshot()` remains on-demand. It exposes frame/memory state, chunk build/upload pressure, visibility/cache counters, upload pacing, terrain residency/payload/headroom, region churn, reclamation totals, and the detected renderer pipeline owner.

## Migration rule

External performance mods remain migration references until matching first-party behavior is implemented and proven in representative builder workloads. Custom FRAPI renderer owners keep control of the chunk pipeline while installed, Iris keeps shader-sensitive terrain submission, ImmediatelyFast keeps overlapping render/upload hooks, and FerriteCore keeps baked-quad deduplication.

## Configuration

```properties
background.enabled=true
background.unfocused_fps=30
background.minimized_fps=10
culling.entities=true
culling.block_entities=true
rendering.optimizations=true
memory.optimizations=true
```

Compatibility markers, visibility masks, upload pacing, terrain residency/reclamation thresholds, buffer growth, and allocator internals are not user-facing knobs.
