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
    public String readBlockState(int worldX, int y, int worldZ) {
        return encode(worldX, y, worldZ);
    }

    @Override
    public String stateAt(int x, int y, int z) {
        return encode(x, y, z);
    }

    private String encode(int x, int y, int z) {
        return codec.encode(world.getBlockState(new BlockPos(x, y, z)));
    }
}
