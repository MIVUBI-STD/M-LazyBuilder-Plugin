package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable structure snapshot for blocks plus optional block-entity payloads. */
public final class StructureSnapshot {
    private final List<StructureBlock> blocks;
    private final List<StructureBlockEntity> blockEntities;
    private final BlockBounds localBounds;

    public StructureSnapshot(List<StructureBlock> blocks) {
        this(blocks, List.of());
    }

    public StructureSnapshot(
            List<StructureBlock> blocks,
            List<StructureBlockEntity> blockEntities
    ) {
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(blockEntities, "blockEntities");
        if (blocks.isEmpty()) throw new IllegalArgumentException("structure must contain at least one block");

        Set<Position> blockPositions = new HashSet<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (StructureBlock block : blocks) {
            Objects.requireNonNull(block, "block");
            Position position = new Position(block.x(), block.y(), block.z());
            if (!blockPositions.add(position)) {
                throw new IllegalArgumentException("duplicate structure-local block position");
            }
            minX = Math.min(minX, block.x());
            minY = Math.min(minY, block.y());
            minZ = Math.min(minZ, block.z());
            maxX = Math.max(maxX, block.x());
            maxY = Math.max(maxY, block.y());
            maxZ = Math.max(maxZ, block.z());
        }

        Set<Position> blockEntityPositions = new HashSet<>();
        for (StructureBlockEntity blockEntity : blockEntities) {
            Objects.requireNonNull(blockEntity, "blockEntity");
            Position position = new Position(blockEntity.x(), blockEntity.y(), blockEntity.z());
            if (!blockPositions.contains(position)) {
                throw new IllegalArgumentException(
                        "block entity must reference an existing structure block");
            }
            if (!blockEntityPositions.add(position)) {
                throw new IllegalArgumentException("duplicate structure-local block entity position");
            }
        }

        this.blocks = List.copyOf(blocks);
        this.blockEntities = List.copyOf(blockEntities);
        this.localBounds = new BlockBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public List<StructureBlock> blocks() { return blocks; }
    public List<StructureBlockEntity> blockEntities() { return blockEntities; }
    public BlockBounds localBounds() { return localBounds; }
    public int blockCount() { return blocks.size(); }
    public int blockEntityCount() { return blockEntities.size(); }

    private record Position(int x, int y, int z) {}
}
