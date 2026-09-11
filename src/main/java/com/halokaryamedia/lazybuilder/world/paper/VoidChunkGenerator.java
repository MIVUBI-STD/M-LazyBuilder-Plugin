package com.halokaryamedia.lazybuilder.world.paper;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

import java.util.Random;

/** Minimal all-air generator used only by LazyBuilder Void World creation. */
public final class VoidChunkGenerator extends ChunkGenerator {
    public static final VoidChunkGenerator INSTANCE = new VoidChunkGenerator();
    public static final int PLATFORM_Y = 64;
    public static final int SPAWN_Y = PLATFORM_Y + 1;

    private VoidChunkGenerator() {
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5D, SPAWN_Y, 0.5D);
    }
}
