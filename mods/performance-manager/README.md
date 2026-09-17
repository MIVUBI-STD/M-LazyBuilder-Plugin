# LazyBuilder Performance Manager

LazyBuilder Performance Manager is the first-party Fabric client performance layer for Minecraft Java 1.21.4.

## Product contract

Performance Manager is a production product owner, not an experimental diagnostics helper. Its job is to keep builder-heavy Minecraft sessions responsive while preserving the user's intended scene and visual quality.

One Performance Manager = one Fabric mod = one mod id = one output JAR.

The target is to replace the external performance stack gradually with independently owned LazyBuilder implementations. External mods remain reference and migration baselines until the matching first-party capability is implemented and proven. They must not be shaded, nested, unpacked, copied, or silently treated as runtime dependencies.

Performance Manager owns client performance behavior only: frame timing/pressure, background FPS policy, conservative culling, immediate rendering efficiency, targeted memory reduction, chunk rebuild/mesh/render-region/upload/visibility/submission/buffer efficiency, and builder-render compatibility policy.

## Current renderer ownership

The first-party renderer path includes conservative culling, rebuild coalescing, block-color and block-side caches, section visibility caching, terrain-layer membership/buffer lookup caches, block-layer allocator lookup caching, thread-local SectionBuilder lookup caching, toroidal BuiltChunk storage remapping, per-layer terrain submission indexing, upload batching/pacing, terrain GPU residency accounting, stale-buffer reclamation, live shared-region allocation modeling, offset-aware arena draw planning, mirrored physical shared-VBO/base-vertex drawing for the safe sequential-index subset, writable GPU-buffer growth/reuse, buffer-pool pressure diagnostics, and translucent-sort coalescing.

Chunk upload batching preserves queue order and shares one bind/unbind for consecutive uploads to the same `VertexBuffer`. Normal render passes process at most 48 queued upload tasks; shutdown drains fully.

## Terrain GPU residency, reclamation, and arena ownership model

Terrain GPU residency is tracked at the existing `VertexBuffer` ownership boundary. Each buffer is associated with its section and one of the five fixed terrain layers. Successful uploads sample actual `GpuBuffer.size` capacities while upload tasks also record mesh payload bytes. Accounting regions are fixed 8x4x8 section groups.

Diagnostics distinguish resident GPU capacity, uploaded payload, headroom/fragmentation, peak residency, region footprint, relocation churn, and reclaimed capacity.

When a toroidal `BuiltChunk` slot moves into a different accounting region, LazyBuilder may reclaim stale terrain capacity. Reclamation remains conservative:

```text
capacity < 2 MiB       -> keep buffer
same 8x4x8 region      -> keep buffer
not on render thread   -> keep buffer
large + cross-region   -> replace stale VertexBuffer with a fresh STATIC_WRITE buffer
```

The shared-region model has three allocation layers:

- `TerrainRegionArenaPolicy`: 256-byte suballocation alignment, 1 MiB arena capacity quanta, 25% sizing headroom, and a 2 MiB minimum recoverable-capacity threshold before a region becomes a compaction candidate.
- `TerrainRegionSuballocator`: aligned first-fit suballocation with coalescing free spans and fragmentation accounting.
- `TerrainRegionAllocationRegistry`: persistent runtime ownership per 8x4x8 region and render layer. It maintains stable allocation handles, reuses slots while payload still fits, reallocates when payload outgrows a slot, compacts when total free space is sufficient but fragmented, grows an arena only when required capacity is genuinely insufficient, and releases ownership when a terrain buffer moves or dies.

The registry is wired to the same runtime lifecycle as the residency ledger, so it receives real section/layer association and actual uploaded payload sizes. A logical arena is intentionally scoped to one region plus one terrain render layer; this matches the existing layer-separated draw path and avoids cross-layer GPU-state sharing.

The residency ledger projects region-level arena pressure from current payload/capacity measurements:

```text
projected shared-arena bytes
projected arena slack bytes
compaction-candidate region count
potential arena reclaim bytes
```

The live allocation registry adds:

