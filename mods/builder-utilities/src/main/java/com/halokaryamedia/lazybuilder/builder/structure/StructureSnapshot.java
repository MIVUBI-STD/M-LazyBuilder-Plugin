package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable block-only structure snapshot with duplicate-local-position validation. */
public final class StructureSnapshot {
    private final List<StructureBlock> blocks;
    private final BlockBounds localBounds;

    public StructureSnapshot(List<StructureBlock> blocks) {
        Objects.requireNonNull(blocks, "blocks");
        if (blocks.isEmpty()) throw new IllegalArgumentException("structure must contain at least one block");

        Set<Position> seen = new HashSet<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (StructureBlock block : blocks) {
            Objects.requireNonNull(block, "block");
            if (!seen.add(new Position(block.x(), block.y(), block.z()))) {
                throw new IllegalArgumentException("duplicate structure-local block position");
            }
            minX = Math.min(minX, block.x());
            minY = Math.min(minY, block.y());
            minZ = Math.min(minZ, block.z());
            maxX = Math.max(maxX, block.x());
            maxY = Math.max(maxY, block.y());
            maxZ = Math.max(maxZ, block.z());
        }

        this.blocks = List.copyOf(blocks);
        this.localBounds = new BlockBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public List<StructureBlock> blocks() {
        return blocks;
    }

    public BlockBounds localBounds() {
        return localBounds;
    }

    public int blockCount() {
        return blocks.size();
    }

    private record Position(int x, int y, int z) {}
}
