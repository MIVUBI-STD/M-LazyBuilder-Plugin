package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Objects;

/** Prevents user-facing structure capture from silently dropping block-entity payloads. */
public final class AxiomStructureCaptureGuard {
    private AxiomStructureCaptureGuard() {}

    public static void requireBlockStateOnly(ClientWorld world, BlockBounds bounds) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(bounds, "bounds");

        for (long y = bounds.minY(); y <= (long) bounds.maxY(); y++) {
            for (long z = bounds.minZ(); z <= (long) bounds.maxZ(); z++) {
                for (long x = bounds.minX(); x <= (long) bounds.maxX(); x++) {
                    BlockPos pos = new BlockPos((int) x, (int) y, (int) z);
                    if (world.getBlockEntity(pos) != null) {
                        throw new IllegalArgumentException(
                                "source contains a block entity at "
                                        + x + "," + y + "," + z
                                        + "; block-entity stamping is not available through Axiom's public API"
                        );
                    }
                }
            }
        }
    }
}