```text
planned live arena capacity
aligned allocated bytes
free bytes
fragmented free bytes
largest live arena
active arenas and allocations
allocation reuse / reallocation counts
compactions / arena growths
allocation failures
```

## Arena-aware draw planning

`TerrainArenaDrawStateRegistry` captures the exact vanilla draw parameters when a `BuiltBuffer` is uploaded: vertex format, vertex/index counts, draw mode, index type, and separate vertex/index payload sizes. Arena allocation sizing therefore has deterministic vertex and index byte ranges.

`TerrainArenaDrawPlanner` consumes the live allocation handle plus this draw state while preserving exact visible-section order. An arena command has explicit vertex byte offset, index byte offset, vertex stride, vertex count, and index count. Only consecutive commands in the same region/layer arena with compatible format/mode/index state can share an arena binding. Missing, stale, undersized, wrong-layer, or incomplete draw state remains an explicit vanilla fallback boundary.

Current diagnostics expose:

```text
arena-eligible draw commands
fallback draw commands
ordered arena batches
potential arena-buffer bind reductions
```

### Mirrored physical shared-VBO subset

Minecraft 1.21.4 exposes `GpuBuffer.copyFrom(ByteBuffer, offset)`, so subrange upload is available. LazyBuilder now mirrors the first safe terrain subset into a render-thread-owned `GpuBuffer` per region/layer and draws it with Minecraft's shared sequential index buffer plus `glDrawElementsBaseVertex`.

`TerrainArenaBaseVertexPolicy` requires:

```text
no custom/sorted index payload
vertex payload exactly matches vertexCount * stride
vertex byte offset aligned to vertex stride
base vertex and end vertex representable as signed ints
complete vanilla draw state
valid arena allocation
```

`TerrainPhysicalArenaManager` owns physical VBO/VAO state for this subset. It creates per-format VAOs over the shared region VBO, copies vertex payloads to logical allocation offsets before the normal vanilla upload closes the source buffer, and validates allocation generation/arena epoch before every physical draw. Arena resize discards mirrored residency for affected entries; those entries immediately fall back to their normal vanilla `VertexBuffer` until they upload again.

The physical path is intentionally mirrored rather than exclusive: the vanilla per-section `VertexBuffer` is still uploaded and retained as a correctness fallback. This means the current stage can reduce repeated terrain VAO/VBO binds for stable eligible batches, but it does not yet claim final VRAM reduction because shared and vanilla copies coexist until runtime correctness/compatibility is proven.

Commands with custom sorted indices, including translucent cases that require their own index data, stay on the vanilla path. Shared custom-index EBO ownership and multi-draw remain later steps.

Physical diagnostics include:

```text
physical arena resident bytes
physical arena count / mirrored resident buffers
total mirrored upload bytes
physical draw count
physical VBO/VAO binds and bind reuses
arena resize invalidations
```

## FRAPI and shader compatibility boundary

Fabric Renderer API 5.x lets renderer replacements declare ownership with:

```text
fabric-renderer-api-v1:contains_renderer
```

LazyBuilder uses the same ownership marker used by Fabric Indigo. Any custom FRAPI renderer owner disables first-party chunk/meshing mixins. Terrain submission has an additional Iris gate. Compatibility uncertainty also disables first-party chunk ownership.

For the current builder stack this resolves to `iris+sodium`; Axiom and WorldEditCUI remain consumers/overlays rather than global renderer owners.

## Diagnostics

`PerformanceManagerClient.currentSnapshot()` remains on-demand. It exposes frame/memory state, chunk build/upload pressure, visibility/cache counters, upload pacing, terrain residency/payload/headroom, region churn, reclamation totals, projected arena pressure, live arena allocation/fragmentation state, offset-aware arena draw coverage, base-vertex-ready coverage, physical shared-VBO usage, and the detected renderer pipeline owner.

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

Compatibility markers, visibility masks, upload pacing, terrain residency/reclamation thresholds, arena sizing/suballocation internals, physical mirror ownership, draw-plan batching, base-vertex eligibility, buffer growth, and allocator details are not user-facing knobs.
