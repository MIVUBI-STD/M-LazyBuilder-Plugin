package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.material.SurfaceHeightFieldSource;
import com.halokaryamedia.lazybuilder.builder.placement.SurfaceHeightSource;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.Heightmap;

import java.util.Objects;

/** Client-world heightmap adapter shared by placement and procedural surface fields. */
public final class AxiomWorldSurfaceHeightSource
        implements SurfaceHeightSource, SurfaceHeightFieldSource {
    private final ClientWorld world;
    private final Heightmap.Type type;
    private final int offset;

    public AxiomWorldSurfaceHeightSource(ClientWorld world, int offset) {
        this(world, Heightmap.Type.WORLD_SURFACE, offset);
    }

    public AxiomWorldSurfaceHeightSource(ClientWorld world, Heightmap.Type type, int offset) {
        this.world = Objects.requireNonNull(world, "world");
        this.type = Objects.requireNonNull(type, "type");
        this.offset = offset;
    }

    @Override
    public int yAt(int x, int z) {
        return Math.addExact(world.getTopY(type, x, z), offset);
    }

    @Override
    public double heightAt(int x, int z) {
        return yAt(x, z);
    }
}
