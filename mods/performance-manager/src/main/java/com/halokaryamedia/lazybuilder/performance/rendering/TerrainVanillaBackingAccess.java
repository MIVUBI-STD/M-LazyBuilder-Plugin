package com.halokaryamedia.lazybuilder.performance.rendering;

import net.minecraft.client.gl.GlUsage;
import net.minecraft.client.gl.GpuBuffer;
import net.minecraft.client.render.VertexFormat;

/** Narrow bridge for reversible ownership of Minecraft's per-section terrain GPU backing. */
public interface TerrainVanillaBackingAccess {
    GlUsage lazybuilder$usage();
    boolean lazybuilder$isVanillaBackingRetired();
    long lazybuilder$retireVanillaBacking();

    void lazybuilder$installRecoveredVanillaBacking(
            GpuBuffer recoveredVertexBuffer,
            GpuBuffer recoveredIndexBuffer,
            VertexFormat format,
            VertexFormat.DrawMode mode,
            VertexFormat.IndexType indexType,
            int indexCount
    );
}
