package com.halokaryamedia.lazybuilder.builder.region;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Sparse exact block region with chunk-local planning bounds. */
public final class PointSetRegion implements ChunkPlannableRegion {
    private final Set<Point> points;
    private final BlockBounds bounds;
    private final List<ChunkWorkUnit> workUnits;

    public PointSetRegion(Collection<Point> input) {
        Objects.requireNonNull(input, "input");
        LinkedHashSet<Point> deduplicated = new LinkedHashSet<>();
        for (Point point : input) deduplicated.add(Objects.requireNonNull(point, "point"));
        if (deduplicated.isEmpty()) throw new IllegalArgumentException("point region must not be empty");
        this.points = Set.copyOf(deduplicated);

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        Map<Long, ChunkAccumulator> chunks = new HashMap<>();
        for (Point point : points) {
            minX = Math.min(minX, point.x()); minY = Math.min(minY, point.y()); minZ = Math.min(minZ, point.z());
            maxX = Math.max(maxX, point.x()); maxY = Math.max(maxY, point.y()); maxZ = Math.max(maxZ, point.z());
            int chunkX = Math.floorDiv(point.x(), BlockBounds.CHUNK_SIZE);
            int chunkZ = Math.floorDiv(point.z(), BlockBounds.CHUNK_SIZE);
            long key = (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
            chunks.computeIfAbsent(key, ignored -> new ChunkAccumulator(chunkX, chunkZ)).include(point);
        }
        this.bounds = new BlockBounds(minX, minY, minZ, maxX, maxY, maxZ);
        ArrayList<ChunkWorkUnit> planned = new ArrayList<>(chunks.size());
        chunks.values().stream()
                .sorted(Comparator.comparingInt(ChunkAccumulator::chunkZ).thenComparingInt(ChunkAccumulator::chunkX))
                .forEach(chunk -> planned.add(chunk.toWorkUnit()));
        this.workUnits = List.copyOf(planned);
    }

    @Override public BlockBounds bounds() { return bounds; }
    @Override public boolean contains(int x, int y, int z) { return points.contains(new Point(x, y, z)); }
    @Override public List<ChunkWorkUnit> workUnits() { return workUnits; }
    public int size() { return points.size(); }

    public record Point(int x, int y, int z) { }

    private static final class ChunkAccumulator {
        private final int chunkX;
        private final int chunkZ;
        private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        private ChunkAccumulator(int chunkX, int chunkZ) { this.chunkX = chunkX; this.chunkZ = chunkZ; }
        private int chunkX() { return chunkX; }
        private int chunkZ() { return chunkZ; }
        private void include(Point point) {
            minX = Math.min(minX, point.x()); minY = Math.min(minY, point.y()); minZ = Math.min(minZ, point.z());
            maxX = Math.max(maxX, point.x()); maxY = Math.max(maxY, point.y()); maxZ = Math.max(maxZ, point.z());
        }
        private ChunkWorkUnit toWorkUnit() {
            return new ChunkWorkUnit(chunkX, chunkZ, new BlockBounds(minX, minY, minZ, maxX, maxY, maxZ));
        }
    }
}
