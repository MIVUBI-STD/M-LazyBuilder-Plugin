package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.material.BlockStateSource;
import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Captures a bounded block-state cuboid into a local-coordinate structure snapshot. */
public final class StructureCapture {
    public static final int MAX_CAPTURE_BLOCKS = 250_000;

    private StructureCapture() {}

    public static StructureSnapshot capture(
            BlockBounds bounds,
            BlockStateSource source,
            boolean includeAir
    ) {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(source, "source");
        if (bounds.blockCount() > MAX_CAPTURE_BLOCKS) {
            throw new IllegalArgumentException(
                    "structure capture exceeds " + MAX_CAPTURE_BLOCKS + " blocks");
        }

        List<StructureBlock> blocks = new ArrayList<>((int) Math.min(bounds.blockCount(), 4096));
        for (long y = bounds.minY(); y <= (long) bounds.maxY(); y++) {
            for (long z = bounds.minZ(); z <= (long) bounds.maxZ(); z++) {
                for (long x = bounds.minX(); x <= (long) bounds.maxX(); x++) {
                    String state = source.stateAt((int) x, (int) y, (int) z);
                    if (state == null || state.isBlank()) {
                        throw new IllegalArgumentException("captured block state must be non-blank");
                    }
                    if (!includeAir && isAir(state)) continue;
                    blocks.add(new StructureBlock(
                            Math.toIntExact(x - bounds.minX()),
                            Math.toIntExact(y - bounds.minY()),
                            Math.toIntExact(z - bounds.minZ()),
                            state
                    ));
                }
            }
        }
        if (blocks.isEmpty()) {
            throw new IllegalArgumentException("structure capture contains no blocks");
        }
        return new StructureSnapshot(blocks);
    }

    private static boolean isAir(String state) {
        int properties = state.indexOf('[');
        String id = properties < 0 ? state : state.substring(0, properties);
        return id.equals("minecraft:air")
                || id.equals("minecraft:cave_air")
                || id.equals("minecraft:void_air");
    }
}
