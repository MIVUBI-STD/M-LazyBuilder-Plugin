# LazyBuilder Performance Manager

LazyBuilder Performance Manager is the first-party Fabric client performance layer for Minecraft Java 1.21.4.

## Product contract

Performance Manager is a production product owner, not an experimental diagnostics helper. Its job is to keep builder-heavy Minecraft sessions responsive while preserving the user's intended scene and visual quality.

One Performance Manager = one Fabric mod = one mod id = one output JAR.

The target is to replace the external performance stack gradually with independently owned LazyBuilder implementations. External mods remain reference and migration baselines until the matching first-party capability is implemented and proven. They must not be shaded, nested, unpacked, copied, or silently treated as runtime dependencies.

Performance Manager owns client performance behavior only: frame timing/pressure, background FPS policy, conservative culling, immediate rendering efficiency, targeted memory reduction, chunk rebuild/mesh/render-region/upload/visibility/submission/buffer efficiency, and builder-render compatibility policy.

## Current renderer ownership

The first-party renderer path includes conservative culling, rebuild coalescing, block-color and block-side caches, section visibility caching, terrain-layer membership/buffer lookup caches, block-layer allocator lookup caching, thread-local SectionBuilder lookup caching, toroidal BuiltChunk storage remapping, per-layer terrain submission indexing, upload batching/pacing, terrain GPU residency accounting, stale-buffer reclamation, live shared-region allocation modeling, offset-aware arena draw planning, mirrored physical shared VBO/EBO drawing, GPU-to-GPU arena relocation, explicit per-draw transform streaming, a guarded true multi-draw submission backend, writable GPU-buffer growth/reuse, buffer-pool pressure diagnostics, and translucent-sort coalescing.

Chunk upload batching preserves queue order and shares one bind/unbind for consecutive uploads to the same `VertexBuffer`. Chunk-meshing hot paths also avoid transient block-side lookup keys on cache hits and retain one cleared section-builder lookup cache per worker thread instead of reallocating it for every section build. Foreground upload work is paced by frame pressure and capped again by the adaptive Performance Governor. The governor observes p95 frame time, upload/build backlog, free buffers, JVM pressure, and sampled culling profitability; it can enter throughput, balanced, or protective mode without changing Minecraft correctness work. Terrain submission also reuses per-layer transform builders and grow-only native packing buffers to reduce per-frame allocation and direct-buffer churn.

Chunk rebuild backpressure uses the same `FramePressure` signal rather than creating a second scheduler. Only non-prioritized work can be deferred, and only while pressure is heavy, at least eight vanilla tasks are already queued, and the chunk buffer pool has one or fewer free buffers. The deferred queue is capped at 128 tasks, fails open when full, and releases work at 16/4/1 tasks per tick for normal/elevated/heavy pressure respectively, so sustained heavy pressure still makes forward progress. `reset` and `stop` cancel deferred tasks instead of carrying stale work into another builder lifecycle.

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

A full rebuild can mirror VBO and sorted EBO together before vanilla closes the `BuiltBuffer`. Later translucent resort uploads update only the shared EBO when the logical handle generation remains stable.

Physical arena growth and logical compaction no longer have to discard every mirrored resident. LazyBuilder owns a small raw render-thread GL buffer wrapper for the shared arena backing and can rebuild an arena by GPU-to-GPU copying each still-valid resident from its old VBO/EBO offsets into the current logical allocation offsets. The VAO set is recreated against the new backing. Entries that cannot be proven valid fall back individually to vanilla instead of blocking the rest of the arena.

Relocation remains conservative: if a current handle is missing, changes region, has invalid draw state, or cannot fit the rebuilt backing safely, that resident is invalidated and vanilla rendering remains authoritative until a later upload. Diagnostics report relocation count, copied bytes, and relocation fallbacks separately from ordinary invalidations.

Sequential-index physical residents now have a guarded promotion path from mirrored ownership to exclusive shared-arena ownership. Each resident must accumulate 600 successful physical draws without re-upload, relocation, invalidation, or multi-draw submission failure before its duplicate vanilla GPU backing can be retired. Custom/sorted-index residents remain mirrored and are excluded from promotion.

Retirement preserves the existing VertexBuffer identity while releasing its duplicate per-section GPU storage. The physical arena remains authoritative for exclusive residents. Before a vanilla fallback, arena relocation, region ownership move, or rendering-optimization disable can become authoritative again, LazyBuilder reconstructs the vanilla vertex backing by GPU-to-GPU copy from the shared arena and reattaches it to the existing VAO. Multi-draw failure first falls back to physical single-draw; vanilla fallback is used only after backing recovery succeeds. A failed recovery blocks the unsafe vanilla draw instead of submitting against retired storage.

