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
    private static final ConcurrentMap<IntArrayKey, int[]> BAKED_QUADS = new ConcurrentHashMap<>();

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
        if (vertices == null || vertices.length == 0) return vertices;
        IntArrayKey probe = new IntArrayKey(vertices);
        int[] existing = BAKED_QUADS.putIfAbsent(probe, vertices);
        return existing == null ? vertices : existing;
    }

    static void clearCache() {
        BAKED_QUADS.clear();
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
