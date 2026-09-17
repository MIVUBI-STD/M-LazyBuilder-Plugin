# LazyBuilder Performance Manager

LazyBuilder Performance Manager is the first-party Fabric client performance layer for Minecraft Java 1.21.4.

## Product contract

Performance Manager is a production product owner, not an experimental diagnostics helper. Its job is to keep builder-heavy Minecraft sessions responsive while preserving the user's intended scene and visual quality.

One Performance Manager = one Fabric mod = one mod id = one output JAR.

The target is to replace the external performance stack gradually with independently owned LazyBuilder implementations. External mods remain reference and migration baselines until the matching first-party capability is implemented and proven. They must not be shaded, nested, unpacked, copied, or silently treated as runtime dependencies.

Performance Manager owns client performance behavior only: frame timing/pressure, background FPS policy, conservative culling, immediate rendering efficiency, targeted memory reduction, chunk rebuild/mesh/render-region/upload/visibility/submission/buffer efficiency, and builder-render compatibility policy.

## Current renderer ownership

The first-party renderer path includes conservative culling, rebuild coalescing, block-color and block-side caches, section visibility caching, terrain-layer membership/buffer lookup caches, block-layer allocator lookup caching, thread-local SectionBuilder lookup caching, toroidal BuiltChunk storage remapping, per-layer terrain submission indexing, upload batching/pacing, terrain GPU residency accounting, stale-buffer reclamation, live shared-region allocation modeling, offset-aware arena draw planning, mirrored physical shared VBO/EBO drawing, writable GPU-buffer growth/reuse, buffer-pool pressure diagnostics, and translucent-sort coalescing.

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

## Arena-aware physical draw path

`TerrainArenaDrawStateRegistry` captures vanilla vertex format, vertex/index counts, draw mode, index type, and separate vertex/index payload sizes. Allocation handles therefore provide deterministic vertex and index byte ranges.

`TerrainPhysicalArenaManager` mirrors eligible geometry into render-thread-owned region/layer buffers while retaining vanilla per-section `VertexBuffer` objects as correctness fallback:

```text
logical allocation
├─ vertex range -> shared region VBO
└─ sorted index range -> shared region EBO when present
```

Sequential-index terrain binds Minecraft's shared sequential index buffer and issues `glDrawElementsBaseVertex`. Custom/sorted-index terrain binds the region EBO and issues the same base-vertex draw with the allocation's index byte offset. The physical path validates vertex payload size, index payload size, allocation generation, arena epoch, index alignment, base-vertex range, and draw state before every draw.

A full rebuild can mirror VBO and sorted EBO together before vanilla closes the `BuiltBuffer`. Later translucent resort uploads update only the shared EBO when the logical handle generation remains stable. If allocator growth or compaction changes the handle, physical residency is invalidated and vanilla rendering remains authoritative until the next full rebuild; no speculative GPU relocation is performed.

The physical path is still mirrored rather than exclusive. Vanilla VBO/EBO state remains populated so any mismatch immediately falls back without hiding builder geometry. Final VRAM reduction therefore waits until runtime correctness and compatibility proof justify dropping duplicate vanilla backing for proven-safe residents.

Physical diagnostics include shared arena resident bytes, arena/resident counts, mirrored upload bytes, physical draw count, VBO/VAO bind reuse, resize invalidations, and current logical arena pressure.

## FRAPI and shader compatibility boundary

Fabric Renderer API 5.x lets renderer replacements declare ownership with:

```text
fabric-renderer-api-v1:contains_renderer
```

LazyBuilder uses the same ownership marker used by Fabric Indigo. Any custom FRAPI renderer owner disables first-party chunk/meshing mixins. Terrain submission has an additional Iris gate. Compatibility uncertainty also disables first-party chunk ownership.

For the current builder stack this resolves to `iris+sodium`; Axiom and WorldEditCUI remain consumers/overlays rather than global renderer owners.

## Diagnostics

`PerformanceManagerClient.currentSnapshot()` remains on-demand. It exposes frame/memory state, chunk build/upload pressure, visibility/cache counters, upload pacing, terrain residency/payload/headroom, region churn, reclamation totals, projected arena pressure, live arena allocation/fragmentation state, offset-aware draw coverage, and physical shared-buffer usage.

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

Compatibility markers, visibility masks, upload pacing, terrain residency/reclamation thresholds, arena sizing/suballocation internals, physical mirror ownership, draw-plan batching, buffer growth, and allocator details are not user-facing knobs.