This promotion remains intentionally limited to sequential-index terrain. Runtime diagnostics expose current exclusive resident count, retired bytes, promotions, recoveries, and recovery failures so live validation can prove that VRAM reduction remains reversible before the scope is expanded.

## Guarded multi-draw contract

Minecraft 1.21.4 terrain rendering normally uploads a different `ModelOffset` uniform for every visible `BuiltChunk`. LazyBuilder materializes that translation in `TerrainDrawTransformStream`, packs physical-ready draws in `TerrainMultiDrawCommandStream`, and exposes a render-thread UBO backend through `TerrainPerDrawShaderBackend`.

A shader must opt in explicitly. The guarded contract is:

```text
std140 uniform block: LazyBuilderDrawTransforms
integer uniform:      LazyBuilderDrawBase
draw-id support:      OpenGL 4.6 or ARB_shader_draw_parameters
```

The intended shader indexing rule is equivalent to:

```text
transform = LazyBuilderDrawTransforms[LazyBuilderDrawBase + gl_DrawID]
```

`TerrainMultiDrawSubmissionBackend` then groups only consecutive vanilla-order commands with the same region/layer arena, vertex format, draw mode, index type, and sequential/custom-index mode. It validates every mirrored source before suppressing any vanilla draw, binds the physical arena once, sets the packet-relative transform base, and issues `glMultiDrawElementsBaseVertex` for the run. Any capability, residency, or submission failure immediately returns that run to the existing physical/vanilla per-section path.

Performance Manager now owns a first-party shader runtime in addition to the guarded vanilla/Indigo multi-draw path. With no selected LazyBuilder shader pack, built-in Minecraft terrain source is augmented only for the per-draw transform contract and falls back to ModelOffset semantics when the contract is unavailable. With an active LazyBuilder-native pack, terrain vertex/fragment stages can be substituted during Minecraft shader compilation while retaining exact-source fallback on compile/link failure. External shader/renderer owners and compatibility-uncertain states remain hard gates so two authoritative pipelines never run simultaneously.

Diagnostics report packed command/transform bytes, capability reason, prepare attempts, eligible prepared runs, submitted multi-draw batches/commands, actual draw-call reductions, and submission failures.

## First-party shader pack format

LazyBuilder shader packs live in the normal `shaderpacks/` directory and may be either folders or ZIP files. The first-party runtime does not require Iris. A pack must provide the terrain pair and may add shadow/composite/final programs:

```text
shaderpacks/MyPack/
├── shader.properties            optional metadata/options
└── shaders/
    ├── terrain.vsh              required
    ├── terrain.fsh              required
    ├── shadow.vsh               optional pair
    ├── shadow.fsh
    ├── composite.vsh            optional pair
    ├── composite.fsh
    ├── final.vsh                optional pair
    ├── final.fsh
    └── lib/*.glsl               optional #include sources
```

`#include "relative/path.glsl"` and root-relative includes are expanded by the bounded first-party preprocessor. Include cycles, path traversal, and excessive include depth fail the candidate compile without replacing the last known-good pipeline.

Optional `shader.properties` metadata uses a deliberately small typed option model:

```properties
id=mivubi.studio
name=Studio Shader
author=MIVUBI
description=Example first-party shader

option.shadows.type=boolean
option.shadows.label=Shadows
option.shadows.default=true

option.exposure.type=float
option.exposure.label=Exposure
option.exposure.default=1.0
option.exposure.min=0.5
option.exposure.max=2.0
option.exposure.step=0.25

option.steps.type=int
option.steps.label=Sample Steps
option.steps.default=4
option.steps.min=1
option.steps.max=8
option.steps.step=1
```

Options are validated, quantized, persisted per pack, and injected after the GLSL `#version` line as deterministic defines such as `LB_OPT_SHADOWS`, `LB_OPT_EXPOSURE`, and `LB_OPT_STEPS`. Utility Manager stages edits locally and applies them in one recompilation instead of recompiling continuously while a slider is dragged.

Native pack identity is persistent and deterministic. Packs may declare a stable lowercase `id=` in `shader.properties`; this is preferred because it survives folder/ZIP rename. Without an explicit ID, LazyBuilder derives one from the source filename plus a deterministic fingerprint. Duplicate explicit IDs are rejected rather than aliased. Legacy pre-fingerprint and prior filename-derived IDs are migrated during catalog refresh, including per-pack option values.

