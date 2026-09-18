package com.halokaryamedia.lazybuilder.performance.rendering;

/** Pure policy deciding when stale terrain GPU capacity is worth reclaiming. */
public final class TerrainGpuReclamationPolicy {
    private static final long MIN_RECLAIM_CAPACITY_BYTES = 2L * 1024L * 1024L;
    private static final int REGION_XZ_SIZE = 8;
    private static final int REGION_Y_SIZE = 4;

    private TerrainGpuReclamationPolicy() {
    }

    public static boolean shouldReclaim(
            long capacityBytes,
            int oldSectionX,
            int oldSectionY,
            int oldSectionZ,
            int newSectionX,
            int newSectionY,
            int newSectionZ
    ) {
        if (capacityBytes < MIN_RECLAIM_CAPACITY_BYTES) return false;
        return regionCoordinate(oldSectionX, REGION_XZ_SIZE) != regionCoordinate(newSectionX, REGION_XZ_SIZE)
                || regionCoordinate(oldSectionY, REGION_Y_SIZE) != regionCoordinate(newSectionY, REGION_Y_SIZE)
                || regionCoordinate(oldSectionZ, REGION_XZ_SIZE) != regionCoordinate(newSectionZ, REGION_XZ_SIZE);
    }

    public static long minimumReclaimCapacityBytes() {
        return MIN_RECLAIM_CAPACITY_BYTES;
    }

    private static int regionCoordinate(int sectionCoordinate, int regionSize) {
        return Math.floorDiv(sectionCoordinate, regionSize);
    }
}
