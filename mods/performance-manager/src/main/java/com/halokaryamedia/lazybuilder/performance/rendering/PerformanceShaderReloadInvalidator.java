package com.halokaryamedia.lazybuilder.performance.rendering;

import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

/**
 * Invalidates shader-sensitive runtime state whenever client resources reload.
 *
 * Terrain GPU residency itself is intentionally preserved; only shader/multi-draw state that is
 * tied to compiled program identity is reset.
 */
public final class PerformanceShaderReloadInvalidator implements SimpleSynchronousResourceReloadListener {
    private static final Identifier ID =
            Identifier.of("lazybuilder_performance_manager", "shader_runtime_invalidation");

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    @Override
    public void reload(ResourceManager manager) {
        TerrainShaderSourceTransformer.reset();
        TerrainMultiDrawSubmissionBackend.clear();
        TerrainPerDrawShaderBackend.clear();
        TerrainDrawTransformStream.clear();
    }
}
