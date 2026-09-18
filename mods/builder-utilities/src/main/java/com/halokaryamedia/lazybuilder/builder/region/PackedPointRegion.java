package com.halokaryamedia.lazybuilder.builder.region;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Sparse exact region backed by sorted primitive packed positions.
 * Intended for high-volume generated geometry where Point objects are too costly.
 */
public final class PackedPointRegion implements ChunkPlannableRegion {
    private final long[] positions;
    private final BlockBounds bounds;
    private final List<ChunkWorkUnit> workUnits;

    public PackedPointRegion(long[] input) {
        Objects.requireNonNull(input, "input");
        if (input.length == 0) throw new IllegalArgumentException("packed point region must not be empty");

        long[] sorted = input.clone();
        Arrays.sort(sorted);
        int unique = 1;
        for (int i = 1; i < sorted.length; i++) {
            if (sorted[i] != sorted[unique - 1]) sorted[unique++] = sorted[i];
        }
        positions = Arrays.copyOf(sorted, unique);

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        Map<Long, ChunkAccumulator> chunks = new HashMap<>();

        for (long packed : positions) {
            int x = PackedWorldBlockPosition.x(packed);
            int y = PackedWorldBlockPosition.y(packed);
            int z = PackedWorldBlockPosition.z(packed);
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);

            int chunkX = Math.floorDiv(x, BlockBounds.CHUNK_SIZE);
            int chunkZ = Math.floorDiv(z, BlockBounds.CHUNK_SIZE);
            long key = (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
            chunks.computeIfAbsent(key, ignored -> new ChunkAccumulator(chunkX, chunkZ))
                    .include(x, y, z);
        }

        bounds = new BlockBounds(minX, minY, minZ, maxX, maxY, maxZ);
        ArrayList<ChunkWorkUnit> planned = new ArrayList<>(chunks.size());
        chunks.values().stream()
                .sorted(Comparator.comparingInt(ChunkAccumulator::chunkZ)
                        .thenComparingInt(ChunkAccumulator::chunkX))
                .forEach(chunk -> planned.add(chunk.toWorkUnit()));
        workUnits = List.copyOf(planned);
    }

    @Override public BlockBounds bounds() { return bounds; }

    @Override
    public boolean contains(int x, int y, int z) {
        return Arrays.binarySearch(positions, PackedWorldBlockPosition.pack(x, y, z)) >= 0;
    }

    @Override public List<ChunkWorkUnit> workUnits() { return workUnits; }

    public int size() { return positions.length; }

    public long[] positions() { return positions.clone(); }

    private static final class ChunkAccumulator {
        private final int chunkX;
        private final int chunkZ;
        private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        private ChunkAccumulator(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        private int chunkX() { return chunkX; }
        private int chunkZ() { return chunkZ; }

        private void include(int x, int y, int z) {
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
        }

        private ChunkWorkUnit toWorkUnit() {
            return new ChunkWorkUnit(
                    chunkX, chunkZ,
                    new BlockBounds(minX, minY, minZ, maxX, maxY, maxZ));
        }
    }
}
