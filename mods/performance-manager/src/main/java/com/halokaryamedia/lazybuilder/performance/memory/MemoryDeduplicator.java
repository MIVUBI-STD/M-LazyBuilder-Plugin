package com.halokaryamedia.lazybuilder.performance.memory;

import com.halokaryamedia.lazybuilder.performance.PerformanceManagerClient;
import com.halokaryamedia.lazybuilder.performance.mixin.BakedQuadAccessor;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Shares identical vanilla baked-quad vertex arrays and drops the cache on resource reload. */
public final class MemoryDeduplicator implements SimpleSynchronousResourceReloadListener {
    private static final Identifier ID = Identifier.of("lazybuilder_performance_manager", "memory_dedup");
    /**
     * Cache unique model data only up to a predictable ceiling. Once full,
     * already-known duplicates still share storage; novel arrays remain valid
     * and unmodified rather than becoming permanent session-wide entries.
     */
    static final int MAX_UNIQUE_QUADS = 131_072;
    private static final int MAX_VERTEX_INTS_PER_QUAD = 256;
    private static final int MAX_CACHED_VERTEX_INTS = 8_388_608; // 32 MiB of raw vertex-array data
    private static final Object CACHE_LOCK = new Object();
    private static final ConcurrentMap<IntArrayKey, int[]> BAKED_QUADS = new ConcurrentHashMap<>();
    private static int cachedEntries;
    private static int cachedVertexInts;

    private MemoryDeduplicator() {
    }

    public static void register() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(new MemoryDeduplicator());
    }

    public static void deduplicate(BakedQuad quad) {
        if (quad == null || !PerformanceManagerClient.preferences().memoryOptimizations()) return;

        int[] vertices = quad.getVertexData();
        if (vertices == null || vertices.length == 0) return;

        int[] canonical = canonicalize(vertices);
        if (canonical != vertices) {
            ((BakedQuadAccessor) quad).lazybuilder$setVertexData(canonical);
        }
    }

    static int[] canonicalize(int[] vertices) {
        if (vertices == null || vertices.length == 0
                || vertices.length > MAX_VERTEX_INTS_PER_QUAD) return vertices;
        IntArrayKey probe = new IntArrayKey(vertices);
        int[] existing = BAKED_QUADS.get(probe);
        if (existing != null) return existing;

        // Synchronize cold insertion with resource reload/cache disable.
        // Hot-path cache hits are still lock-free, and no eviction of data
        // referenced by baked models is necessary for correctness.
        synchronized (CACHE_LOCK) {
            existing = BAKED_QUADS.get(probe);
            if (existing != null) return existing;
            if (cachedEntries >= MAX_UNIQUE_QUADS
                    || vertices.length > MAX_CACHED_VERTEX_INTS - cachedVertexInts) return vertices;
            BAKED_QUADS.put(probe, vertices);
            cachedEntries++;
            cachedVertexInts += vertices.length;
            return vertices;
        }
    }

    /** Dropping dedup lookup ownership never modifies already-baked quads. */
    public static void clearCache() {
        synchronized (CACHE_LOCK) {
            BAKED_QUADS.clear();
            cachedEntries = 0;
            cachedVertexInts = 0;
        }
    }

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    @Override
    public void reload(ResourceManager manager) {
        clearCache();
    }

    static int cachedQuadCount() {
        return BAKED_QUADS.size();
    }

    private static final class IntArrayKey {
        private final int[] values;
        private final int hash;

        private IntArrayKey(int[] values) {
            this.values = values;
            this.hash = Arrays.hashCode(values);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || (other instanceof IntArrayKey key && Arrays.equals(this.values, key.values));
        }
    }
}
