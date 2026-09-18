package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;

import java.util.Objects;

/** Exact axis-aligned world bounds of a quarter-turn/mirrored structure placement. */
public record StructurePlacementBounds(
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
) {
    public StructurePlacementBounds {
        if (maxX < minX || maxY < minY || maxZ < minZ) {
            throw new IllegalArgumentException("placement bounds must be ordered");
        }
    }

    public static StructurePlacementBounds of(
            StructureSnapshot snapshot,
            StructurePlacement placement
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(placement, "placement");
        BlockBounds local = snapshot.localBounds();

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        int[] xs = {local.minX(), local.maxX()};
        int[] ys = {local.minY(), local.maxY()};
        int[] zs = {local.minZ(), local.maxZ()};

        for (int x : xs) {
            for (int y : ys) {
                for (int z : zs) {
                    StructurePlacement.WorldPosition world =
                            placement.transform(x, y, z);
                    minX = Math.min(minX, world.x());
                    minY = Math.min(minY, world.y());
                    minZ = Math.min(minZ, world.z());
                    maxX = Math.max(maxX, world.x());
                    maxY = Math.max(maxY, world.y());
                    maxZ = Math.max(maxZ, world.z());
                }
            }
        }

        return new StructurePlacementBounds(
                minX, minY, minZ, maxX, maxY, maxZ);
    }

    public boolean overlaps(StructurePlacementBounds other) {
        Objects.requireNonNull(other, "other");
        return minX <= other.maxX && maxX >= other.minX
                && minY <= other.maxY && maxY >= other.minY
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }
}
