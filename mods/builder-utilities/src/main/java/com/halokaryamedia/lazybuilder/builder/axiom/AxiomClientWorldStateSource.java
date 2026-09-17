package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.material.BlockStateSource;
import com.halokaryamedia.lazybuilder.builder.mutation.WorldBlockStateSource;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Objects;

/** Canonical world read adapter shared by material planning and mutation reconciliation. */
public final class AxiomClientWorldStateSource implements WorldBlockStateSource, BlockStateSource {
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
