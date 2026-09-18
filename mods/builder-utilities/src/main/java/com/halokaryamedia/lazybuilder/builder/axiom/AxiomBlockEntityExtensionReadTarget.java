package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.mutation.HistoryExtensionMutationTarget;
import net.minecraft.client.world.ClientWorld;

import java.io.IOException;
import java.util.Objects;

/** Read-only block-entity target used by reconciliation/recovery. */
public final class AxiomBlockEntityExtensionReadTarget implements HistoryExtensionMutationTarget {
    private final ClientWorld world;

    public AxiomBlockEntityExtensionReadTarget(ClientWorld world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    @Override
    public byte[] read(String typeId, int chunkX, int chunkZ, long localKey) throws IOException {
        int x = Math.addExact(
                Math.multiplyExact(chunkX, 16),
                com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition.localX(localKey));
        int z = Math.addExact(
                Math.multiplyExact(chunkZ, 16),
                com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition.localZ(localKey));
        int y = com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition.y(localKey);
        return AxiomBlockEntityPayloads.capture(world, x, y, z);
    }
}
