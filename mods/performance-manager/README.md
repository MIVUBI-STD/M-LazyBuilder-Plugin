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
- chunk rebuild, mesh, render-region, upload, visibility, translucent sorting, color-provider, and buffer efficiency as renderer ownership grows;
- compatibility policy for builder-critical render consumers.

Performance Manager does not own shader loading or shader-pack UX, building/editing behavior, map/world management, screenshot/chat/window convenience, automatic graphics-quality reduction, or speculative background schedulers.

## Current renderer ownership

The first-party renderer path now includes conservative culling, chunk rebuild coalescing, block-color lookup caching, block-side visibility caching, section directional visibility caching, terrain-layer lookup caching, toroidal built-chunk storage remapping, chunk upload batching, writable GPU-buffer growth/reuse, buffer-pool pressure diagnostics, and pre-scheduler translucent-sort coalescing.

Translucent sort coalescing mirrors vanilla cancellation semantics: sections without a translucent layer do not enqueue a sort task, and an unchanged normalized camera-relative position is skipped only when vanilla would also cancel it. Camera-axis cases still sort. Sodium remains the active owner for this path while installed.

Full mesh replacement, GPU render-region arenas, terrain multi-draw submission, Fabric Renderer API ownership, and Iris/shader compatibility are not yet claimed equivalent.

## Diagnostics

`PerformanceManagerClient.currentSnapshot()` remains on-demand and now includes chunk backlog, upload backlog, free chunk buffers, coalesced rebuild requests, buffer acquire misses, avoided upload binds, remapped storage sections, section visibility cache hits, and avoided translucent sort tasks.

## Migration rule

External performance mods remain migration references until the matching first-party behavior is implemented and proven in representative builder workloads. In particular, Sodium-owned chunk/render mixins are rejected while Sodium is installed, ImmediatelyFast-owned overlapping render/upload hooks are rejected while ImmediatelyFast is installed, and FerriteCore-owned baked-quad deduplication is rejected while FerriteCore is installed.

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

Implementation details such as visibility masks, upload grouping, sort coalescing, provider caches, buffer growth, storage-ring mapping, and allocator behavior are not user-facing knobs.
