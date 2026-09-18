package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.structure.StructureBlockEntityStateSource;
import net.minecraft.client.world.ClientWorld;

import java.io.IOException;
import java.util.Objects;

public final class AxiomBlockEntityStateSource implements StructureBlockEntityStateSource {
    private final ClientWorld world;

    public AxiomBlockEntityStateSource(ClientWorld world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    @Override
    public byte[] read(int worldX, int y, int worldZ) throws IOException {
        return AxiomBlockEntityPayloads.capture(world, worldX, y, worldZ);
    }
}
