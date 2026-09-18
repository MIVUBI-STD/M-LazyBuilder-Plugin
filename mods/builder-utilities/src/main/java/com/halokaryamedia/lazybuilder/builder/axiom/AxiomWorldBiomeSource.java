package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.placement.PlacementBiomeSource;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Objects;

/** Read-only biome identity adapter for placement exclusion constraints. */
public final class AxiomWorldBiomeSource implements PlacementBiomeSource {
    private final ClientWorld world;

    public AxiomWorldBiomeSource(ClientWorld world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    @Override
    public String biomeAt(int x, int y, int z) {
        return world.getBiome(new BlockPos(x, y, z))
                .getKey()
                .map(key -> key.getValue().toString())
                .orElseThrow(() -> new IllegalStateException(
                        "Biome at " + x + "," + y + "," + z + " has no registry key"));
    }
}