Pack discovery distinguishes an empty folder from broken candidates. Folder/ZIP entries that look like shader packs but are missing required stages, have malformed manifests, or contain invalid option declarations remain visible to the manager as invalid entries with an actionable reason instead of silently disappearing.

A declared option is strict product input: malformed type/default/range/step data rejects the manifest instead of silently dropping the setting. A pack may expose at most 128 options, and option IDs that collapse to the same GLSL define are rejected.

The first-party runtime uses atomic candidate publication: every declared program must compile/link before the new pipeline replaces the previous one. Terrain stage substitution also retains the exact original Minecraft source for compile/link fallback.

When a compiled LazyBuilder pack changes its terrain source, Performance Manager now reloads only Minecraft's `ShaderLoader` using the public resource-reloader boundary. It invalidates LazyBuilder's shader-sensitive transform/multi-draw/fallback state explicitly, prepares a complete replacement Minecraft shader cache, and swaps it only after the ShaderLoader apply phase succeeds. It does **not** call the full `MinecraftClient.reloadResources()` path for a terrain-shader change, so textures, models, audio, and unrelated client resources are not reloaded merely because a shader option changed.

Non-terrain shader changes avoid even that targeted Minecraft shader reload when the prepared terrain-source fingerprint is unchanged.

Shader health is reported as an explicit runtime mode rather than a single healthy/failed bit: `full`, `terrain-post`, `terrain-shadow`, `terrain-only`, `fallback`, or `disabled`. Optional shadow/post-process failures therefore remain distinguishable from terrain integration failure and can degrade without discarding a still-valid terrain path.

## FRAPI and shader compatibility boundary

Fabric Renderer API 5.x lets renderer replacements declare ownership with:

```text
fabric-renderer-api-v1:contains_renderer
```

LazyBuilder uses the same ownership marker used by Fabric Indigo. Any custom FRAPI renderer owner disables first-party chunk/meshing mixins. Iris remains an optional compatibility owner when installed, not a dependency: its presence gates first-party shader-sensitive terrain submission to prevent double ownership. Compatibility uncertainty also disables first-party chunk ownership.

The target core renderer path is Minecraft/Fabric + LazyBuilder Performance Manager without Sodium or another renderer mod. When a third-party FRAPI renderer is installed, LazyBuilder still fails open to that declared owner for compatibility. Axiom and WorldEditCUI remain consumers/overlays rather than global renderer owners.

Entity and block-entity culling now cache vanilla renderer ownership by type identity and avoid duplicate queue-membership lookups. This keeps the conservative culling contract unchanged while reducing repeated registry/namespace work in the render path.

## Diagnostics

`PerformanceManagerClient.currentSnapshot()` remains on-demand. It exposes frame/memory state, chunk build/upload pressure, visibility/cache counters, upload pacing, terrain residency/payload/headroom, region churn, reclamation totals, projected arena pressure, live arena allocation/fragmentation state, offset-aware draw coverage, physical shared-buffer usage, custom-index draws, physical relocation health, transform-stream readiness, and guarded multi-draw submission health.

## Live runtime proof

Performance Manager has an opt-in structured proof logger for representative builder workloads. It is disabled during normal play and does not create a metrics history database.

Enable it for a benchmark run with:

```text
-Dlazybuilder.performance.proof=true
```

While a focused world is rendering, the logger emits one `LB_PERF_PROOF` sample every 120 rendered frames. Samples include FPS, p50/p95/p99/p99.9 frame time, fixed-threshold stutter counts, chunk-upload and terrain-submission CPU timing, upload queue pressure, governor mode/budgets, sampled culling CPU cost/yield, rebuild deferral/release totals, vanilla terrain GPU residency, physical-arena residency/draws, exclusive resident count, retired duplicate backing bytes, promotion/recovery counts, relocation health, multi-draw submissions/failures, GPU capability tier, reported VRAM where the driver exposes it, and asynchronous GPU timing for terrain, shadow, and post-process passes. GPU timing uses a query ring and never waits synchronously for a result. CPU/GPU timing evidence is scoped to the active world session; world changes reset accumulated counters and late GPU query results from the previous session are drained but ignored.

A useful two-run comparison keeps the same world, camera route, render distance, FPS target, resource pack, resolution, and other mods:

```text
baseline  -> rendering.optimizations=false
optimized -> rendering.optimizations=true
```

