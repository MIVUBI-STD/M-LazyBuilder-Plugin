package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.mutation.WorldBlockStateSource;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Objects;

/** Canonical read adapter matching the History v2 block-state serialization used for Axiom dispatch. */
public final class AxiomClientWorldStateSource implements WorldBlockStateSource {
    private final ClientWorld world;
    private final AxiomBlockStateCodec codec;

    public AxiomClientWorldStateSource(ClientWorld world) {
        this.world = Objects.requireNonNull(world, "world");
        this.codec = new AxiomBlockStateCodec(world);
    }

    @Override
    public String stateAt(int worldX, int y, int worldZ) {
        return codec.encode(world.getBlockState(new BlockPos(worldX, y, worldZ)));
    }
}