Correctness proof should show physical draws when the first-party path is active, exclusive promotion and retired bytes after the stability threshold, zero exclusive recovery failures, and no missing/corrupted terrain. Each structured sample also reports whether the strict standalone renderer is first-party-ready, its blocker/status string, the first-party shader stage, shader rendering readiness, terrain-shader integration, shadow-cache reuse, stale-GBuffer recovery, shader-only reload requests/failures, invalid shader-pack count, culling cache hit/stale totals, and culling queue-drop totals. FPS alone is not the acceptance criterion; average/worst frame time, ownership readiness, shader integration, and recovery health are equally important.

## Migration rule

External performance mods are compatibility peers, not required runtime owners. Custom FRAPI renderer owners still take precedence while installed because they explicitly claim the Fabric renderer boundary. Iris is treated the same way for shader-sensitive terrain ownership: when present it gates the first-party shader path for coexistence, but LazyBuilder's own shader runtime is the standalone path. ImmediatelyFast may keep overlapping hooks when installed to avoid duplicate interception, while LazyBuilder remains functional without it. Baked-quad vertex deduplication is first-party through MemoryDeduplicator and does not require FerriteCore.

## Standalone readiness versus feature parity

`first-party-ready` is an ownership/correctness statement for the optimization domains LazyBuilder actually implements. It does **not** claim feature-for-feature parity with every migration-source mod.

Current boundaries are explicit:

- ImmediatelyFast parity is partial. LazyBuilder owns its current text-render lookup reuse, dynamic GPU-buffer growth, terrain upload/render paths, and other first-party domains, but does not yet claim equivalent generic batching for every entity, block-entity, particle, HUD, GUI, or map rendering workload.
- FerriteCore parity is partial. LazyBuilder owns baked-quad vertex-array canonicalization plus its own terrain/memory systems, but does not currently claim FerriteCore's broader blockstate/property, multipart-model/predicate, model-resource-string, or shape-cache memory optimizations.
- The first-party shadow path supports opaque terrain by default and opt-in cutout terrain when the shadow program declares the complete LazyBuilder cutout contract. The pack must define `LAZYBUILDER_CUTOUT_SHADOWS 1`, pass `UV0` through `LazyBuilderShadowTexCoord`, and expose `LazyBuilderBlockAtlas` plus `LazyBuilderShadowAlphaCutoff`; otherwise the runtime remains safely `ready-solid-only`. Entity and block-entity shadow coverage remains separate work.
- LazyBuilder-native shader packs are a first-party format. Existing Iris/OptiFine shader packs are not assumed compatible and must not be advertised as such until a verified compatibility/import layer exists.
- Terrain-stage changes use Minecraft's own ShaderLoader resource-reloader directly. LazyBuilder invalidates only shader-sensitive terrain state and reloads the Minecraft shader cache without invoking full client resource reload; fingerprinting and generation coalescing avoid unnecessary or stale shader reloads.

### Pre-local-test acceptance gate

Before the standalone renderer/shader path is accepted for release, representative local workloads must prove all of the following on the exact candidate revision:

```text
standalone_renderer_ready=true
renderer owner = fabric-indigo / first-party-safe path
no Sodium/Iris/ImmediatelyFast/FerriteCore requirement
first-party terrain source links or falls back without corruption
zero exclusive terrain recovery failures
no missing/corrupt terrain across reload, teleport, world switch, resize and shader toggle
shader auxiliary memory remains within policy
GBuffer stale-frame recoveries remain exceptional rather than continuous
shadow reuse occurs when visibility/content/light keys are unchanged
shader compile generations discard stale preparation safely
```

Performance comparison must use identical world, camera route, resolution, render distance, resource pack, shader selection, and FPS target. p95/p99 frame time, stutter frequency, GPU stage time, recovery health, and correctness are primary evidence; FPS alone is insufficient. Optional fast paths must also demonstrate useful work: culling budget is profitability-aware and multi-draw enters a finite retryable cooldown when repeated preparation yields negligible draw-call savings.

## Configuration

Performance Manager does not own a separate settings screen. User-facing performance
controls are integrated into the permanent LazyBuilder `Settings -> Video` shell through Fabric ObjectShare using a narrow JDK-only snapshot/update contract. Performance
Manager remains the runtime and persistence owner; the Settings shell only presents and
edits the stable preference snapshot without importing Performance implementation classes.

```properties
background.enabled=true
background.unfocused_fps=30
background.minimized_fps=10
culling.entities=false
culling.block_entities=false
rendering.optimizations=true
memory.optimizations=true
```

Compatibility markers, visibility masks, upload pacing, terrain residency/reclamation thresholds, arena sizing/suballocation internals, physical mirror ownership, draw-plan batching, relocation, transform-stream layout, multi-draw shader handshake, buffer growth, and allocator details are not user-facing knobs.
